package rise.core

import util.monads._
import arithexpr.arithmetic.NamedVar
import rise.core.traverse._
import rise.core.types._

object IsClosedForm {
  case class OrderedSet[T](ordered : Seq[T], unique : Set[T])
  object OrderedSet {
    def one[T] : T => OrderedSet[T] = t => OrderedSet(Seq(t), Set(t))
    def add[T] : T => OrderedSet[T] => OrderedSet[T] = t => ts =>
      if (ts.unique.contains(t)) ts else OrderedSet(t +: ts.ordered, ts.unique + t)
    def empty[T] : OrderedSet[T] = OrderedSet(Seq(), Set())
    def append[T] : OrderedSet[T] => OrderedSet[T] => OrderedSet[T] = x => y => {
      val ordered = x.ordered.filter(!y.unique.contains(_)) ++ y.ordered
      val unique = x.unique ++ y.unique
      OrderedSet(ordered, unique)
    }
  }
  implicit def OrderedSetMonoid[T] : Monoid[OrderedSet[T]] = new Monoid[OrderedSet[T]] {
    def empty : OrderedSet[T] = OrderedSet.empty
    def append : OrderedSet[T] => OrderedSet[T] => OrderedSet[T] = OrderedSet.append
  }

  case class Visitor(boundV: Set[Identifier], boundT: Set[Kind.Identifier])
    extends PureAccumulatorTraversal[(OrderedSet[Identifier], OrderedSet[Kind.Identifier])]
  {
    override val accumulator = PairMonoid(OrderedSetMonoid, OrderedSetMonoid)

    override def identifier[I <: Identifier]: VarType => I => Pair[I] = vt => i => {
      for { t2 <- `type`(i.t);
            i2 <- if (vt == Reference && !boundV(i)) {
                    accumulate((OrderedSet.one(i : Identifier), OrderedSet.empty))(i)
                  } else {
                    return_(i)
                  }}
        yield i2
    }

    override def typeIdentifier[I <: Kind.Identifier]: VarType => I => Pair[I] = {
      case Reference => i =>
        if (boundT(i)) return_(i) else accumulate((OrderedSet.empty, OrderedSet.one(i : Kind.Identifier)))(i)
      case _ => return_
    }

    override def nat: Nat => Pair[Nat] = n => {
      val free = n.varList.foldLeft(OrderedSet.empty[Kind.Identifier]) {
        case (free, v: NamedVar) if !boundT(NatIdentifier(v)) => OrderedSet.add(NatIdentifier(v) : Kind.Identifier)(free)
        case (free, _) => free
      }
      accumulate((OrderedSet.empty, free))(n)
    }

    override def expr: Expr => Pair[Expr] = {
      case l@Lambda(x, e) =>
        // The binder's type itself might contain free type variables
        val ((fVx, fTx), x1) = identifier(Binding)(x).unwrap
        val ((fVe, fTe), e1) = this.copy(boundV = boundV + x1).expr(e).unwrap
        val ((fVt, fTt), t1) = `type`(l.t).unwrap
        val fV = OrderedSet.append(OrderedSet.append(fVx)(fVe))(fVt)
        val fT = OrderedSet.append(OrderedSet.append(fTx)(fTe))(fTt)
        accumulate((fV, fT))(Lambda(x1, e1)(t1): Expr)
      case DepLambda(x, b) => this.copy(boundT = boundT + x).expr(b)
      case e => super.expr(e)
    }

    override def natToData: NatToData => Pair[NatToData] = {
      case NatToDataLambda(x, e) =>
        for { p <- this.copy(boundT = boundT + x).`type`(e) }
          yield (p._1, NatToDataLambda(x, e))
      case t => super.natToData(t)
    }

    override def natToNat: NatToNat => Pair[NatToNat] = {
      case NatToNatLambda(x, n) =>
        for { p <- this.copy(boundT = boundT + x).nat(n) }
          yield (p._1, NatToNatLambda(x, n))
      case n => super.natToNat(n)
    }

    override def `type`[T <: Type]: T => Pair[T] = {
      case d@DepFunType(x, t) =>
        for { p <- this.copy(boundT = boundT + x).`type`(t) }
          yield (p._1, d.asInstanceOf[T])
      case d@DepPairType(x, dt) =>
        for { p <- this.copy(boundT = boundT + x).datatype(dt) }
          yield (p._1, d.asInstanceOf[T])
      case t => super.`type`(t)
    }
  }

  def freeVars(expr: Expr): (Seq[Identifier], Seq[Kind.Identifier]) = {
    val (fV, fT) = traverse(expr, Visitor(Set(), Set()))._1
    (fV.ordered, fT.ordered)
  }

  def varsToClose(expr : Expr): (Seq[Identifier], Seq[Kind.Identifier]) = {
    val (fV, fT) = freeVars(expr)
    // Exclude matrix layout and fragment kind identifiers, since they cannot currently be bound
    (fV, fT.flatMap {
      case i : MatrixLayoutIdentifier => Seq()
      case i : FragmentKindIdentifier => Seq()
      case e => Seq(e)
    })
  }

  def apply(expr: Expr): Boolean = {
    val (freeV, freeT) = varsToClose(expr)
    freeV.isEmpty && freeT.isEmpty
  }
}