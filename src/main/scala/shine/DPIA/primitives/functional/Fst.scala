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
final case class Fst(val dt1: DataType, val dt2: DataType, val pair: Phrase[ExpType]) extends ExpPrimitive {
  assert {
    pair :: expT(PairType(dt1, dt2), read)
    true
  }
  override val t: ExpType = expT(dt1, read)
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): Fst = new Fst(v.data(dt1), v.data(dt2), VisitAndRebuild(pair, v))
}
