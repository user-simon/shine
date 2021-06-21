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
final case class Scatter(val n: Nat, val m: Nat, val dt: DataType, val indices: Phrase[ExpType], val input: Phrase[ExpType]) extends ExpPrimitive {
  assert {
    indices :: expT(ArrayType(n, IndexType(m)), read)
    input :: expT(ArrayType(n, dt), write)
    true
  }
  override val t: ExpType = expT(ArrayType(m, dt), write)
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): Scatter = new Scatter(v.nat(n), v.nat(m), v.data(dt), VisitAndRebuild(indices, v), VisitAndRebuild(input, v))
}
