package idealised.SurfaceLanguage.Primitives

import idealised.DPIA
import idealised.SurfaceLanguage.Types._
import idealised.SurfaceLanguage._

//noinspection TypeAnnotation
final case class ReduceSeqUnroll(f: Expr, init: Expr, array: Expr,
                                 override val t: Option[DataType])
  extends AbstractReduce(f, init, array, t) {

  override def makeDPIAReduce = DPIA.FunctionalPrimitives.ReduceSeqUnroll

  override def makeReduce = ReduceSeqUnroll

}
