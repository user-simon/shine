package idealised.DPIA.Primitives

import benchmarks.core.SimpleRunOpenCLProgram
import idealised.DPIA
import idealised.DPIA.Nat
import idealised.OpenCL.KernelWithSizes
import idealised.util.SyntaxChecker
import idealised.utils.Time.ms
import idealised.utils.TimeSpan
import lift.arithmetic._
import lift.core.DSL._
import lift.core._
import lift.core.primitives._
import lift.core.types._

import scala.util.Random


class Partition extends idealised.util.Tests {
  test("Simple partition into a triangle C") {
    val lenF = n2nFun((i: NatIdentifier) => i + 1)

    val slideExample =
      nFun(n =>
        fun(ArrayType(n, float))(xs => xs |> partition(3)(lenF) |> depMapSeq(mapSeq(fun(x => x)))))

    val p = idealised.C.ProgramGenerator.makeCode(DPIA.fromLift(infer(slideExample)))
    val code = p.code
    SyntaxChecker(code)
  }

  test("Partition threeway with pad and unrolling") {
    val padAmount = 3

    def lenF(n: Nat) = n2nFun((i: NatIdentifier) => SteppedCase(3, n, 3)(i))

    val padAndPartition: Expr = nFun(n =>
      fun(ArrayType(n, float))(xs => xs |>
        padCst(padAmount)(padAmount)(l(0.0f)) |>
        partition(3)(lenF(n)) |>
        depMapSeq(mapSeq(fun(x => x + l(1.0f))))))

    case class Run() extends SimpleRunOpenCLProgram(false) {
      val inputSize: Int = 128

      override type Input = Array[Float]

      override def expr: Expr = padAndPartition

      override protected def makeInput(random: Random): Input = {
        Array.fill(inputSize)(5.0f)
      }

      override protected def runScalaProgram(input: Array[Float]): Array[Float] = {
        (Array.fill(padAmount)(0.0f) ++ input ++ Array.fill(padAmount)(0.0f)).map(x => x + 1.0f)
      }

      override protected def runKernel(k: KernelWithSizes, input: Array[Float]): (Array[Float], TimeSpan[ms]) = {
        import idealised.OpenCL._

        val kernelFun = k.as[ScalaFunction `(` Int `,` Input `)=>` Array[Float]]
        kernelFun(inputSize `,` input)
      }
    }

    Run().run(1, 1).correctness.check()
  }
}
