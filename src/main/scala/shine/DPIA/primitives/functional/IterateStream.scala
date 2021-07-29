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
final case class IterateStream(val n: Nat, val dt1: DataType, val dt2: DataType, val f: Phrase[FunType[ExpType, ExpType]], val array: Phrase[ExpType]) extends ExpPrimitive {
  assert {
    f :: FunType(expT(dt1, read), expT(dt2, write))
    array :: expT(ArrayType(n, dt1), read)
    true
  }
  override val t: ExpType = expT(ArrayType(n, dt2), write)
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): IterateStream = new IterateStream(v.nat(n), v.data(dt1), v.data(dt2), VisitAndRebuild(f, v), VisitAndRebuild(array, v))
}
