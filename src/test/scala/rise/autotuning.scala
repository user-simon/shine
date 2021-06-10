package rise


import arithexpr.arithmetic.{ArithExpr, PosInf, RangeAdd, RangeMul, RangeUnknown}
import java.util.concurrent.{Executors, TimeUnit}
import rise.core._
import rise.core.types.{NatIdentifier, _}
import rise.core.primitives.{let => _, _}
import rise.core.DSL._
import rise.core.DSL.Type._
import rise.core.DSL.HighLevelConstructs.{slideVectors, tileShiftInwards}
import rise.openCL.DSL._
import rise.openCL.primitives.oclReduceSeq
import rise.autotune.{Tuner, generateJSON, tuningParam, wrapOclRun}
import apps.separableConvolution2D.weightsSeqVecUnroll
import shine.OpenCL.{GlobalSize, LocalSize}
import util.{gen, writeToPath}

class autotuning extends test_util.Tests {
  val convolution: ToBeTyped[Expr] =
  // tileShiftInwards should constrain n >= tile
  // slideVectors and slide should constrain tile % vec = 0
    tuningParam("vec", RangeAdd(0, 32, 1), (vec: Nat) =>
      tuningParam("tile", RangeAdd(4, 32, 1), (tile: Nat) =>
        depFun(RangeAdd(1, PosInf, vec), (n: Nat) =>
          fun(3 `.` f32)(weights =>
            fun(((n + 2) `.` f32) ->: (n `.` f32))(input =>
              input |> tileShiftInwards(tile)(mapWorkGroup(0)(
                slideVectors(vec) >> slide(3)(vec) >>
                  mapLocal(0)(weightsSeqVecUnroll(weights)) >>
                  asScalar
              ))
            )))))

  val convolutionOcl: ToBeTyped[Expr] =
  // tileShiftInwards should constrain n >= tile
  // slideVectors and slide should constrain tile % vec = 0
    tuningParam("vec", RangeAdd(0, 32, 1), (vec: Nat) =>
      tuningParam("tile", RangeAdd(4, 32, 1), (tile: Nat) =>
        depFun(RangeAdd(1, PosInf, vec), (n: Nat) =>
          fun(3 `.` f32)(weights =>
            fun(((n + 2) `.` f32) ->: (n `.` f32))(input =>
              oclRun(LocalSize(1), GlobalSize(32))(
                input |> tileShiftInwards(tile)(mapWorkGroup(0)(
                  slideVectors(vec) >> slide(3)(vec) >>
                    mapLocal(0)(weightsSeqVecUnroll(weights)) >>
                    asScalar
                ))
              ))))))

  val convolutionOclGsLsWrap: Expr =
    tuningParam("ls0", (ls0: Nat) => tuningParam("ls1", (ls1: Nat) =>
      tuningParam("gs0", (gs0: Nat) => tuningParam("gs1", (gs1: Nat) =>
        wrapOclRun(LocalSize(ls0, ls1), GlobalSize(gs0, gs1))(convolution)
      ))))

  val convolutionOclGsLs: ToBeTyped[Expr] = {
    // tileShiftInwards should constrain n >= tile
    // slideVectors and slide should constrain tile % vec = 0
    tuningParam("ls0", RangeMul(1, 1024, 2), (ls0: Nat) =>
      tuningParam("ls1", RangeMul(1, 1024, 2), (ls1: Nat) =>
        tuningParam("gs0", RangeMul(1, 1024, 2), (gs0: Nat) =>
          tuningParam("gs1", RangeMul(1, 1024, 2), (gs1: Nat) =>
            tuningParam("vec", RangeAdd(0, 32, 1), (vec: Nat) =>
              tuningParam("tile", RangeAdd(4, 1024, 1), (tile: Nat) =>
                depFun(RangeAdd(1, PosInf, vec), (n: Nat) =>
                  fun(3 `.` f32)(weights =>
                    fun(((n + 2) `.` f32) ->: (n `.` f32))(input =>
                      oclRun(LocalSize(ls0, ls1), GlobalSize(gs0, gs1))(
                        input |> tileShiftInwards(tile)(mapWorkGroup(0)(
                          slideVectors(vec) >> slide(3)(vec) >>
                            mapLocal(0)(weightsSeqVecUnroll(weights)) >>
                            asScalar
                        ))
                      ))))))))))
  }

  val scalSJ: ToBeTyped[Expr] =
    depFun((n: Nat) => fun(n `.` f32)(input => fun(f32)(alpha =>
      oclRun(LocalSize(1), GlobalSize(32))(
        input |> split(4) |> mapGlobal(0)(fun(x => alpha * x)) |> join)
    )))

  val scalOcl: ToBeTyped[Expr] =
    depFun((n: Nat) => fun(n `.` f32)(input => fun(f32)(alpha =>
      oclRun(LocalSize(1), GlobalSize(32))(
        input |> mapGlobal(0)(fun(x => alpha * x)))
    )))

  val scal: ToBeTyped[Expr] =
    depFun((n: Nat) => fun(n `.` f32)(input => fun(f32)(alpha =>
      input |> mapGlobal(0)(fun(x => alpha * x)))
    ))

  val main: Int => String = iterations => {
    s"""
    const int N = ${iterations};
    int main(int argc, char** argv) {
      Context ctx = createDefaultContext();
      Buffer input = createBuffer(ctx, N * sizeof(float), HOST_READ | HOST_WRITE | DEVICE_READ);
      Buffer output = createBuffer(ctx, N * sizeof(float), HOST_READ | HOST_WRITE | DEVICE_WRITE);

      float* in = hostBufferSync(ctx, input, N * sizeof(float), HOST_WRITE);
      for (int i = 0; i < N; i++) {
        in[i] = 1;
      }

      foo_init_run(ctx, output, input, input);

      float* out = hostBufferSync(ctx, output, N * sizeof(float), HOST_READ);

//    todo add error checking

      destroyBuffer(ctx, input);
      destroyBuffer(ctx, output);
      destroyContext(ctx);
      return EXIT_SUCCESS;
    }
    """
  }

  test("collect parameters") {
    val params = autotune.collectParameters(convolutionOclGsLsWrap)
    assert(params.find(_.name == "vec").get.range == RangeAdd(0, 32, 1))
    assert(params.find(_.name == "tile").get.range == RangeAdd(4, 32, 1))
    assert(params.find(_.name == "ls0").get.range == RangeUnknown)
    assert(params.find(_.name == "ls1").get.range == RangeUnknown)
    assert(params.find(_.name == "gs0").get.range == RangeUnknown)
    assert(params.find(_.name == "gs1").get.range == RangeUnknown)
    assert(params.size == 6)
  }

  test("collect constraints") {
    val e: Expr = convolutionOclGsLsWrap
    autotune.collectConstraints(e, autotune.collectParameters(e)).foreach(println)
  }

  test("substitute parameters") {
    val e: Expr = convolution(32)
    val constraints = autotune.collectConstraints(e, autotune.collectParameters(e))
    println("constraints: \n" + constraints)

    val badParameters1 = Map(
      NatIdentifier("vec", isExplicit = true) -> (5: Nat),
      NatIdentifier("tile", isExplicit = true) -> (15: Nat)
    )
    assert(!autotune.checkConstraints(constraints, badParameters1))

    val badParameters2 = Map(
      NatIdentifier("vec", isExplicit = true) -> (4: Nat),
      NatIdentifier("tile", isExplicit = true) -> (13: Nat)
    )
    assert(!autotune.checkConstraints(constraints, badParameters2))

    /* FIXME: there is no `n >= tile` constraint collected
    val badParameters3 = Map(
      NatIdentifier("vec", isExplicit = true) -> (8: Nat),
      NatIdentifier("tile", isExplicit = true) -> (64: Nat)
    )
    assert(!autotune.checkConstraints(constraints, badParameters3))
    */

    val goodParameters = Map(
      NatIdentifier("vec", isExplicit = true) -> (4: Nat),
      NatIdentifier("tile", isExplicit = true) -> (16: Nat)
    )
    assert(autotune.checkConstraints(constraints, goodParameters))
    rise.core.substitute.natsInExpr(goodParameters, e)
  }

  val mmKernel: ToBeTyped[Expr] =
    tuningParam("v3", (v3: Nat) =>
      tuningParam("v4", (v4: Nat) =>
        tuningParam("v5", (v5: Nat) =>
          tuningParam("v6", (v6: Nat) =>
            tuningParam("v7", (v7: Nat) =>
              tuningParam("v8", (v8: Nat) =>
                depFun((n: Nat, m: Nat, o: Nat) => fun(
                  (o `.` n `.` f32) ->: (o `.` m `.` f32) ->: (n `.` m `.` f32)
                )((at, b) =>
                  at |>
                    map(split(v5)) |> split(v8) |> // O'.v8.N'.v5.f
                    map(transpose) |> transpose |> // N'.O'.v8.v5.f
                    mapWorkGroup(1)(fun(p2 =>
                      b |>
                        map(split(v7)) |> split(v8) |> // O'.v8.M'.v7.f
                        map(transpose) |> transpose |> // M'.O'.v8.v7.f
                        mapWorkGroup(0)(fun(p3 =>
                          zip(p2)(p3) |> // O'.(v8.v5.f x v8.v7.f)
                            oclReduceSeq(AddressSpace.Local)(fun((p13, p14) =>
                              // (v5/^v4).(v7/^v3).v4.v3.f x (v8.v5.f x v8.v7.f)
                              let(toLocal(makePair(
                                p14._1 |> join |> split(v6) |> // ((v8 x v5) /^ v6).v6.f
                                  mapLocal(1)(asScalar o mapLocal(0)(id) o asVectorAligned(4)) |>
                                  join |> split(v5)
                              )( // v8.v5.f
                                p14._2 |> // v8.v7.f
                                  mapLocal(1)(asScalar o mapLocal(0)(id) o asVectorAligned(4))
                              )))
                                be (p15 =>
                                zip(p13)(split(v4)(transpose(p15._1))) |> // (v5/^v4).((v7/^v3).v4.v3.f x v4.v8.f)
                                  mapLocal(1)(fun(p16 =>
                                    zip(p16._1)(split(v3)(transpose(p15._2))) |> // (v7/^v3).(v4.v3.f x v3.v8.f)
                                      mapLocal(0)(fun(p17 =>
                                        zip(transpose(p16._2))(transpose(p17._2)) |> // v8.(v4.f x v3.f)
                                          oclReduceSeq(AddressSpace.Private)(fun((p19, p20) =>
                                            // v4.v3.f x (v4.f x v3.f)
                                            let(toPrivate(makePair(mapSeq(id)(p20._1))(mapSeq(id)(p20._2))))
                                              be (p21 =>
                                              zip(p19)(p21._1) |> // v4.(v3.f x f)
                                                mapSeq(fun(p22 =>
                                                  zip(p22._1)(p21._2) |> // v3.(f x f)
                                                    mapSeq(fun(p23 =>
                                                      p23._1 + (p22._2 * p23._2)
                                                    ))
                                                ))
                                              )
                                          ))(p17._1 // v4.v3.f
                                            |> mapSeq(mapSeq(id)) // TODO: think about that
                                          ) |> mapSeq(mapSeq(id)) // TODO: think about that
                                      ))
                                  ))
                                )
                            ))(
                              generate(fun(_ =>
                                generate(fun(_ =>
                                  generate(fun(_ =>
                                    generate(fun(_ => lf32(0.0f))))))))) |>
                                mapLocal(1)(mapLocal(0)(mapSeq(mapSeq(id))))
                            ) |> // (v5/^v4).(v7/^v3).v4.v3.f
                            mapLocal(1)(mapLocal(0)(mapSeq(asScalar o mapSeq(id) o asVector(4)))) |>
                            map(transpose) |> join |> map(join) |> transpose // v7.v5.f
                        )) |> join |> transpose // v5.M.f
                    )) |> join // N.M.f
                ))))))))

  test("mm kernel constraints") {
    val e: Expr =
      tuningParam("ls0", (ls0: Nat) => tuningParam("ls1", (ls1: Nat) =>
        tuningParam("gs0", (gs0: Nat) => tuningParam("gs1", (gs1: Nat) =>
          wrapOclRun(LocalSize(ls0, ls1), GlobalSize(gs0, gs1))(mmKernel)
        ))))
    val (nIdent, mIdent, oIdent) = e match {
      case DepLambda(n: NatIdentifier, DepLambda(m: NatIdentifier, DepLambda(o: NatIdentifier, _))) =>
        (n, m, o)
      case _ => ???
    }
    val params = autotune.collectParameters(e)
    val constraints = autotune.collectConstraints(e, params)
    // note: v5 multiple of 4, contiguous memory constraint is missing

    // n, m, o, v3, v4, v5, v6, v7, v8, ls0, ls1, gs0, gs1
    val badParameters = Seq[(Nat, Nat, Nat, Nat, Nat, Nat, Nat, Nat, Nat, Nat, Nat, Nat, Nat)](
      (64, 128, 128, 8, 1, 1, 1, 16, 1, 32, 4, 32, 16),
      (64, 128, 128, 1, 1, 1, 32, 1, 128, 64, 2, 64, 128),
      (64, 128, 128, 2, 1, 1, 2, 32, 32, 1, 1, 4, 64),
      (64, 128, 128, 1, 1, 1, 1, 2, 1, 1, 1, 2, 1),
      (64, 128, 128, 1, 1, 1, 1, 8, 4, 4, 1, 64, 1),
      (64, 128, 128, 1, 1, 1, 1, 1, 128, 2, 2, 2, 8),
      (64, 128, 128, 1, 1, 1, 2, 1, 2, 2, 2, 4, 128),
      (64, 128, 128, 2, 1, 1, 1, 2, 4, 4, 4, 16, 64),
      (64, 128, 128, 2, 1, 1, 2, 8, 2, 4, 2, 4, 2),
      (64, 128, 128, 8, 1, 1, 1, 8, 4, 8, 64, 8, 64),
      (64, 128, 128, 16, 2, 2, 1, 16, 2, 64, 4, 64, 8),
      (64, 128, 128, 1, 1, 2, 2, 128, 4, 16, 16, 64, 64),
      (64, 128, 128, 1, 1, 2, 4, 1, 16, 4, 1, 4, 32),
      (64, 128, 128, 1, 1, 2, 1, 4, 128, 4, 2, 64, 4),
      (64, 128, 128, 2, 1, 2, 4, 2, 64, 2, 1, 128, 1),
      (64, 128, 128, 16, 2, 2, 2, 32, 8, 2, 1, 128, 1),
      (64, 128, 128, 2, 2, 2, 2, 16, 8, 4, 1, 4, 32),
      (64, 128, 128, 1, 2, 2, 1, 1, 1, 4, 1, 4, 4),
      (64, 128, 128, 8, 2, 2, 1, 8, 32, 8, 2, 32, 2),
      (64, 128, 128, 32, 2, 2, 2, 64, 16, 1, 2, 16, 32),
      (64, 128, 128, 2, 4, 4, 16, 8, 64, 8, 1, 8, 8),
      (64, 128, 128, 1, 1, 4, 32, 8, 64, 2, 4, 128, 4),
      (64, 128, 128, 1, 2, 4, 4, 1, 1, 16, 1, 16, 1),
      (64, 128, 128, 1, 2, 4, 128, 8, 128, 2, 1, 2, 32),
      (64, 128, 128, 1, 1, 4, 16, 16, 32, 1, 128, 32, 128),
      (64, 128, 128, 1, 1, 4, 32, 8, 8, 16, 8, 64, 8),
      (64, 128, 128, 2, 1, 4, 64, 8, 32, 2, 8, 4, 64),
      (64, 128, 128, 4, 1, 4, 2, 32, 2, 4, 8, 8, 64),
      (64, 128, 128, 2, 1, 8, 2, 4, 128, 2, 2, 2, 16),
      (64, 128, 128, 1, 4, 8, 8, 2, 16, 4, 2, 8, 4),
      (64, 128, 128, 2, 8, 8, 8, 128, 2, 2, 1, 4, 2),
      (64, 128, 128, 1, 4, 8, 16, 4, 4, 1, 2, 128, 2),
      (64, 128, 128, 1, 1, 8, 1, 4, 1, 1, 16, 1, 32),
      (64, 128, 128, 2, 8, 8, 8, 8, 1, 8, 4, 8, 16),
      (64, 128, 128, 2, 1, 16, 16, 8, 32, 1, 2, 1, 128),
      (64, 128, 128, 2, 2, 16, 4, 2, 4, 2, 1, 128, 2),
      (64, 128, 128, 2, 1, 16, 16, 8, 8, 16, 2, 16, 2),
      (64, 128, 128, 1, 4, 16, 32, 8, 128, 1, 1, 2, 8),
      (64, 128, 128, 1, 4, 16, 128, 32, 32, 1, 4, 1, 16),
      (64, 128, 128, 1, 1, 16, 4, 1, 1, 1, 4, 32, 32),
      (64, 128, 128, 2, 1, 32, 1, 4, 8, 8, 2, 128, 2),
      (64, 128, 128, 1, 4, 32, 1, 4, 64, 1, 8, 1, 8),
      (64, 128, 128, 1, 1, 32, 2, 1, 8, 8, 1, 64, 1),
      (64, 128, 128, 1, 8, 32, 32, 1, 64, 1, 1, 1, 64),
      (64, 128, 128, 1, 32, 32, 16, 64, 32, 1, 1, 8, 16),
      (64, 128, 128, 2, 32, 32, 4, 4, 2, 16, 8, 32, 64),
      (64, 128, 128, 32, 8, 32, 2, 32, 1, 2, 8, 2, 8),
      (64, 128, 128, 2, 8, 64, 4, 4, 64, 1, 8, 1, 32),
      (64, 128, 128, 2, 1, 64, 16, 2, 4, 1, 1, 4, 64),
      (64, 128, 128, 1, 4, 64, 8, 1, 128, 1, 2, 16, 4),
      (64, 128, 128, 2, 16, 64, 2, 2, 8, 2, 2, 2, 2),
      (64, 128, 128, 1, 8, 64, 1, 1, 1, 1, 1, 1, 2),
      (64, 128, 128, 1, 1, 64, 1, 2, 8, 32, 64, 64, 128),
      (64, 128, 128, 1, 64, 64, 64, 1, 4, 1, 2, 1, 16),
      (64, 128, 128, 2, 64, 64, 4, 2, 1, 2, 8, 2, 32),
      (64, 128, 128, 64, 32, 64, 1, 64, 64, 32, 1, 128, 64),
      (64, 128, 128, 32, 32, 128, 1, 64, 4, 64, 1, 64, 1),
      (64, 128, 128, 4, 2, 128, 32, 4, 8, 1, 1, 8, 4),
      (64, 128, 128, 4, 128, 128, 1, 4, 32, 2, 16, 2, 64),
      (64, 128, 128, 1, 8, 128, 128, 1, 32, 2, 2, 4, 16),
      (64, 128, 128, 1, 32, 128, 2, 1, 128, 4, 1, 64, 8),
      (64, 128, 128, 1, 8, 128, 8, 4, 128, 4, 8, 8, 16),
      (64, 128, 128, 8, 128, 128, 128, 16, 16, 8, 64, 16, 64),
      (64, 128, 128, 16, 64, 128, 1, 128, 128, 1, 2, 1, 4),
      (64, 128, 128, 16, 128, 128, 8, 32, 8, 1, 2, 1, 2),
      (64, 128, 128, 2, 1, 1, 8, 4, 16, 2, 2, 2, 2),
    )
    val goodParameters = Seq[(Nat, Nat, Nat, Nat, Nat, Nat, Nat, Nat, Nat, Nat, Nat, Nat, Nat)](
      (64, 128, 128, 4, 1, 1, 4, 4, 8, 8, 4, 8, 32),
      (64, 128, 128, 4, 1, 1, 8, 64, 16, 1, 16, 4, 32),
      (64, 128, 128, 4, 1, 1, 8, 128, 64, 1, 8, 8, 8),
      (64, 128, 128, 4, 1, 2, 4, 64, 128, 4, 2, 4, 4),
      (64, 128, 128, 64, 2, 2, 32, 64, 32, 1, 1, 2, 32),
      (64, 128, 128, 4, 1, 2, 8, 8, 64, 32, 2, 32, 16),
      (64, 128, 128, 4, 1, 2, 32, 4, 32, 4, 4, 4, 4),
    )
    for ((params, isGood) <- Seq((badParameters, false), (goodParameters, true))) {
      params.foreach { case cfg@(n, m, o, v3, v4, v5, v6, v7, v8, ls0, ls1, gs0, gs1) =>
        val map = Map(
          nIdent -> n,
          mIdent -> m,
          oIdent -> o,
          NatIdentifier("v3", isExplicit = true) -> v3,
          NatIdentifier("v4", isExplicit = true) -> v4,
          NatIdentifier("v5", isExplicit = true) -> v5,
          NatIdentifier("v6", isExplicit = true) -> v6,
          NatIdentifier("v7", isExplicit = true) -> v7,
          NatIdentifier("v8", isExplicit = true) -> v8,
          NatIdentifier("ls0", isExplicit = true) -> ls0,
          NatIdentifier("ls1", isExplicit = true) -> ls1,
          NatIdentifier("gs0", isExplicit = true) -> gs0,
          NatIdentifier("gs1", isExplicit = true) -> gs1,
        )
        if (autotune.checkConstraints(constraints, map) != isGood) {
          val (sat, notSat) = constraints.partition(c =>
            c.substitute(map.asInstanceOf[Map[ArithExpr, ArithExpr]]).isSatisfied())
          println("satisfied:")
          sat.foreach(println)
          println("not satisfied:")
          notSat.foreach(println)
          throw new Exception(s"$cfg should${if (isGood) "" else " not"} pass constraint checking")
        }
      }
    }
  }

  test("generateJSON") {
    val parameters = autotune.collectParameters(convolution)
    val constraints = autotune.collectConstraints(convolution, parameters)
    val json = autotune.generateJSON(parameters, constraints, Tuner(main(32)))

    val gold =
      """{
        | "application_name" : "RISE",
        | "optimization_objectives" : ["runtime"],
        | "hypermapper_mode" : {
        |   "mode" : "client-server"
        | },
        | "feasible_output" : {
        |   "enable_feasible_predictor" : true,
        |   "name" : "Valid",
        |   "true_value" : "True",
        |   "false_value" : "False"
        | },
        | "design_of_experiment" : {
        |   "doe_type" : "random sampling",
        |   "number_of_samples" : 20
        | },
        | "optimization_iterations" : 100,
        | "input_parameters" : {
        |   "tile" : {
        |       "parameter_type" : "integer",
        |       "values" : [4, 32]
        |   },
        |   "vec" : {
        |       "parameter_type" : "integer",
        |       "values" : [1, 32]
        |   }
        | }
        |}
        |""".stripMargin

    assert(json.equals(gold))
  }

  test("wrapOclRun") {
    val wrapped = wrapOclRun(LocalSize(1), GlobalSize(32))(convolution)
    assert(convolutionOcl.toExpr =~= wrapped)

    val e = (wrapped: ToBeTyped[Expr]) (32)
    assert(convolutionOcl(32).toExpr =~= e.toExpr)
  }


  test("search") {
    // test full tuning run
    val e: Expr = convolutionOcl(32)

    val tuningResult = autotune.search(Tuner(main(32)))(e)

    println("tuningResult: \n")
    tuningResult.samples.foreach(elem => println(elem))

    val bestSample = autotune.getBest(tuningResult.samples)
    println("bestSample: \n" + bestSample)

    autotune.saveSamples("autotuning/RISE.csv", tuningResult)
  }

  // test Hypermapper constraints support
  // needs access to hypermapper_dev repository
  ignore("search experimental") {
    val e: Expr = convolutionOclGsLs(1024)

    //    val tuner = Tuner(main(1024), 100, "RISE", "autotuning", Some("/home/jo/development/rise-lang/shine/autotuning/configs/convolution.json"), Some("/home/jo/development/tuning/hypermapper_dev/hypermapper/optimizer.py"))
    val tuner = Tuner(main(1024), 10, "RISE", "autotuning", None, Some("/home/jo/development/tuning/hypermapper_dev/hypermapper/optimizer.py"))

    val tuningResult = autotune.search(tuner)(e)

    println("tuningResult: \n")
    tuningResult.samples.foreach(elem => println(elem))

    val bestSample = autotune.getBest(tuningResult.samples)
    println("bestSample: \n" + bestSample)

    autotune.saveSamples("autotuning/RISE.csv", tuningResult)
  }

  // only important for automatic json generation for hm constraints support
  ignore("distribute constraints") {
    val e: Expr = convolutionOclGsLs(1024)
    val tuner = Tuner(main(1024), 10)
    val params = autotune.collectParameters(e)
    val constraints = autotune.collectConstraints(e, params)

    val distribution = autotune.distributeConstraints(params, constraints)

    println("output: \n")
    distribution.foreach(elem => {
      println("elem: " + elem._1)
      println("dependencies: " + elem._2._2)
      println("constraints: " + elem._2._1)
      println()
    })

    println("\nnow generate json")
    val json = generateJSON(params, constraints, tuner)
    println("json: \n" + json)
  }

  test("execute convolution") {
    val goodParameters = Map(
      NatIdentifier("vec", isExplicit = true) -> (4: Nat),
      NatIdentifier("tile", isExplicit = true) -> (16: Nat)
    )

    val e: Expr = convolutionOcl(32)
    val e2 = rise.core.substitute.natsInExpr(goodParameters, e)

    val result = autotune.execute(e2, main(32))

    // check if result has valid runtime
    assert(result._1.isDefined)
    // check if no error was reported
    assert(result._2.errorLevel.equals(autotune.NO_ERROR))

    println("result: " + result)
  }

  test("execute scal") {
    val e: Expr = scalOcl(32)

    val main =
      """
    const int N = 32;
    int main(int argc, char** argv) {
      Context ctx = createDefaultContext();
      Buffer input = createBuffer(ctx, N * sizeof(float), HOST_READ | HOST_WRITE | DEVICE_READ);
      Buffer output = createBuffer(ctx, N * sizeof(float), HOST_READ | HOST_WRITE | DEVICE_WRITE);

      float* in = hostBufferSync(ctx, input, N * sizeof(float), HOST_WRITE);
      for (int i = 0; i < N; i++) {
        in[i] = 1;
      }

      foo_init_run(ctx, output, input, 4);

      float* out = hostBufferSync(ctx, output, N * sizeof(float), HOST_READ);

      for (int i = 0; i < N; i++) {
        if (out[i] != 4) {
          fprintf(stderr, "wrong output: %f\n", out[i]);
          exit(EXIT_FAILURE);
        }
      }

      destroyBuffer(ctx, input);
      destroyBuffer(ctx, output);
      destroyContext(ctx);
      return EXIT_SUCCESS;
    }
    """

    val result = autotune.execute(e, main)

    // check if result has valid runtime
    assert(result._1.isDefined)
    // check if no error was reported
    assert(result._2.errorLevel == autotune.NO_ERROR)

    println("result: \n" + result)
  }

  test("test xml parsing") {
    val xmlString =
      """
<trace date="2021-03-30 18:04:26" profiler_version="0.1.0" ocl_version="1.2">
  <device name="GeForce RTX 2070" id="1"/>
  <queue properties="CL_QUEUE_PROFILING_ENABLE" device_id="1" id="1"/>
  <program build_options="-cl-fast-relaxed-math -Werror -cl-std=CL1.2" id="1"/>
  <kernel name="k0" program_id="1" id="1"/>
  <kernel_instance kernel_id="1" id="1" unique_id="1" command_queue_id="1">
    <event forced="true" queued="1617120266695090400" submit="1617120266695092832" start="1617120266695097344" end="1617120266695107456"/>
    <offset_range/>
    <global_range dim="3" x="1" y="1" z="1"/>
    <local_range dim="3" x="1" y="1" z="1"/>
  </kernel_instance>
  <mem_object type="Buffer" flag="CL_MEM_WRITE_ONLY|CL_MEM_ALLOC_HOST_PTR" size="128" id="2"/>
  <mem_object type="Buffer" flag="CL_MEM_READ_ONLY|CL_MEM_ALLOC_HOST_PTR" size="128" id="1"/>
</trace>
    """
    assert(util.ExecuteOpenCL.getRuntimeFromClap(xmlString).value.toFloat == 0.010112f)
  }

  test("text xml parsing with corrupted xml string") {
    val corruptedXmlString =
      """<trace date="2021-04-01 18:42:51" profiler_version="0.1.0" ocl_version="1.2"/>
<trace date="2021-04-01 18:42:51" profiler_version="0.1.0" ocl_version="1.2">
  <device name="pthread-Intel(R) Core(TM) i5-8265U CPU @ 1.60GHz" id="1"/>
  <queue properties="CL_QUEUE_PROFILING_ENABLE" device_id="1" id="1"/>
  <program build_options="-cl-fast-relaxed-math -Werror -cl-std=CL1.2" id="1"/>
  <kernel name="k0" program_id="1" id="1"/>
  <kernel_instance kernel_id="1" id="1" unique_id="1" command_queue_id="1">
    <event forced="true" queued="42573479270519" submit="42573479270669" start="42573583785589" end="42573583944408"/>
    <offset_range/>
    <global_range dim="3" x="32" y="1" z="1"/>
    <local_range dim="3" x="1" y="1" z="1"/>
  </kernel_instance>
  <mem_object type="Buffer" flag="CL_MEM_WRITE_ONLY|CL_MEM_ALLOC_HOST_PTR" size="128" id="2"/>
  <mem_object type="Buffer" flag="CL_MEM_READ_ONLY|CL_MEM_ALLOC_HOST_PTR" size="128" id="1"/>
</trace>

    """
    assert(util.ExecuteOpenCL.getRuntimeFromClap(corruptedXmlString).value.toFloat == 0.158819f)
  }

  test("generate huge amount of code") {
    // expression
    val e: Expr = convolutionOclGsLs(1024)

    // define parameters to break the code-gen
    val parameters = Map(
      NatIdentifier("vec", isExplicit = true) -> (16: Nat),
      NatIdentifier("tile", isExplicit = true) -> (32: Nat),
      NatIdentifier("gs0", isExplicit = true) -> (1: Nat),
      NatIdentifier("gs1", isExplicit = true) -> (512: Nat),
      NatIdentifier("ls0", isExplicit = true) -> (1: Nat),
      NatIdentifier("ls1", isExplicit = true) -> (64: Nat)
    )

    // substitute parameters
    val eWithParams = rise.core.substitute.natsInExpr(parameters, e)

    println("run codegen with timeout ")
    // WARNING: timeout does not stop the thread, it only returns to the host thread
    val result = rise.autotune.execute(eWithParams, main(1024))

    print("result: " + result)
    assert(result._2.errorLevel.equals(autotune.CODE_GENERATION_ERROR))
  }
}
