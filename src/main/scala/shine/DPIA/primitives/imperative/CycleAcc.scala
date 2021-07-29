// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package shine.DPIA.primitives.imperative
import arithexpr.arithmetic._
import shine.DPIA.Phrases._
import rise.core.types.DataTypeOps._
import shine.DPIA.Types._
import rise.core.types.{ FunType => _, DepFunType => _, TypePlaceholder => _, TypeIdentifier => _, Type => _, _ }
import rise.core.types.DataType._
import rise.core.types.Kind.{ Identifier => _, _ }
import shine.DPIA._
final case class CycleAcc(val n: Nat, val m: Nat, val dt: DataType, val input: Phrase[AccType]) extends AccPrimitive {
  assert {
    input :: accT(ArrayType(m, dt))
    true
  }
  override val t: AccType = accT(ArrayType(n, dt))
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): CycleAcc = new CycleAcc(v.nat(n), v.nat(m), v.data(dt), VisitAndRebuild(input, v))
}
