// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package shine.cuda.primitives.imperative
import arithexpr.arithmetic._
import shine.DPIA.Phrases._
import shine.DPIA.Types._
import rise.core.types.{ FunType => _, DepFunType => _, TypePlaceholder => _, TypeIdentifier => _, ExprType => _, _ }
import rise.core.types.DataType._
import rise.core.types.Kind.{ Identifier => _, _ }
import shine.DPIA._
final case class WmmaFill(val rows: Nat, val columns: Nat, val layers: Nat, val dt: DataType, val frag: Fragment, val layout: MatrixLayout, val fill: Phrase[ExpType], val target: Phrase[AccType]) extends CommandPrimitive {
  assert {
    fill :: expT(dt, read)
    target :: accT(FragmentType(rows, columns, layers, dt, frag, layout))
    true
  }
  override val t: CommType = comm
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): WmmaFill = new WmmaFill(v.nat(rows), v.nat(columns), v.nat(layers), v.data(dt), frag, layout, VisitAndRebuild(fill, v), VisitAndRebuild(target, v))
}
