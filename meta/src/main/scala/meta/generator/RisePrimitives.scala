package meta.generator

import fastparse.{Parsed, parse}
import meta.parser._
import meta.parser.rise.Kind
import meta.parser.rise.Kind.AST

object RisePrimitives {
  def main(args: Array[String]): Unit = {
    val sourceDir = args.head // excepts one argument that is the source directory of the rise repo (i.e. 'rise/src')
    val risePath = os.Path(sourceDir) / "rise"
    os.walk.stream(risePath).filter(_.ext == "rise").foreach(path => {  // for each file with the `.rise` extension ...

      val definition = os.read(path)                                    // ... read the file content
      parse(definition, rise.Decl.PrimitiveDeclarations(_)) match {     // ... and parse it
        case failure: Parsed.Failure =>
          println(s"Failed to parse `${failure.extra.input}'")
          println(s"  $failure")
        case Parsed.Success(seq, _) => seq.foreach {                    // ... if successful go over all
            case rise.Decl.AST.PrimitiveDeclaration(rise.Decl.AST.Identifier(name), scalaParams, typeSignature)
                if rise.isWellKindedType(                               // ... well kinded declarations
                      toParamList(definition, scalaParams), typeSignature) =>
              val outputPath = (path / os.up) / s"$name.scala"
              println(s"Generate $outputPath")

              // ... and generate class or object definition
              val generatedDef = generate(name, toParamList(definition, scalaParams), typeSignature)

              import scala.meta._
              val packageName = path.relativeTo(risePath).segments.dropRight(1).foldLeft[Term.Ref](Term.Name("rise")) {
                case (t, name) => Term.Select(t, Term.Name(name))
              }
              // ... combine into the generated source code
              val code = s"""// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
                            |// This file is automatically generated and should not be changed manually //
                            |// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
                            |${q"""
package $packageName {

import rise.core.DSL._
import rise.core.DSL.Type._
import rise.core._
import rise.core.types._
import rise.core.types.DataType._
import arithexpr.arithmetic._

..${generatedDef.stats}

}""".toString()}
                            |""".stripMargin

              os.write.over(outputPath, code)                           // ... and finally write out to disk.
            case rise.Decl.AST.PrimitiveDeclaration(name, _, typeSignature) =>
              println(s"Could not generate code for `$name' as type signature `$typeSignature' is not well kinded.")
          }
      }
    })
  }

  // parse scala parameters (i.e. parameters that are not part of the rise language) into a list of parameters
  def toParamList(definition: String, scalaParams: Option[(Int, Int)]): Option[List[scala.meta.Term.Param]] = {
    import scala.meta._
    scalaParams.map { case (start, end) =>
      s"def foo(${definition.substring(start, end)})".parse[Stat].get match {
        case declDef: Decl.Def => declDef.paramss.head
      }
    }
  }

  // generate either an object (if there are no scala parameters) or a case class (if there are some)
  def generate(name: String,
               params: Option[List[scala.meta.Term.Param]],
               typeSignature: rise.Type.AST): scala.meta.Term.Block =
    params match {
      case None => generateObject(name, typeSignature)
      case Some(params) => generateCaseClass(name, params, typeSignature)
    }


  def generateObject(name: String, typeSignature: rise.Type.AST): scala.meta.Term.Block = {
    import scala.meta._
    val generated = q"""{
      object ${Term.Name{name}} extends Builder {
        private final case class Primitive()(override val t: ExprType = TypePlaceholder)
          extends rise.core.Primitive
        {
          override val name: String = ${Lit.String(name)}
          override def setType(ty: ExprType): Primitive = Primitive()(ty)
          override def primEq(obj: rise.core.Primitive): Boolean = obj.getClass == getClass
          override def typeScheme: ExprType = ${generateTypeScheme(typeSignature)}
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

  def generateCaseClass(name: String,
                        params: List[scala.meta.Term.Param],
                        typeSignature: rise.Type.AST): scala.meta.Term.Block = {
    import scala.meta._

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
        private final case class Primitive(..$params)(override val t: ExprType = TypePlaceholder)
          extends rise.core.Primitive
        {
          override val name: String = ${Lit.String(name)}
          override def setType(ty: ExprType): Primitive = Primitive(..$args)(ty)
          override def typeScheme: ExprType = ${generateTypeScheme(typeSignature)}

          override def primEq(obj: rise.core.Primitive): Boolean = obj match {
            case p: Primitive => ${generateComparisonChain(args)}
            case _ => false
          }
        }

        def unapply(arg: rise.core.Expr): ${if (types.length > 1) {
          t"Option[(..$types)]" // create tuple if there are multiple type parameters
        } else {
          t"Option[..$types]"}
        } = arg match {
          case p: Primitive => Some(..${generateMemberAccesses(args)})
          case _ => None
        }
      }
       """
    generated
  }

  // generate a scala term representing the type scheme, i.e. something like this for map:
  //   impl { (n: Nat) => impl { (s: DataType) => impl { (t: DataType) => (s ->: t) ->: n`.`s ->: n`.`t } } }
  def generateTypeScheme(typeAST: rise.Type.AST): scala.meta.Term = {
    import scala.meta._
    typeAST match {
      case rise.Type.AST.FunType(inT, outT) =>
        q"(${generateTypeScheme(inT)}) ->: (${generateTypeScheme(outT)})"
      case rise.Type.AST.DepFunType(id, kind, t) =>
        q"expl((${Term.Name(id.name)}: ${Type.Name(kindName(kind))}) => ${generateTypeScheme(t)})"
      case rise.Type.AST.ImplicitDepFunType(id, kind, t) =>
        q"impl((${Term.Name(id.name)}: ${Type.Name(kindName(kind))}) => ${generateTypeScheme(t)})"
      case rise.Type.AST.VariadicFunType(_, rise.Type.AST.UnrolledIdentifier(inTs), outT) =>
        // variadic function type with unrolled identifier, e.g.:  n*(*inTs ->) outT
        // generates:  inTs.foldRight(outT){ case (lhsT, rhsT) => lhs ->: rhs }
        // to represent n-many function types: inT0 ->: inT1 ->: ... ->: outT
        q"""${Term.Name(inTs)}.foldRight(${generateTypeScheme(outT)}: ExprType) {
           case (lhsT, rhsT) => lhsT ->: rhsT
        }"""
      case rise.Type.AST.VariadicFunType(rise.Type.AST.Identifier(n), dt, outT) =>
        // variadic function type without an unrolled identifier, e.g:  n*(dt ->) outT
        // generated:  Seq.fill(n)(dt).foldRight(outT){ case (lhsT, rhsT) => lhs ->: rhs }
        // to represent n-many function types: dt -> dt -> ... -> outT
        q"""Seq.fill(${Term.Name(n)})(${generateDataType(dt)}).foldRight(${generateTypeScheme(outT)}: ExprType) {
           case (lhsT, rhsT) => lhsT ->: rhsT
        }"""
      case rise.Type.AST.VariadicDepFunType(n, ids, kind, t) =>
        // variadic dependent function type, e.g.:  n*((ids: kind) ->) t
        // generates:
        //    val ids = Seq.fill(n)(DataTypeIdentifier(freshName("dt")))
        //    ids.foldRight(t){ case (id, t) => DepFunType[DataKind](id, t) }
        // to represent n-many dependent function types: (id0: kind) -> (id1: kind) -> ... -> t
        val (createIds, kindName) = kind match {
          case AST.Data =>
            (q"""DataTypeIdentifier(freshName("dt"))""", Term.Name("DataKind"))
          case AST.Address =>
            (q"""AddressSpaceIdentifier(freshName("a"))""", Term.Name("AddressSpaceKind"))
          case AST.Nat2Nat =>
            (q"""NatToNatIdentifier(freshName("n2n"))""", Term.Name("NatToNatKind"))
          case AST.Nat2Data =>
            (q"""NatToDataIdentifier(freshName("n2d"))""", Term.Name("NatToDataKind"))
          case AST.Nat =>
            (q"""NatIdentifier(freshName("n"))""", Term.Name("NatKind"))
          case AST.Fragment => throw new Exception("No support for Fragment Kind yet")
          case AST.MatrixLayout => throw new Exception("No support for Matrix Layout Kind yet")
        }
        q"""{
            val ${Pat.Var(Term.Name(ids.name))} = Seq.fill(${Term.Name(n.name)})($createIds)
            ${Term.Name(ids.name)}.foldRight(${generateTypeScheme(t)}: ExprType) {
              case (id, t) =>   DepFunType($kindName, id, t)
            }
         }"""
      case _ => generateDataType(typeAST)
    }
  }

  // generate a scala term representing a rise data type
  def generateDataType(typeAST: rise.Type.AST): scala.meta.Term = {
    import scala.meta._
    typeAST match {
      case rise.Type.AST.Identifier(name) =>
        Term.Name(name)
      case rise.Type.AST.UnrolledIdentifier(name) =>
        Term.Name(name)
      case rise.Type.AST.ScalarType(t) =>
        t.parse[Term].get
      case rise.Type.AST.NatType =>
        q"NatType"
      case rise.Type.AST.OpaqueType(name) =>
        q"OpaqueType(${Lit.String(name)})"
      case rise.Type.AST.VectorType(size, elemType) =>
        q"VectorType(${generateNat(size)}, ${generateDataType(elemType)})"
      case rise.Type.AST.IndexType(size) =>
        q"IndexType(${generateNat(size)})"
      case rise.Type.AST.PairType(lhs, rhs) =>
        q"PairType(${generateDataType(lhs)}, ${generateDataType(rhs)})"
      case rise.Type.AST.DepPairType(id, kind, t) => kind match {
        case Kind.AST.Nat =>
          q"Nat `**` ((${Term.Name(id.name)}: Nat) => ${generateDataType(t)})"
        case _ => throw new Exception("DepPair types currently only support Nat Kind")
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
        q"""FragmentType(${generateNat(n)}, ${generateNat(m)}, ${generateNat(k)}, ${generateDataType(elemType)},
                         ${generateFragment(fKind)}, ${generateMatrixLayout(mLayout)})"""
      case rise.Type.AST.ManagedBufferType(dt) =>
        q"ManagedBufferType(${generateDataType(dt)})"
      case rise.Type.AST.FunType(_, _) | rise.Type.AST.DepFunType(_, _, _) |
           rise.Type.AST.ImplicitDepFunType(_, _, _) | rise.Type.AST.VariadicFunType(_, _, _) |
           rise.Type.AST.VariadicDepFunType(_, _, _, _) =>
        throw new Exception("This should not happen, there are not data types")
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
      case AST.Fragment => "Fragment"
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
        q"Fragment.Accumulator"
      case rise.Type.Fragment.AST.A =>
        q"Fragment.AMatrix"
      case rise.Type.Fragment.AST.B =>
        q"Fragment.BMatrix"
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
