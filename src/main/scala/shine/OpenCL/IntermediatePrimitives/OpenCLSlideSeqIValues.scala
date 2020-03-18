package shine.OpenCL.IntermediatePrimitives

import shine.DPIA.DSL._
import shine.DPIA.FunctionalPrimitives.{Drop}
import shine.DPIA.ImperativePrimitives._
import shine.DPIA.IntermediatePrimitives.MapSeqI
import shine.DPIA.Phrases._
import shine.DPIA.Types._
import shine.DPIA.Types.DataType._
import shine.DPIA._

object OpenCLSlideSeqIValues {
  def apply(
    a: AddressSpace,
    n: Nat,
    size: Nat,
    step: Nat,
    dt1: DataType,
    dt2: DataType,
    load: Phrase[ExpType ->: AccType ->: CommType],
    nextInput: Phrase[`(nat)->:`[(ExpType ->: CommType) ->: CommType]],
    nextC: Phrase[`(nat)->:`[(ExpType ->: CommType) ->: CommType] ->: CommType]
  ): Phrase[CommType] = {
    assert(step.eval == 1) // FIXME?
    // FIXME: need an additional function to rotate values?
    assert(dt1 == dt2)

    // TODO: unroll flags?
    shine.OpenCL.DSL.`new`(a)(size`.`dt2, rs => {
      // prologue initialisation
      forNat(size - 1, i => streamNext(nextInput, i, fun(expT(dt1, read))(x =>
        load(x)(rs.wr `@` i)
      )), unroll = true) `;`
      nextC(nFun(i =>
        fun(expT(size`.`dt2, read) ->: (comm: CommType))(k =>
          // load next value
          streamNext(nextInput, i + size - 1, fun(expT(dt1, read))(x =>
            load(x)(rs.wr `@` (size - 1))
          )) `;`
          // use neighborhood
          k(rs.rd) `;`
          // rotate
          MapSeqI(size - 1, dt1, dt2, load,
            Drop(1, size - 1, read, dt1, rs.rd),
            TakeAcc(size - 1, 1, dt2, rs.wr), unroll = true)
        ),
      arithexpr.arithmetic.RangeAdd(0, n, 1)))
    })
  }
}