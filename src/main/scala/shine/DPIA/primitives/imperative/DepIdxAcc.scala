// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package shine.DPIA.primitives.imperative
import arithexpr.arithmetic._
import shine.DPIA.Phrases._
import shine.DPIA.Types._
import rise.core.types.{ FunType => _, DepFunType => _, TypePlaceholder => _, TypeIdentifier => _, ExprType => _, _ }
import rise.core.types.DataType._
import rise.core.types.Kind.{ Identifier => _, _ }
import shine.DPIA._
final case class DepIdxAcc(val n: Nat, val ft: NatToData, val index: Nat, val array: Phrase[AccType]) extends AccPrimitive {
  assert {
    array :: accT(DepArrayType(n, ft))
    true
  }
  override val t: AccType = accT(NatToDataApply(ft, index))
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): DepIdxAcc = new DepIdxAcc(v.nat(n), v.natToData(ft), v.nat(index), VisitAndRebuild(array, v))
}
