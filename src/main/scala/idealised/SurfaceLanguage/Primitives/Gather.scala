package idealised.SurfaceLanguage.Primitives

import idealised.DPIA
import idealised.SurfaceLanguage.DSL.DataExpr
import idealised.SurfaceLanguage.Types.TypeInference
import idealised.SurfaceLanguage.Types._
import idealised.SurfaceLanguage._

import scala.language.{postfixOps, reflectiveCalls}

final case class Gather(idxF: Expr[`(nat)->`[DataType ->DataType]],
                        array: DataExpr,
                        override val `type`: Option[DataType] = None)
  extends PrimitiveExpr
{


  override def toDPIA: DPIA.Phrases.Phrase[DPIA.Types.ExpType] = {
    array.`type` match {
      case Some(ArrayType(n, dt)) =>
        DPIA.FunctionalPrimitives.Gather(n, dt, ToDPIA(idxF), ToDPIA(array))
      case _ => throw new Exception("")
    }
  }

  override def inferType(subs: TypeInference.SubstitutionMap): DataExpr = {
    import TypeInference._
    val array_ = TypeInference(array, subs)
    array_.`type` match {
      case Some(ArrayType(n, dt)) =>
        val idxF_ = TypeInference(idxF, subs)
        val lifted = Lifting.liftNatDependentFunctionExpr(idxF_)
        println(n)
        println(lifted(n))
        lifted(n).`type` match {
          case Some(FunctionType(IndexType(m1), IndexType(m2))) =>
            if (n == m1 && n == m2) {
              Gather(idxF_, array_, array_.`type`)
            } else {
              error(expr = s"Gather($idxF_, $array_)",
                found = s"`$n', `$m1' and `$m2'", expected = "them to match")
            }
//          case Some(NatDependentFunctionType(x, FunctionType(IndexType(m1_), IndexType(m2_)))) =>
//            val m1 = m1_ `[` n `/` x `]`
//            val m2 = m2_ `[` n `/` x `]`
//
//            println(m1_)
//            println(m2_)
//
//            if (n == m1 && n == m2) {
//              Gather(idxF_, array_, array_.`type`)
//            } else {
//              error(expr = s"Gather($idxF_, $array_)",
//                found = s"`$n', `$m1' and `$m2'", expected = "them to match")
//            }
          case x => error(expr = s"Gather($idxF_, $array_)",
            found = s"`${x.toString}'", expected = "idx(_) -> idx(_)")
        }
      case x => error(expr = s"Gather($idxF, $array_)",
        found = s"`${x.toString}'", expected = "n.dt")
    }
  }

  override def visitAndRebuild(f: VisitAndRebuild.Visitor): DataExpr = {
    Gather(VisitAndRebuild(idxF, f), VisitAndRebuild(array, f))
  }
}
