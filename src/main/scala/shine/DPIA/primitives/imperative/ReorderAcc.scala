// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package shine.DPIA.primitives.imperative
import arithexpr.arithmetic._
import shine.DPIA.Phrases._
import shine.DPIA.Types.DataType._
import shine.DPIA.Types._
import shine.DPIA._
final case class ReorderAcc(val n: Nat, val dt: DataType, val idxF: NatToNat, val array: Phrase[AccType]) extends AccPrimitive {
  {
    array :: accT(ArrayType(n, dt))
  }
  override val t: AccType = accT(ArrayType(n, dt))
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): ReorderAcc = new ReorderAcc(v.nat(n), v.data(dt), v.natToNat(idxF), VisitAndRebuild(array, v))
}
