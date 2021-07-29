// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package shine.cuda.primitives.functional
import arithexpr.arithmetic._
import shine.DPIA.Phrases._
import rise.core.types.DataTypeOps._
import shine.DPIA.Types._
import rise.core.types.{ FunType => _, DepFunType => _, TypePlaceholder => _, TypeIdentifier => _, Type => _, _ }
import rise.core.types.DataType._
import rise.core.types.Kind.{ Identifier => _, _ }
import shine.DPIA._
final case class MapFragment(val rows: Nat, val columns: Nat, val layers: Nat, val dt: DataType, val frag: Fragment, val layout: MatrixLayout, val f: Phrase[FunType[ExpType, ExpType]], val input: Phrase[ExpType]) extends ExpPrimitive {
  assert {
    f :: FunType(expT(dt, read), expT(dt, write))
    input :: expT(FragmentType(rows, columns, layers, dt, frag, layout), read)
    true
  }
  override val t: ExpType = expT(FragmentType(rows, columns, layers, dt, frag, layout), write)
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): MapFragment = new MapFragment(v.nat(rows), v.nat(columns), v.nat(layers), v.data(dt), frag, layout, VisitAndRebuild(f, v), VisitAndRebuild(input, v))
}
