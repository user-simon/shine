package AccPatterns

import Core._
import Core.OperationalSemantics._
import apart.arithmetic.ArithExpr
import ir.Type
import opencl.generator.OpenCLAST.VarRef

import scala.xml.Elem

case class SplitAcc(n: ArithExpr,
                    m: ArithExpr,
                    dt: DataType,
                    array: Phrase[AccType]) extends AccPattern {

  override def typeCheck(): AccType = {
    import TypeChecker._
    TypeChecker(array) match {
      case AccType(ArrayType(m_, ArrayType(n_, dt_)))
        if n_ == n && m_ == m && dt == dt_ =>

        AccType(ArrayType(n*m, dt))
      case x => error(x.toString, "ArrayType(ArrayType)")
    }
  }

  override def visitAndRebuild(fun: VisitAndRebuild.fun): Phrase[AccType] = {
    SplitAcc(fun(n), fun(m), fun(dt), VisitAndRebuild(array, fun))
  }

  override def eval(s: Store): AccIdentifier = {
    ???
  }

  override def toOpenCL(env: ToOpenCL.Environment): VarRef = ???

  override def toOpenCL(env: ToOpenCL.Environment,
                        arrayAccess: List[(ArithExpr, ArithExpr)],
                        tupleAccess: List[ArithExpr],
                        dt: DataType): VarRef = {
    val idx = arrayAccess.head
    val stack = arrayAccess.tail

    val chunkId = idx._1 / n
    val chunkElemId = idx._1 % n

    val l = Type.getLengths( DataType.toType(t.dataType) ).reduce(_*_)

    val newAs = (chunkId, l * n) :: (chunkElemId, l) :: stack

    ToOpenCL.acc(array, env, newAs, tupleAccess, dt)
  }

  override def prettyPrint: String = s"(splitAcc ${PrettyPrinter(array)})"

  override def xmlPrinter: Elem =
    <splitAcc n={ToString(n)} m={ToString(m)} dt={ToString(dt)}>
      {Core.xmlPrinter(array)}
    </splitAcc>
}
