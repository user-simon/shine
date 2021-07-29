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
final case class ZipAcc2(val n: Nat, val dt1: DataType, val dt2: DataType, val array: Phrase[AccType]) extends AccPrimitive {
  assert {
    array :: accT(ArrayType(n, PairType(dt1, dt2)))
    true
  }
  override val t: AccType = accT(ArrayType(n, dt2))
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): ZipAcc2 = new ZipAcc2(v.nat(n), v.data(dt1), v.data(dt2), VisitAndRebuild(array, v))
}
