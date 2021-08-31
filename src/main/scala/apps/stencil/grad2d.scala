package apps.stencil

import rise.core.DSL.HighLevelConstructs.{padClamp2D, scatterShift, slide2D}
import rise.core.DSL.Type._
import rise.core.DSL._
import rise.core.Expr
import rise.core.primitives._
import rise.core.types.DataType._
import rise.core.types._

object grad2d {
  private val grad = foreignFun("grad",
    Seq("top", "bottom", "left", "right", "center"),
    """return center + 1.0f/sqrt(0.0001f +
      | (center-top)*(center-top) +
      | (center-bottom)*(center-bottom) +
      | (center-right)*(center-right) +
      | (center-left)*(center-left));""".stripMargin,
    f32 ->: f32 ->: f32 ->: f32 ->: f32 ->: f32)

  val grad2dHighLevel: ToBeTyped[Expr] = depFun((n: Nat, m: Nat) => fun(
    (n `.` m `.` f32) ->: (n `.` m `.` f32)
  )(input => input |>
    slide2D(3, 1) >> map(map(fun { nbh =>
      def at(i: Int, j: Int): ToBeTyped[Expr] =
        nbh `@` lidx(i, 3) `@` lidx(j, 3)
      // vonNeumann 5pt
      val top = at(0, 1)
      val bottom = at(2, 1)
      val left = at(1, 0)
      val right = at(1, 2)
      val center = at(1, 1)

      grad(top)(bottom)(left)(right)(center)
    })) >> padClamp2D(1, 1) >> scatterShift(1) >> map(scatterShift(1))
    // TODO: map(padEmpty(2)) >> padEmpty(2) instead ?
  ))
}