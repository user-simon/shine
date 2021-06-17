// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package rise.core.primitives
import rise.core.DSL._
import rise.core.DSL.Type._
import rise.core._
import rise.core.types._
import arithexpr.arithmetic._
final case class foreignFunction(funDecl: rise.core.ForeignFunction.Decl, n: Int) extends Builder {
  override def toString: String = "foreignFunction"
  override def primitive: rise.core.Primitive = foreignFunction.Primitive(funDecl, n)()
  override def apply: ToBeTyped[rise.core.Primitive] = toBeTyped(foreignFunction.Primitive(funDecl, n)())
  override def unapply(arg: Expr): Boolean = arg match {
    case _: Primitive => true
    case _ => false
  }
}
object foreignFunction {
  private final case class Primitive(funDecl: rise.core.ForeignFunction.Decl, n: Int)(override val t: Type = TypePlaceholder) extends rise.core.Primitive {
    override val name: String = "foreignFunction"
    override def setType(ty: Type): Primitive = Primitive(funDecl, n)(ty)
    override def typeScheme: Type = {
      val inTs = Seq.fill(n)(DataTypeIdentifier(freshName("dt")))
      inTs.foldRight(expl { (outT: DataType) => inTs.foldRight(outT: Type)({
        case (lhsT, rhsT) =>
          lhsT ->: rhsT
      }) }: Type)({
        case (id, t) =>
          DepFunType(DataKind, id, t)
      })
    }
    override def primEq(obj: rise.core.Primitive): Boolean = obj match {
      case p: Primitive =>
        p.funDecl == funDecl && (p.n == n && true)
      case _ =>
        false
    }
  }
  def unapply(arg: rise.core.Expr): Option[(rise.core.ForeignFunction.Decl, Int)] = arg match {
    case p: Primitive =>
      Some(p.funDecl, p.n)
    case _ =>
      None
  }
}
