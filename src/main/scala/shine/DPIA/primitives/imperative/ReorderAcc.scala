package shine.DPIA.primitives.imperative

import shine.DPIA.Phrases._
import shine.DPIA.Types.DataType._
import shine.DPIA.Types._
import shine.DPIA._
import shine.macros.Primitive.accPrimitive

@accPrimitive
final case class ReorderAcc(n: Nat,
                            dt: DataType,
                            idxF: NatToNat,
                            array: Phrase[AccType]
                           ) extends AccPrimitive {
  array :: accT(n`.`dt)
  override val t: AccType = accT(n`.`dt)
}
