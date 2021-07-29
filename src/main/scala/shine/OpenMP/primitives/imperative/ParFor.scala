// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package shine.OpenMP.primitives.imperative
import arithexpr.arithmetic._
import shine.DPIA.Phrases._
import rise.core.types.DataTypeOps._
import shine.DPIA.Types._
import rise.core.types.{ FunType => _, DepFunType => _, TypePlaceholder => _, TypeIdentifier => _, Type => _, _ }
import rise.core.types.DataType._
import rise.core.types.Kind.{ Identifier => _, _ }
import shine.DPIA._
final case class ParFor(val n: Nat, val dt: DataType, val out: Phrase[AccType], val body: Phrase[FunType[ExpType, FunType[AccType, CommType]]]) extends CommandPrimitive {
  assert {
    out :: accT(ArrayType(n, dt))
    body :: FunType(expT(IndexType(n), read), FunType(accT(dt), comm))
    true
  }
  override val t: CommType = comm
  override def visitAndRebuild(v: VisitAndRebuild.Visitor): ParFor = new ParFor(v.nat(n), v.data(dt), VisitAndRebuild(out, v), VisitAndRebuild(body, v))
}
