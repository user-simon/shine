// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package shine.DPIA.primitives.imperative
import arithexpr.arithmetic._
import shine.DPIA.Phrases._
import shine.DPIA.Types.DataType._
import shine.DPIA.Types._
import shine.DPIA._
final case class AsScalarAcc(val n: Nat, val m: Nat, val dt: DataType, val array: Phrase[AccType]) extends AccPrimitive {
  {
    array :: accT(ArrayType(m * n, dt))
  }
  override val t: AccType = accT(ArrayType(n, VectorType(m, dt)))
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): AsScalarAcc = new AsScalarAcc(v.nat(n), v.nat(m), v.data(dt), VisitAndRebuild(array, v))
}
