package rise.core.DSL

import Type.freshTypeIdentifier
import rise.core.traverse._
import rise.core._
import rise.core.types.InferenceException.error
import rise.core.types._

import scala.collection.mutable

object infer {
  private [DSL] def apply(e: Expr,
            printFlag: Flags.PrintTypesAndTypeHoles = Flags.PrintTypesAndTypeHoles.Off,
            explDep: Flags.ExplicitDependence = Flags.ExplicitDependence.Off): Expr = {
    // Constraints of the form `implicit type var == explicit type var` result in substitutions
    // `implicit type var -> explicit type var`. We (ab)use that fact to create directed constraints out of
    // type assertions and opaque types. To do so, we make the type identifiers on one side of the constraint explicit,
    // and we return a `ftvSubs` map that maps these explicit type identifiers back to implicit type identifiers.
    val (_, typed_e, constraints, ftvSubs) = constrainTypes(Map())(e)
    // Applies ftvSubs to the constraint solutions
    val solution = Constraint.solve(constraints.reverse, Seq())(explDep) ++ ftvSubs
    val res = traverse(typed_e, Visitor(solution))
    if (printFlag == Flags.PrintTypesAndTypeHoles.On) {
      printTypesAndTypeHoles(res)
    }
    res
  }

  def printTypesAndTypeHoles(expr: Expr): Unit = {
    // TODO: move holeFound state into the traverse
    var holeFound = false
    traverse(expr, new PureExprTraversal {
      override def expr : Expr => Pure[Expr] = {
        case h@primitives.typeHole(msg) =>
          println(s"found type hole ${msg}: ${h.t}")
          holeFound = true
          return_(h : Expr)
        case p@primitives.printType(msg) =>
          println(s"$msg : ${p.t} (Rise level)")
          return_(p : Expr)
        case e => super.expr(e)
      }
    })
    if (holeFound) {
      error("type holes were found")(Seq())
    }
  }

  implicit class TrivialSolutionConcat(a: Solution) {
    def <>(b: Solution): Solution =
      new Solution(
        a.ts ++ b.ts,
        a.ns ++ b.ns,
        a.as ++ b.as,
        a.n2ds ++ b.n2ds,
        a.n2ns ++ b.n2ns,
        a.natColls ++ b.natColls
      )
  }

  private def implToExpl[K <: Kind.Identifier with Kind.Explicitness] : K => (K, K) = i =>
    (i.asImplicit.asInstanceOf[K], i.asExplicit.asInstanceOf[K])

  private def freeze(ftvSubs: Solution, t: Type): Type =
    Solution(
      ftvSubs.ts.keySet.map(_.asInstanceOf[DataTypeIdentifier]).map(implToExpl).toMap,
      ftvSubs.ns.keySet.map(implToExpl).toMap,
      ftvSubs.as.keySet.map(implToExpl).toMap,
      ftvSubs.n2ds.keySet.map(implToExpl).toMap,
      ftvSubs.n2ns.keySet.map(implToExpl).toMap,
      ftvSubs.natColls.keySet.map(implToExpl).toMap
    )(t)

  private def explToImpl[K <: Kind.Identifier with Kind.Explicitness] : K => Map[K, K] = i =>
    Map(i.asExplicit.asInstanceOf[K] -> i.asImplicit.asInstanceOf[K])

  private def getFTVSubs(t: Type): Solution = {
    getFTVs(t).foldLeft(Solution())((solution, ftv) =>
      solution match {
        case s@Solution(ts, ns, as, n2ds, n2ns, natColls) =>
          ftv match {
            case _: TypeIdentifier => throw TypeException("TypeIdentifier cannot be frozen")
            case i: DataTypeIdentifier      => s.copy(ts = ts ++ explToImpl(i))
            case i: NatIdentifier           => s.copy(ns = ns ++ explToImpl(i))
            case i: AddressSpaceIdentifier  => s.copy(as = as ++ explToImpl(i))
            case i: NatToDataIdentifier     => s.copy(n2ds = n2ds ++ explToImpl(i))
            case i: NatToNatIdentifier      => s.copy(n2ns = n2ns ++ explToImpl(i))
            case i: NatCollectionIdentifier => s.copy(natColls = natColls ++ explToImpl(i))
            case i => throw TypeException(s"${i.getClass} is not supported yet")
          }
      }
    )
  }

  def getFTVs(t: Type): Seq[Kind.Identifier] = {
    val ftvs = mutable.ListBuffer[Kind.Identifier]()
    traverse(t, new PureTraversal {
      override def typeIdentifier[I <: Kind.Identifier]: VarType => I => Pure[I] = _ => i => {
        i match {
          case i: Kind.Explicitness => if (!i.isExplicit) (ftvs += i)
          case i => ftvs += i
        }
        return_(i)
      }
      override def nat: Nat => Pure[Nat] = ae =>
        return_(ae.visitAndRebuild({
          case i: NatIdentifier if !i.isExplicit => ftvs += i; i
          case n => n
        }))
    })
    ftvs.distinct.toSeq
  }

  def getFTVsRec(e: Expr): Seq[Kind.Identifier] = {
    val ftvs = mutable.ListBuffer[Kind.Identifier]()
    traverse(e, new PureTraversal {
      override def typeIdentifier[I <: Kind.Identifier]: VarType => I => Pure[I] = _ => i => {
        i match {
          case i: Kind.Explicitness => if (!i.isExplicit) (ftvs += i)
          case i => ftvs += i
        }
        return_(i)
      }
      override def nat: Nat => Pure[Nat] = ae =>
        return_(ae.visitAndRebuild({
          case i: NatIdentifier if !i.isExplicit => ftvs += i; i
          case n => n
        }))
    })
    ftvs.distinct.toSeq
  }

  private val genType : Expr => Type = e => if (e.t == TypePlaceholder) freshTypeIdentifier else e.t

  private val constrainTypes : Map[String, Type] => Expr => (Map[String, Type], Expr, Seq[Constraint], Solution) =
    env => {
      case i: Identifier =>
        val t = env.getOrElse(i.name,
          if (i.t =~= TypePlaceholder) error(s"$i has no type")(Seq()) else i.t )
        val c = TypeConstraint(t, i.t)
        val env1 = env + (i.name -> t)
        (env1, i.setType(t), c +: Nil, Solution())

      case expr@Lambda(x, e) =>
        val tx = x.setType(genType(x))
        val env1 : Map[String, Type] = env + (tx.name -> tx.t)
        val (env2, te, cs, ftvE) = constrainTypes(env1)(e)
        val env3 : Map[String, Type] = env2 - tx.name
        val ft = FunType(tx.t, te.t)
        val exprT = genType(expr)
        val c = TypeConstraint(exprT, ft)
        (env3, Lambda(tx, te)(ft), c +: cs, ftvE)


      case expr@App(f, e) =>
        val (env1, tf, csF, ftvF) = constrainTypes(env)(f)
        val (env2, te, csE, ftvE) = constrainTypes(env1)(e)
        val exprT = genType(expr)
        val c = TypeConstraint(tf.t, FunType(te.t, exprT))
        (env2, App(tf, te)(exprT), c +: csF ++: csE, ftvF <> ftvE)

      case expr@DepLambda(x, e) =>
        val (env1, te, csE, ftvE) = constrainTypes(env)(e)
        val exprT = genType(expr)
        val tf = x match {
          case n: NatIdentifier =>
            DepLambda[NatKind](n, te)(DepFunType[NatKind, Type](n, te.t))
          case dt: DataTypeIdentifier =>
            DepLambda[DataKind](dt, te)(DepFunType[DataKind, Type](dt, te.t))
          case ad: AddressSpaceIdentifier =>
            DepLambda[AddressSpaceKind](ad, te)(DepFunType[AddressSpaceKind, Type](ad, te.t))
          case n2n: NatToNatIdentifier =>
            DepLambda[NatToNatKind](n2n, te)(DepFunType[NatToNatKind, Type](n2n, te.t))
        }
        val c = TypeConstraint(exprT, tf.t)
        (env1, tf, c +: csE, ftvE)

      case expr@DepApp(f, x) =>
        val (env1, tf, csF, ftvF) = constrainTypes(env)(f)
        val exprT = genType(expr)
        val c = DepConstraint(tf.t, x, exprT)
        (env1, DepApp(tf, x)(exprT), c +: csF, ftvF)

      case TypeAnnotation(e, t) =>
        val (env1, te, csE, ftvE) = constrainTypes(env)(e)
        val c = TypeConstraint(te.t, t)
        (env1, te, c +: csE, ftvE)

      case TypeAssertion(e, t) =>
        val ftvT = getFTVSubs(t)
        val (env1, te, csE, ftvE) = constrainTypes(env)(e)
        val c = TypeConstraint(te.t, freeze(ftvT, t))
        (env1, te, c +: csE, ftvE <> ftvT)

      case o: Opaque =>
        val ftvO = getFTVSubs(o.t)
        val frozenExpr = Opaque(o.e, freeze(ftvO, o.t))
        (env, frozenExpr, Nil, ftvO)

      case l: Literal => (env, l, Nil, Solution())

      case p: Primitive => (env, p.setType(p.typeScheme), Nil, Solution())
    }

  private case class Visitor(sol: Solution) extends PureTraversal {
    override def expr : Expr => Pure[Expr] = {
      case Opaque(x, _) => return_(x)
      case TopLevel(x, inst) => TopLevel.Visitor(inst, sol).expr(x)
      case e => super.expr(e)
    }
    override def nat : Nat => Pure[Nat] = n => return_(sol(n))
    override def `type`[T <: Type] : T => Pure[T] = t => return_(sol(t).asInstanceOf[T])
    override def addressSpace : AddressSpace => Pure[AddressSpace] = a => return_(sol(a))
    override def natToData : NatToData => Pure[NatToData] = n2d => return_(sol(n2d))
    override def natToNat : NatToNat => Pure[NatToNat] = n2n => return_(sol(n2n))
  }
}

object inferDependent {
  def apply(e: ToBeTyped[Expr],
            printFlag: Flags.PrintTypesAndTypeHoles = Flags.PrintTypesAndTypeHoles.Off): Expr = infer(e match {
    case ToBeTyped(e) => e
  }, printFlag, Flags.ExplicitDependence.On)
}
