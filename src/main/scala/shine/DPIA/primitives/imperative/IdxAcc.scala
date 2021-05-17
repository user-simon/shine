// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package shine.DPIA.primitives.imperative
import arithexpr.arithmetic._
import shine.DPIA.Phrases._
import shine.DPIA.Types.DataType._
import shine.DPIA.Types._
import shine.DPIA._
final case class IdxAcc(val n: Nat, val dt: DataType, val index: Phrase[ExpType], val array: Phrase[AccType]) extends AccPrimitive {
  assert {
    index :: expT(IndexType(n), read)
    array :: accT(ArrayType(n, dt))
    true
  }
  override val t: AccType = accT(dt)
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): IdxAcc = new IdxAcc(v.nat(n), v.data(dt), VisitAndRebuild(index, v), VisitAndRebuild(array, v))
}
