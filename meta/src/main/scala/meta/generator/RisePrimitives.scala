package meta.generator

import fastparse.{Parsed, parse}
import meta.parser._
import meta.parser.rise.Kind

object RisePrimitives {
  def main(args: Array[String]): Unit = {
    val sourceDir = args.head
    val risePath = os.Path(sourceDir) / "rise"
    os.walk.stream(risePath).filter(_.ext == "rise").foreach(path => {

      val definition = os.read(path)
      parse(definition, rise.Decl.PrimitiveDeclarations(_)) match {
        case failure: Parsed.Failure =>
          println(s"Failed to parse `${failure.extra.input}'")
          println(s"  $failure")
        case Parsed.Success(seq, _) =>
          seq.foreach {
            case rise.Decl.AST.PrimitiveDeclaration(rise.Decl.AST.Identifier(name), scalaParams, typeSignature)
                if rise.isWellKindedType(typeSignature) =>
              val outputPath = (path / os.up) / s"$name.scala"
              println(s"Generate $outputPath")

              val generatedDef = scalaParams match {
                case None =>
                  generateObject(name, typeSignature)
                case Some((start, end)) =>
                  generateCaseClass(name, definition.substring(start, end), typeSignature)
              }

              import scala.meta._
              val packageName = path.relativeTo(risePath).segments.dropRight(1).foldLeft[Term.Ref](Term.Name("rise")) {
                case (t, name) => Term.Select(t, Term.Name(name))
              }
              val code = s"""// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
                            |// This file is automatically generated and should not be changed manually //
                            |// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
                            |${q"""
package $packageName {

import rise.core.DSL._
import rise.core.DSL.Type._
import rise.core._
import rise.core.types._
import arithexpr.arithmetic._

..${generatedDef.stats}

}""".toString()}
                            |""".stripMargin

              os.write.over(outputPath, code)
            case rise.Decl.AST.PrimitiveDeclaration(name, _, typeSignature) =>
              println(s"Could not generate code for `$name' as type signature `$typeSignature' is not well kinded.")
          }
      }
    })
  }

  def generateObject(name: String, typeSignature: rise.Type.AST): scala.meta.Term.Block = {
    import scala.meta._
    val generated = q"""{
      object ${Term.Name{name}} extends Builder {
        private final case class Primitive()(override val t: Type = TypePlaceholder)
          extends rise.core.Primitive
        {
          override val name: String = ${Lit.String(name)}
          override def setType(ty: Type): Primitive = Primitive()(ty)
          override def primEq(obj: rise.core.Primitive): Boolean = obj.getClass == getClass
          override def typeScheme: Type = ${generateTypeScheme(typeSignature)}
        }

        override def toString: String = ${Lit.String(name)}
        override def primitive: rise.core.Primitive = Primitive()()
        override def apply: ToBeTyped[rise.core.Primitive] = toBeTyped(Primitive()())
        override def unapply(arg: Expr): Boolean = arg match {
          case _: Primitive => true
          case _ => false
        }
      }
      }"""
    generated
  }

  def generateCaseClass(name: String, paramsString: String, typeSignature: rise.Type.AST): scala.meta.Term.Block = {
    import scala.meta._

    val params = s"def foo($paramsString)".parse[Stat].get match {
      case declDef: Decl.Def => declDef.paramss.head
    }
    val args: List[Term.Name] = params.map(p => Term.Name(p.name.value))
    val types: List[Type] = params.map(p => p.decltpe.get)

    val generated =
      q"""
      final case class ${Type.Name(name)}(..$params) extends Builder {
        override def toString: String = ${Lit.String(name)}
        override def primitive: rise.core.Primitive = ${Term.Name(name)}.Primitive(..$args)()
        override def apply: ToBeTyped[rise.core.Primitive] = toBeTyped(${Term.Name(name)}.Primitive(..$args)())

        override def unapply(arg: Expr): Boolean = arg match {
          case _: Primitive => true
          case _ => false
        }
      }

      object ${Term.Name(name)} {
        private final case class Primitive(..$params)(override val t: Type = TypePlaceholder)
          extends rise.core.Primitive
        {
          override val name: String = ${Lit.String(name)}
          override def setType(ty: Type): Primitive = Primitive(..$args)(ty)
          override def typeScheme: Type = ${generateTypeScheme(typeSignature)}

          override def primEq(obj: rise.core.Primitive): Boolean = obj match {
            case p: Primitive => ${generateComparisonChain(args)}
            case _ => false
          }
        }

        def unapply(arg: rise.core.Expr): Option[..$types] = arg match {
          case p: Primitive =>
            Some(..${generateMemberAccesses(args)})
          case _ => None
        }
      }
       """
    generated
  }

  def generateTypeScheme(typeAST: rise.Type.AST): scala.meta.Term = {
    import scala.meta._
    typeAST match {
      case rise.Type.AST.FunType(inT, outT) =>
        q"(${generateTypeScheme(inT)}) ->: (${generateTypeScheme(outT)})"
      case rise.Type.AST.DepFunType(id, kind, t) =>
        q"expl((${Term.Name(id.name)}: ${Type.Name(kindName(kind))}) => ${generateTypeScheme(t)})"
      case rise.Type.AST.ImplicitDepFunType(id, kind, t) =>
        q"impl((${Term.Name(id.name)}: ${Type.Name(kindName(kind))}) => ${generateTypeScheme(t)})"
      case _ => generateDataType(typeAST)
    }
  }

  def generateDataType(typeAST: rise.Type.AST): scala.meta.Term = {
    import scala.meta._
    typeAST match {
      case rise.Type.AST.Identifier(name) =>
        Term.Name(name)
      case rise.Type.AST.ScalarType(t) =>
        t.parse[Term].get
      case rise.Type.AST.NatType =>
        q"NatType"
      case rise.Type.AST.OpaqueType(name) =>
        q"OpaqueType($name)"
      case rise.Type.AST.VectorType(size, elemType) =>
        q"VectorType(${generateNat(size)}, ${generateDataType(elemType)})"
      case rise.Type.AST.IndexType(size) =>
        q"IndexType(${generateNat(size)})"
      case rise.Type.AST.PairType(lhs, rhs) =>
        q"PairType(${generateDataType(lhs)}, ${generateDataType(rhs)})"
      case rise.Type.AST.DepPairType(id, kind, t) => kind match {
        case Kind.AST.Nat =>
          q"Nat `**` ((${Term.Name(id.name)}: Nat) => ${generateDataType(t)})"
        case _ => ???
      }
      case rise.Type.AST.NatToDataApply(f, n) =>
        q"NatToDataApply(${generateDataType(f)}, ${generateNat(n)})"
      case rise.Type.AST.NatToDataLambda(id, t) =>
        q"n2dtFun((${Term.Name(id.name)}: NatIdentifier) => ${generateDataType(t)})"
      case rise.Type.AST.ArrayType(size, elemType) =>
        q"ArrayType(${generateNat(size)}, ${generateDataType(elemType)})"
      case rise.Type.AST.DepArrayType(size, fdt) =>
        q"DepArrayType(${generateNat(size)}, ${generateDataType(fdt)})"
      case rise.Type.AST.FragmentType(n, m, k, elemType, fKind, mLayout) =>
        q"FragmentType(${generateNat(n)}, ${generateNat(m)}, ${generateNat(k)}, ${generateDataType(elemType)}, ${generateFragment(fKind)}, ${generateMatrixLayout(mLayout)})"
      case rise.Type.AST.ManagedBufferType(dt) =>
        q"ManagedBufferType(${generateDataType(dt)})"
      case rise.Type.AST.FunType(_, _) | rise.Type.AST.DepFunType(_, _, _) |
           rise.Type.AST.ImplicitDepFunType(_, _, _) => ???
    }
  }

  def kindName(kind: Kind.AST): String = {
    import meta.parser.rise.Kind.AST
    kind match {
      case AST.Data =>  "DataType"
      case AST.Address => "AddressSpace"
      case AST.Nat2Nat => "NatToNat"
      case AST.Nat2Data => "NatToData"
      case AST.Nat => "Nat"
      case AST.Fragment => "FragmentKind"
      case AST.MatrixLayout => "MatrixLayout"
    }
  }

  def generateNat(n: Nat.AST): scala.meta.Term = {
    import scala.meta._
    n match {
      case Nat.AST.Identifier(id) =>
        Term.Name(id)
      case Nat.AST.Number(n) =>
        n.parse[Term].get
      case Nat.AST.BinaryOp(lhs, "^", rhs) =>
        q"${generateNat(lhs)}.pow(${generateNat(rhs)})"
      case Nat.AST.BinaryOp(lhs, op, rhs) =>
        q"${generateNat(lhs)} ${Term.Name(op)} ${generateNat(rhs)}"
      case Nat.AST.TernaryOp(cond, thenN, elseN) =>
        val operator: Term = cond.op match {
          case "<" => q"Operator.<"
          case ">" => q"Operator.>"
        }
        q"""
           IfThenElse(
              arithPredicate(${generateNat(cond.lhs)}, ${generateNat(cond.rhs)}, $operator),
              ${generateNat(thenN)},
              ${generateNat(elseN)})
         """
      case Nat.AST.Nat2NatApply(f, n) =>
        q"${generateDataType(f)}(${generateNat(n)})"
      case Nat.AST.Sum(id, from, upTo, body) =>
        q"""BigSum(
              from = ${generateNat(from)},
              upTo = ${generateNat(upTo)},
              (${Term.Name(id.name)}: Nat) => ${generateNat(body)})
         """
    }
  }

  def generateFragment(fragmentAST: rise.Type.Fragment.AST): scala.meta.Term = {
    import scala.meta._
    fragmentAST match {
      case rise.Type.Fragment.AST.Identifier(name) =>
        Term.Name(name)
      case rise.Type.Fragment.AST.ACC =>
        q"FragmentKind.Accumulator"
      case rise.Type.Fragment.AST.A =>
        q"FragmentKind.AMatrix"
      case rise.Type.Fragment.AST.B =>
        q"FragmentKind.BMatrix"
    }
  }

  def generateMatrixLayout(matrixLayoutAST: rise.Type.MatrixLayout.AST): scala.meta.Term = {
    import scala.meta._
    matrixLayoutAST match {
      case rise.Type.MatrixLayout.AST.Identifier(name) =>
        Term.Name(name)
      case rise.Type.MatrixLayout.AST.ROW_MAJOR =>
        q"MatrixLayout.Row_Major"
      case rise.Type.MatrixLayout.AST.COL_MAJOR =>
        q"MatrixLayout.Col_Major"
      case rise.Type.MatrixLayout.AST.NONE =>
        q"MatrixLayout.None"
    }
  }

  def generateComparisonChain(args: List[scala.meta.Term.Name]): scala.meta.Term = {
    import scala.meta._
    args match {
      case List() => q"true"
      case head :: tail =>
        q"(p.$head == $head) && ${generateComparisonChain(tail)}"
    }
  }

  def generateMemberAccesses(args: List[scala.meta.Term.Name]): List[scala.meta.Term] = {
    import scala.meta._
    args match {
      case List() => List(q"()")
      case List(arg) => List(q"p.$arg")
      case head :: tail =>
        q"p.$head" :: generateMemberAccesses(tail)
    }
  }
}
