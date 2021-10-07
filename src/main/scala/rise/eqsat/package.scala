package rise

import scala.collection.mutable

/** Equality Saturation for Rise,
  * based on the [[https://egraphs-good.github.io/ `egg` library]].
  */
package object eqsat {
  type ENode = Node[EClassId, NatId, DataTypeId]
  type PNode = Node[Pattern, NatPattern, DataTypePattern]

  /** A key to identify [[EClass]]es within an [[EGraph]] */
  case class EClassId(i: Int)

  /** A key to identify interned nats */
  case class NatId(i: Int)

  /** A key to identify interned types */
  sealed trait TypeId
  /** A key to identify interned data types */
  case class DataTypeId(i: Int) extends TypeId
  /** A key to identify interned types which are not data types */
  case class NotDataTypeId(i: Int) extends TypeId

  type Vec[T] = mutable.ArrayBuffer[T]
  val Vec: mutable.ArrayBuffer.type = mutable.ArrayBuffer
  type HashMap[K, V] = mutable.HashMap[K, V]
  val HashMap: mutable.HashMap.type = mutable.HashMap
  type HashSet[V] = mutable.HashSet[V]
  val HashSet: mutable.HashSet.type = mutable.HashSet

  trait NF {
    val normalize: Expr => Expr
    val rules: Seq[RewriteDirected]
  }

  object BENF extends NF {
    override val normalize: Expr => Expr = BENF
    override val rules: Seq[RewriteDirected] = Seq(
      RewriteDirected.Eta,
      RewriteDirected.BetaExtract,
      RewriteDirected.BetaNatExtract
    )
  }

  object CNF extends NF {
    override val normalize: Expr => Expr = CNF
    override val rules: Seq[RewriteDirected] = Seq(
      RewriteDirected.Eta,
      RewriteDirected.BetaExtract,
      RewriteDirected.BetaNatExtract,
      RewriteDirected.CompositionIntro,
      RewriteDirected.CompositionAssoc2
    )
  }

  // NOTE: this is not quite BENF, since cost is AstSize
  def BENF(e: Expr): Expr = {
    val egraph = EGraph.empty()
    val id = egraph.addExpr(e)
    ExprWithHashCons.expr(egraph)(BENF_internal(egraph, id))
  }
  def BENF(e: ExprWithHashCons, hc: HashConses): ExprWithHashCons = {
    val egraph = EGraph.empty()
    egraph.hashConses = hc
    val id = egraph.addExpr(e)
    BENF_internal(egraph, id)
  }

  private def BENF_internal(egraph: EGraph, id: EClassId): ExprWithHashCons = {
    Runner.init().run(egraph, NoPredicate(), Seq(), BENF.rules, Seq(id))
    val (normalized, _) = Extractor.findBestOf(egraph, AstSize, id)
    normalized
  }

  // Combinator Normal Form
  def CNF(e: Expr): Expr = {
    val egraph = EGraph.empty()
    val id = egraph.addExpr(BENF(e))
    Runner.init().run(egraph, NoPredicate(), Seq(), CNF.rules, Seq(id))
    // val extractor = Extractor.init(egraph, LexicographicCost(AppCount, AstSize))
    // val (_, normalized) = extractor.findBestOf(id)
    val analyser = Analyser.init(egraph,
      AvoidCompositionAssoc1Extract(LexicographicCost(AppCount, AstSize)))
    val (avoidCount, _, normalized) = analyser.analysisOf(id).best
    assert(avoidCount == 0)
    ExprWithHashCons.expr(egraph)(normalized)
  }
}
