package shine.OpenCL.IntermediatePrimitives

import shine.DPIA.Compilation.TranslationContext
import shine.DPIA.Compilation.TranslationToImperative.acc
import shine.DPIA.DSL.{`new` => _, _}
import shine.DPIA.Phrases._
import shine.DPIA.Types._
import shine.DPIA._
import shine.OpenCL.AdjustArraySizesForAllocations
import shine.OpenCL.DSL._


object OpenCLScanSeqI {
  def apply(n: Nat,
            initAddrSpace: shine.DPIA.Types.AddressSpace,
            dt1: DataType, dt2: DataType,
            f: Phrase[ExpType ->: ExpType ->: AccType ->: CommType],
            init: Phrase[ExpType],
            in: Phrase[ExpType],
            out: Phrase[AccType])
           (implicit context: TranslationContext): Phrase[CommType] = {
    val adj = AdjustArraySizesForAllocations(init, dt2, initAddrSpace)

    comment("oclScanSeqI") `;`
      `new`(initAddrSpace)(adj.dt, accumulator =>
        acc(init)(adj.accF(accumulator.wr)) `;`
          `for`(n, i =>
            f(in `@` i)(adj.exprF(accumulator.rd))(adj.accF(accumulator.wr)) `;`
              //FIXME remove general assignment
              ((out `@` i) :=| dt2 | adj.exprF(accumulator.rd))
          )
      )
  }
}
