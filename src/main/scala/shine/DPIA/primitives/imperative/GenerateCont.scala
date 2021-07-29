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
final case class GenerateCont(val n: Nat, val dt: DataType, val f: Phrase[FunType[ExpType, FunType[FunType[ExpType, CommType], CommType]]]) extends ExpPrimitive {
  assert {
    f :: FunType(expT(IndexType(n), read), FunType(FunType(expT(dt, read), comm), comm))
    true
  }
  override val t: ExpType = expT(ArrayType(n, dt), read)
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): GenerateCont = new GenerateCont(v.nat(n), v.data(dt), VisitAndRebuild(f, v))
}
