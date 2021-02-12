package rise

import arithexpr.arithmetic.RangeUnknown
import rise.core.DSL.Type.NatFunctionWrapper
import rise.core._
import rise.core.types._
import util.{Time, TimeSpan}

package object autotune {
  type Parameters = Set[NatIdentifier]
  case class Sample(p: Parameters, runtime: Option[TimeSpan[Time.ms]], timestamp: Int)

  // should we allow tuning params to be substituted during type inference?
  // this could allow to restrict the search space at compile time
  def tuningParam[A](name: String, w: NatFunctionWrapper[A]): A =
    w.f(NatIdentifier(name, RangeUnknown, isExplicit = true, isTuningParam = true))
  def tuningParam[A](name: String, r: arithexpr.arithmetic.Range, w: NatFunctionWrapper[A]): A =
    w.f(NatIdentifier(name, r, isExplicit = true, isTuningParam = true))

  def search(e: Expr): Seq[Sample] = ???

  def collectParameters(e: Expr): Parameters = {
    var params = scala.collection.mutable.Set[NatIdentifier]()
    traverse(e, new traverse.PureTraversal {
      override def nat: Nat => traverse.Pure[Nat] = n =>
        return_(n.visitAndRebuild({
          case n: NatIdentifier if n.isTuningParam =>
            params += n
            n
          case ae => ae
        }))
    })
    params.toSet
  }

  private def generateJSON(p: Parameters): String = ???

  def applyBest(e: Expr, samples: Seq[Sample]): Expr = ???
}
