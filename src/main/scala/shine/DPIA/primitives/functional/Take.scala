package shine.DPIA.primitives.functional

import shine.DPIA.Compilation.TranslationContext
import shine.DPIA.Compilation.TranslationToImperative._
import shine.DPIA.DSL._
import shine.DPIA.Phrases._
import shine.DPIA.Types.DataType._
import shine.DPIA.Types._
import shine.DPIA._
import shine.macros.Primitive.expPrimitive

// this takes n many elements from an array of n + m elements
@expPrimitive
final case class Take(n: Nat,
                      m: Nat,
                      dt: DataType,
                      array: Phrase[ExpType]
                     ) extends ExpPrimitive with ContinuationTranslatable {
  array :: expT((n + m)`.`dt, read)
  override val t: ExpType = expT(n`.`dt, read)

  def continuationTranslation(C: Phrase[->:[ExpType, CommType]])
                             (implicit context: TranslationContext): Phrase[CommType] =
    con(array)(λ(expT((n + m)`.`dt, read))(x =>
      C(Take(n, m, dt, x))))
}
