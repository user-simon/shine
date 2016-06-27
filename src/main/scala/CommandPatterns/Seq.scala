package CommandPatterns

import Core._
import Core.OperationalSemantics._
import Compiling.SubstituteImplementations
import Core.PrettyPrinter.Indent
import opencl.generator.OpenCLAST.Block

case class Seq(c1: Phrase[CommandType],
               c2: Phrase[CommandType])
  extends CommandPattern {

  override def typeCheck(): CommandType = {
    import TypeChecker._
    check(TypeChecker(c1), CommandType())
    check(TypeChecker(c2), CommandType())
    CommandType()
  }

  override def eval(s: Store): Store = {
    val s1 = OperationalSemantics.eval(s, c1)
    OperationalSemantics.eval(s1, c2)
  }

  override def visitAndRebuild(fun: VisitAndRebuild.fun): Phrase[CommandType] = {
    Seq(VisitAndRebuild(c1, fun), VisitAndRebuild(c2, fun))
  }

  override def substituteImpl(env: SubstituteImplementations.Environment): Phrase[CommandType] =
    Seq(SubstituteImplementations(c1, env), SubstituteImplementations(c2, env))

  override def toOpenCL(block: Block, ocl: ToOpenCL): Block = {
    ToOpenCL.cmd(c1, block, ocl)
    ToOpenCL.cmd(c2, block, ocl)
  }

  override def prettyPrint(indent: Indent): String =
    indent + s"(\n" +
      s"${PrettyPrinter(c1, indent.more)}\n" +
      indent.more + ";\n" +
      s"${PrettyPrinter(c2, indent.more)}\n" +
      indent + s") : comm"

}
