package rise.eqsat

import PatternDSL._
import rise.core.{primitives => rcp}

object rules {
  type Rule = Rewrite[DefaultAnalysisData]

  def containsIdent(v: PatternVar, ident: Var)
                   (egraph: EGraph[DefaultAnalysisData], eclass: EClassId, subst: Subst): Boolean =
    egraph.getMut(subst(v)).data.free.contains(ident.index)

  def neg[D](cond: (EGraph[D], EClassId, Subst) => Boolean)
            (egraph: EGraph[D], eclass: EClassId, subst: Subst): Boolean =
    !cond(egraph, eclass, subst)

  // TODO: find a way to combine different analysis requirements?

  // -- reduction --

  val beta: Rule = Rewrite.init("beta",
    app(lam(?(0)), ?(1)).compile()
      -->
    BetaApplier(?(0), ?(1))
  )
  val betaNat: Rule = Rewrite.init("beta-nat",
    nApp(nLam(?(0)), `?n`(0)).compile()
      -->
    BetaNatApplier(?(0), `?n`(0))
  )

  import rise.core.types.{Nat, DataType, Type}
  import NamedRewriteDSL._

  val etaAbstraction: Rule = NamedRewrite.init("eta-abstraction",
    ("f" :: ("a" ->: ("b": Type))) --> lam("x", app("f", "x"))
  )
  // FIXME: does not work as intended
  // Avoids excessive lam("y", app(lam("x", app("f", "x")), "y"))
  val gentleEtaAbstraction: Rule = Rewrite.init("gentle-eta-abstraction",
    etaAbstraction.searcher -> etaAbstraction.applier
  ) when { case (egraph, eclass, _) =>
    egraph.get(eclass).nodes.exists(n => !n.matches(Lambda(())))
  }

  val eta: Rule = NamedRewrite.init("eta",
    lam("x", app("f", "x")) --> "f",
    Seq("f" notFree "x"))
  val removeTransposePair: Rule = NamedRewrite.init("remove-transpose-pair",
    app(transpose, app(transpose, "x")) --> "x"
  )

  // -- algorithmic --

  val mapFusion: Rule = NamedRewrite.init("map-fusion",
    app(app(map, "f"), app(app(map, "g"), "in"))
      -->
    app(app(map, lam("x", app("f", app("g", "x")))), "in")
  )
  val mapFission: Rule = NamedRewrite.init("map-fission",
    app(map, lam("x", app("f", "gx" :: ("dt": DataType))))
      -->
    lam("in", app(app(map, "f"), app(app(map, lam("x", "gx")), "in"))),
    Seq("f" notFree "x")
  )

  // TODO: other means of picking n, such as tuning parameters
  def splitJoin(n: Int): Rule = NamedRewrite.init(s"split-join-$n",
    app(map, "f")
      -->
    lam("x", app(join, app(app(map, app(map, "f")), app(nApp(split, n), "x"))))
  )
  def blockedReduce(n: Int): Rule = NamedRewrite.init(s"blocked-reduce-$n",
    app(app(app(reduce, "op" :: ("a" ->: "a" ->: ("a": Type))), "init"), "arg")
      -->
    app(app(app(rcp.reduceSeq.primitive,
      lam("acc", lam("y", app(app("op", "acc"),
        app(app(app(reduce, "op"), "init"), "y"))))),
      "init"), app(nApp(split, n), "arg"))
  )

  val reduceSeqMapFusion: Rule = NamedRewrite.init("reduce-seq-map-fusion",
    app(app(app(rcp.reduceSeq.primitive, "f"), "init"), app(app(map, "g"), "in"))
      -->
    app(app(app(rcp.reduceSeq.primitive, lam("acc", lam("x",
      app(app("f", "acc"), app("g", "x"))))), "init"), "in")
  )

  // FIXME: how can we generalize to N?
  val mapOutsideMakeArray2: Rule = NamedRewrite.init("map-outside-make-array-2",
    app(app(rcp.makeArray(2).primitive, app(app(map, "f1"), "e")), app(app(map, "f2"), "e"))
      -->
    app(transpose, app(app(map, lam("x", app(app(rcp.makeArray(2).primitive, app("f1", "x")), app("f2", "x")))), "e"))
  )

  val idAfter = NamedRewrite.init("id-after",
    ("x": Pattern) --> app(lam("y", "y"), "x")
  )
  val transposePairAfter: Rule = NamedRewrite.init("transpose-pair-after",
    ("x" :: ((`_`: Nat)`.`((`_`: Nat)`.``_`))) --> app(transpose, app(transpose, "x"))
  )
  val transposePairAfter2: Rule = NamedRewrite.init("transpose-pair-after-2",
    ("x" :: ((`_`: Nat)`.`((`_`: Nat)`.``_`))) -->
      app(lam("y", app(transpose, app(transpose, "y"))), "x")
  )
  val transposePairAfter3: Rule = NamedRewrite.init("transpose-pair-after-3",
    ("f" :: ("in" ->: ((`_`: Nat)`.`((`_`: Nat)`.``_`)))) -->
      lam("x", app(lam("y", app(transpose, app(transpose, "y"))), app("f", "x")))
  )
  val transposePairAfter4: Rule = NamedRewrite.init("transpose-pair-after-4",
    ("f" :: ("in" ->: ((`_`: Nat)`.`((`_`: Nat)`.``_`)))) -->
    lam("x", app(transpose, app(lam("y", app(transpose, app("f", "y"))), "x")))
  )
  val createTransposePair: Rule = NamedRewrite.init("create-transpose-pair",
    app(lam("y", "y"), "x" :: ((`_`: Nat)`.`((`_`: Nat)`.``_`)))
      -->
    app(lam("y", app(transpose, app(transpose, "y"))), "x")
  )

  // - slide widening -

  val dropInSlide: Rule = NamedRewrite.init("drop-in-slide",
    app(nApp(drop, "l"), app(nApp(nApp(slide, "n"), 1), "in"))
      -->
    app(app(map, nApp(drop, "l")), app(nApp(nApp(slide, ("n": Nat) + ("l": Nat)), 1), "in"))
  )
  val takeInSlide: Rule = NamedRewrite.init("take-in-slide",
    app(nApp(take, "r") :: ((("s": Nat)`.``_`) ->: `_`), app(nApp(nApp(slide, "n"), 1), "in"))
      -->
    app(app(map, nApp(take, "n")), app(nApp(nApp(slide, ("n": Nat) + ("s": Nat) - ("r": Nat)), 1), "in"))
  )

  // -- movement --

  val mapSlideBeforeTranspose: Rule = NamedRewrite.init("map-slide-before-transpose",
    app(transpose, app(app(map, nApp(nApp(slide, "sz"), "sp")), "in"))
      -->
    app(app(map, transpose), app(nApp(nApp(slide, "sz"), "sp"), app(transpose, "in")))
  )
  val slideBeforeMapMapF: Rule = NamedRewrite.init("slide-before-map-map-f",
    app(app(map, app(map, "f")), app(nApp(nApp(slide, "sz"), "sp"), "in"))
      -->
    app(nApp(nApp(slide, "sz"), "sp"), app(app(map, "f"), "in"))
  )
  val slideBeforeMap: Rule = NamedRewrite.init("slide-before-map",
    app(nApp(nApp(slide, "sz"), "sp"), app(app(map, "f"), "in"))
      -->
    app(app(map, app(map, "f")), app(nApp(nApp(slide, "sz"), "sp"), "in"))
  )

  val splitBeforeMap: Rule = NamedRewrite.init("split-before-map",
    app(nApp(split, "n"), app(app(map, "f"), "in"))
      -->
      app(app(map, app(map, "f")), app(nApp(split, "n"), "in"))
  )

  val mapMapFBeforeTranspose: Rule = NamedRewrite.init("map-map-f-before-transpose",
    app(transpose, app(app(map, app(map, "f")), "y"))
      -->
    app(app(map, app(map, "f")), app(transpose, "y"))
  )
  val transposeBeforeMapMapF: Rule = NamedRewrite.init("transpose-before-map-map-f",
    app(app(map, app(map, "f")), app(transpose, "y"))
      -->
    app(transpose, app(app(map, app(map, "f")), "y"))
  )

  val dropBeforeMap: Rule = NamedRewrite.init("drop-before-map",
    app(nApp(drop, "n"), app(app(map, "f"), "in"))
      -->
    app(app(map, "f"), app(nApp(drop, "n"), "in"))
  )
  val takeBeforeMap: Rule = NamedRewrite.init("take-before-map",
    app(nApp(take, "n"), app(app(map, "f"), "in"))
      -->
    app(app(map, "f"), app(nApp(take, "n"), "in"))
  )

  val dropBeforeTake: Rule = NamedRewrite.init("drop-before-take",
    app(nApp(drop, "m"), app(nApp(take, "n+m"), "in"))
      -->
    app(nApp(take, ("n+m": Nat) - ("m": Nat)), app(nApp(drop, "m"), "in"))
  )

  // -- lowering --

  val reduceSeq: Rule = NamedRewrite.init("reduce-seq",
    reduce --> rcp.reduceSeq.primitive
  )
  val reduceSeqUnroll: Rule = NamedRewrite.init("reduce-seq-unroll",
    reduce --> rcp.reduceSeqUnroll.primitive
  )
  val mapSeq: Rule = NamedRewrite.init("map-seq",
    map --> rcp.mapSeq.primitive
  )
  val iterateStream: Rule = NamedRewrite.init("iterate-stream",
    map --> rcp.iterateStream.primitive
  )
  val toMemAfterMapSeq: Rule = NamedRewrite.init("to-mem-after-map-seq",
    app(app(rcp.mapSeq.primitive, "f"), "in")
      -->
    app(rcp.toMem.primitive, app(app(rcp.mapSeq.primitive, "f"), "in"))
  )

  // TODO: condition typeHasTrivialCopy(t) / or synthesise trivial write function
  val mapSeqUnrollWrite: Rule = NamedRewrite.init("map-seq-unroll-write",
    ("x" :: ((`_`: Nat)`.`"dt"))
      -->
    app(app(rcp.mapSeqUnroll.primitive, lam("y", "y")), "x")
  )
  val mapSeqUnrollMapSeqWrite: Rule = NamedRewrite.init("map-seq-unroll-map-seq-write",
    ("x" :: ((`_`: Nat)`.`((`_`: Nat)`.`"dt")))
      -->
      app(app(rcp.mapSeqUnroll.primitive, app(rcp.mapSeq.primitive, lam("y", "y"))), "x")
  )

  val circularBuffer: Rule = NamedRewrite.init("circular-buffer",
    nApp(nApp(slide, "sz"), 1) --> app(nApp(nApp(rcp.circularBuffer.primitive, "sz"), "sz"), lam("x", "x"))
  )
  val circularBufferLoadFusion: Rule = NamedRewrite.init("circular-buffer-load-fusion",
    app(app(nApp(nApp(rcp.circularBuffer.primitive, "n1"), "n2"), "load"), app(app(map, "f"), "in"))
      -->
      app(app(nApp(nApp(rcp.circularBuffer.primitive, "n1"), "n2"), lam("x", app("load", app("f", "x")))), "in")
  )

  // TODO: generalize to rotateValues(write: Expr)
  val rotateValuesScalar: Rule = NamedRewrite.init("rotate-values-scalar",
    nApp(nApp(slide, "sz"), 1) --> app(nApp(rcp.rotateValues.primitive, "sz"), lam("x", "x"))
  )

  /*
  object combinatory {
    val composition: Rule = NamedRewrite.init("composition",
      app("f", app("g", "x")) --> app("g" >> "f", "x")
    )
  }
   */
}
