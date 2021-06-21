// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package shine.DPIA.primitives.functional
import arithexpr.arithmetic._
import shine.DPIA.Phrases._
import shine.DPIA.Types.DataType._
import shine.DPIA.Types._
import shine.DPIA.Types.Kind.{ Identifier => _, _ }
import shine.DPIA._
final case class Drop(val n: Nat, val m: Nat, val dt: DataType, val array: Phrase[ExpType]) extends ExpPrimitive {
  assert {
    array :: expT(ArrayType(n + m, dt), read)
    true
  }
  override val t: ExpType = expT(ArrayType(m, dt), read)
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): Drop = new Drop(v.nat(n), v.nat(m), v.data(dt), VisitAndRebuild(array, v))
}
