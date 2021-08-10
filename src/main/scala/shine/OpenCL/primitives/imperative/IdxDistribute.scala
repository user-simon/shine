// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package shine.OpenCL.primitives.imperative
import arithexpr.arithmetic._
import shine.DPIA.Phrases._
import shine.DPIA.Types._
import rise.core.types.{ FunType => _, DepFunType => _, TypePlaceholder => _, TypeIdentifier => _, ExprType => _, _ }
import rise.core.types.DataType._
import rise.core.types.Kind.{ Identifier => _, _ }
import shine.DPIA._
final case class IdxDistribute(parallelismLevel: shine.OpenCL.ParallelismLevel)(val m: Nat, val n: Nat, val stride: Nat, val dt: DataType, val array: Phrase[ExpType]) extends ExpPrimitive {
  assert {
    array :: expT(ArrayType(m, dt), read)
    true
  }
  override val t: ExpType = expT(ArrayType(n, dt), read)
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): IdxDistribute = new IdxDistribute(parallelismLevel)(v.nat(m), v.nat(n), v.nat(stride), v.data(dt), VisitAndRebuild(array, v))
  def unwrap: (Nat, Nat, Nat, DataType, Phrase[ExpType]) = (m, n, stride, dt, array)
}
