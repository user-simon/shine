// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package shine.DPIA.primitives.functional
import arithexpr.arithmetic._
import shine.DPIA.Phrases._
import rise.core.types.DataTypeOps._
import shine.DPIA.Types._
import rise.core.types.{ FunType => _, DepFunType => _, TypePlaceholder => _, TypeIdentifier => _, Type => _, _ }
import rise.core.types.DataType._
import rise.core.types.Kind.{ Identifier => _, _ }
import shine.DPIA._
final case class VectorFromScalar(val n: Nat, val dt: DataType, val arg: Phrase[ExpType]) extends ExpPrimitive {
  assert {
    arg :: expT(dt, read)
    true
  }
  override val t: ExpType = expT(VectorType(n, dt), read)
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): VectorFromScalar = new VectorFromScalar(v.nat(n), v.data(dt), VisitAndRebuild(arg, v))
}
