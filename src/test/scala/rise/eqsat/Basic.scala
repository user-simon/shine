package rise.eqsat

import rise.{core => rc}
import rise.core.{types => rct}

class Basic extends test_util.Tests {
  import Basic._

  test("normalize") {
    import ExprDSL._

    val m = map((f32 ->: f32) ->: (`%n`(0)`.`f32) ->: (`%n`(0)`.`f32))
    assert(
      BENF(lam(`%n`(0)`.`f32,
        app(app(lam(f32 ->: f32, app(m, %(0, f32 ->: f32))), %(2, f32 ->: f32)),
          %(0, `%n`(0)`.`f32)))) ==
      app(m, %(1, f32 ->: f32)))
  }

  import rise.core.DSL._
  import rise.core.DSL.Type._
  import rise.core.primitives._

  test("introduceDataFuns") {
    val e = introduceDataFuns(4, _ => f => {
      f(0) >> f(1) >> f(2) >> f(3)
    })
    println(e.toExpr)
  }

  test("map fusion") {
    proveEquivBENF(
      withArrayAndFuns(4, in => f =>
        in |> map(f(0)) |> map(f(1)) |> map(f(2)) |> map(f(3))),
      withArrayAndFuns(4, in => f =>
        in |> map(f(0) >> f(1)) |> map(f(2) >> f(3))),
      Seq(rules.eta, rules.beta, rules.mapFusion)
    )
  }

  test("map fusion (CNF)") {
    proveEquivCNF(
      withArrayAndFuns(4, in => f =>
        in |> map(f(0)) |> map(f(1)) |> map(f(2)) |> map(f(3))),
      withArrayAndFuns(4, in => f =>
        in |> map(f(0) >> f(1)) |> map(f(2) >> f(3))),
      Seq(rules.combinatory.compositionAssoc1,
        rules.combinatory.compositionAssoc2,
        rules.combinatory.mapFusion)
    )
  }

  test("map fission") {
    proveEquivBENF(
      withArrayAndFuns(4, in => f =>
        in |> map(f(0) >> f(1) >> f(2) >> f(3))),
      withArrayAndFuns(4, in => f =>
        in |> map(f(0)) |> map(f(1)) |> map(f(2)) |> map(f(3))),
      Seq(rules.eta, rules.betaExtract, rules.mapFission)
    )
  }

  test("map fission (CNF)") {
    proveEquivCNF(
      withArrayAndFuns(4, in => f =>
        in |> map(f(0) >> f(1) >> f(2) >> f(3))),
      withArrayAndFuns(4, in => f =>
        in |> map(f(0)) |> map(f(1)) |> map(f(2)) |> map(f(3))),
      Seq(rules.combinatory.compositionAssoc1,
        rules.combinatory.compositionAssoc2,
        rules.combinatory.mapFission)
    )
  }

  test("map first fission") {
    proveEquivBENF(
      withArrayAndFuns(4, in => f =>
        in |> map(f(0) >> f(1) >> f(2) >> f(3))),
      withArrayAndFuns(4, in => f =>
        in |> map(f(0)) >> map(f(1) >> f(2) >> f(3))),
      Seq(rules.eta, rules.betaExtract, rules.mapFission, rules.mapFusion)
    )
  }

  test("map first fission (CNF)") {
    proveEquivCNF(
      withArrayAndFuns(4, in => f =>
        in |> map(f(0) >> f(1) >> f(2) >> f(3))),
      withArrayAndFuns(4, in => f =>
        in |> map(f(0)) >> map(f(1) >> f(2) >> f(3))),
      Seq(rules.combinatory.compositionAssoc1,
        rules.combinatory.compositionAssoc2,
        rules.combinatory.mapFission,
        rules.combinatory.mapFusion)
    )
  }

  test("slideBeforeMapMapF") {
    val `_` = rct.TypePlaceholder
    def wrap(inner: ToBeTyped[rc.Expr] => ToBeTyped[rc.Expr])
    : ToBeTyped[rc.Expr] =
      depFun((n: rct.Nat) =>
      depFun((dt1: rct.DataType) => depFun((dt2: rct.DataType) =>
      fun(f =>
        inner(f :: dt1 ->: dt2) :: ((n`.`dt1) ->: `_`)
      ))))

    proveEquivBENF(
      wrap(f =>
        slide(3)(1) >> slide(4)(2) >> map(map(map(f)))),
      wrap(f =>
        map(f) >> slide(3)(1) >> slide(4)(2)),
      Seq(rules.eta, rules.betaExtract, rules.mapFusion, rules.mapFission, rules.slideBeforeMapMapF)
    )
  }
}

object Basic {
  def proveEquivBENF(start: rise.core.Expr,
                     goal: rise.core.Expr,
                     rules: Seq[DefaultAnalysis.Rewrite]): Unit = {
    proveEquivBENF(start, Seq(goal), rules)
  }

  def proveEquivCNF(start: rise.core.Expr,
                    goal: rise.core.Expr,
                    rules: Seq[DefaultAnalysis.Rewrite]): Unit = {
    proveEquivCNF(start, Seq(goal), rules)
  }

  def proveEquiv(start: Expr,
                 goal: Expr,
                 rules: Seq[DefaultAnalysis.Rewrite],
                 analysis: DefaultAnalysisCustomisable = DefaultAnalysis): Unit = {
    proveEquiv(start, Seq(goal), rules, analysis)
  }

  def proveEquivBENF(start: rise.core.Expr,
                     goals: Seq[rise.core.Expr],
                     rules: Seq[DefaultAnalysis.Rewrite]): Unit = {
    val normStart = BENF(Expr.fromNamed(start))
    val normGoals = goals.map(g => BENF(Expr.fromNamed(g)))
    println(s"normalized start: ${Expr.toNamed(normStart)}")
    for ((goal, i) <- normGoals.zipWithIndex) {
      println(s"normalized goal n°$i: ${Expr.toNamed(goal)}")
    }
    proveEquiv(normStart, normGoals, rules, DefaultAnalysis)
  }

  def proveEquivCNF(start: rise.core.Expr,
                    goals: Seq[rise.core.Expr],
                    rules: Seq[DefaultAnalysis.Rewrite]): Unit = {
    val normStart = CNF(Expr.fromNamed(start))
    val normGoals = goals.map(g => CNF(Expr.fromNamed(g)))
    println(s"normalized start: ${Expr.toNamed(normStart)}")
    for ((goal, i) <- normGoals.zipWithIndex) {
      println(s"normalized goal n°$i: ${Expr.toNamed(goal)}")
    }
    proveEquiv(normStart, normGoals, rules, DefaultAnalysis)
  }

  def proveEquiv(start: Expr,
                 goals: Seq[Expr],
                 rules: Seq[DefaultAnalysis.Rewrite],
                 analysis: DefaultAnalysisCustomisable): Unit = {
    val goalPatterns = goals.map(Pattern.fromExpr(_).compile())

    val egraph = EGraph.emptyWithAnalysis(analysis)
    val startId = egraph.addExpr(start)
    // val goalId = runner.egraph.addExpr(goal)
    val runner = Runner.init().doneWhen { _ =>
        goalPatterns.forall(_.searchEClass(egraph, startId).isDefined)
        // note: could also use this to get a faster procedure,
        // but it would allow rewriting the goal as well, not just the start
        // r.egraph.findMut(startId) == r.egraph.findMut(goalId)
    }.run(egraph, rules)
    runner.printReport()

    if (!runner.stopReasons.contains(Done)) {
      runner.iterations.foreach(println)
      // egraph.dot().toSVG("/tmp/egraph.svg")
      val (found, notFound) = goalPatterns.zipWithIndex.partition { case (goal, _) =>
        goal.searchEClass(egraph, startId).isDefined
      }
      println(s"found: ${found.map(_._2).mkString(", ")}")
      println(s"not found: ${notFound.map(_._2).mkString(", ")}");
      {
        import PatternDSL._
        val pat: Pattern = lam(lam(app(app(map, lam(
          app(transpose >> app(map,
            app(zip, %(0)) >>
            app(app(prim(rc.primitives.reduceSeq.primitive),
              lam(lam(app(
                ?(0) >> app(add, %(1)),
                %(0))))),
              l(rc.semantics.FloatData(0)))
          ), %(1))
        )), %(1))))
        println(pat.compile().searchEClass(egraph, startId))
      }
      throw CouldNotProveEquiv
    }
  }

  case object CouldNotProveEquiv extends Exception

  import rise.core.DSL._
  import rise.core.DSL.Type._

  def introduceDataFuns(n: Int,
                        k: Seq[rct.DataType] => Seq[ToBeTyped[rc.Identifier]] => ToBeTyped[rc.Expr]
                       ): ToBeTyped[rc.Expr] = {
    def recDataTypes(n: Int,
                     k: Seq[rct.DataType] => ToBeTyped[rc.Expr]
                    ): ToBeTyped[rc.Expr] =
      if (n <= 0) {
        k(Seq())
      } else {
        recDataTypes(n - 1, rest => depFun((dt: rct.DataType) => k(dt +: rest)))
      }

    def recFuns(n: Int, dts: Seq[rct.DataType],
                k: Seq[ToBeTyped[rc.Identifier]] => ToBeTyped[rc.Expr]
               ): ToBeTyped[rc.Expr] =
      if (n <= 0) {
        k(Seq())
      } else {
        recFuns(n - 1, dts, rest => fun((dts(n) ->: dts(n - 1)): rct.Type)(f => k(f +: rest)))
      }

    recDataTypes(n + 1, dts => recFuns(n, dts, k(dts)))
  }

  def withArrayAndFuns(n: Int,
                       k: ToBeTyped[rc.Expr] => Seq[ToBeTyped[rc.Expr]] => ToBeTyped[rc.Expr]
                      ): ToBeTyped[rc.Expr] = {
    impl { elemT: rct.DataType =>
      depFun((size: rct.Nat) => introduceDataFuns(n, _ => f => fun(size`.`elemT)(in => k(in)(f)))) }
  }
}
