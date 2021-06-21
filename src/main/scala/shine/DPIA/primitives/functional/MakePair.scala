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
final case class MakePair(val dt1: DataType, val dt2: DataType, val a: AccessType, val fst: Phrase[ExpType], val snd: Phrase[ExpType]) extends ExpPrimitive {
  assert {
    fst :: expT(dt1, a)
    snd :: expT(dt2, a)
    true
  }
  override val t: ExpType = expT(PairType(dt1, dt2), a)
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): MakePair = new MakePair(v.data(dt1), v.data(dt2), v.access(a), VisitAndRebuild(fst, v), VisitAndRebuild(snd, v))
}
