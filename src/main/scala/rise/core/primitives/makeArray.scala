// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
// This file is automatically generated and should not be changed manually //
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
package rise.core.primitives
import rise.core.DSL._
import rise.core.DSL.Type._
import rise.core._
import rise.core.types._
import arithexpr.arithmetic._
final case class makeArray(n: Int) extends Builder {
  override def toString: String = "makeArray"
  override def primitive: rise.core.Primitive = makeArray.Primitive(n)()
  override def apply: ToBeTyped[rise.core.Primitive] = toBeTyped(makeArray.Primitive(n)())
  override def unapply(arg: Expr): Boolean = arg match {
    case _: Primitive => true
    case _ => false
  }
}
object makeArray {
  private final case class Primitive(n: Int)(override val t: Type = TypePlaceholder) extends rise.core.Primitive {
    override val name: String = "makeArray"
    override def setType(ty: Type): Primitive = Primitive(n)(ty)
    override def typeScheme: Type = impl { (dt: DataType) => Seq.fill(n)(dt).foldRight(ArrayType(n, dt): Type)({
      case (lhsT, rhsT) =>
        lhsT ->: rhsT
    }) }
    override def primEq(obj: rise.core.Primitive): Boolean = obj match {
      case p: Primitive =>
        p.n == n && true
      case _ =>
        false
    }
  }
  def unapply(arg: rise.core.Expr): Option[Int] = arg match {
    case p: Primitive =>
      Some(p.n)
    case _ =>
      None
  }
}
