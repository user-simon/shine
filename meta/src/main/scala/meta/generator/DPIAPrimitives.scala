package meta.generator

import fastparse.{Parsed, parse}
import meta.parser.DPIA.Decl.KindOrType
import meta.parser._
import scala.meta._

object DPIAPrimitives {
  def main(args: Array[String]): Unit = {
    val sourceDir = args.head // excepts one argument that is the source directory of the rise repo (i.e. 'rise/src')
    val shinePath = os.Path(sourceDir) / "shine"
    os.walk.stream(shinePath).filter(_.ext == "dpia").foreach(path => { // for each file with the `.dpia` extension ...

      import DPIA.Decl.AST._

    val definition = os.read(path)                                      // ... read the file content
      parse(definition, DPIA.Decl.PrimitiveDeclarations(_)) match {     // ... and parse it
        case failure: Parsed.Failure =>
          println(s"Failed to parse `${failure.extra.input}'")
          println(s"  $failure")
        case Parsed.Success(seq, _) => seq.foreach {                    // ... if successful go over all
            case PrimitiveDeclaration(Identifier(originalName), scalaParams, params, returnType)
              if DPIA.isWellKindedDefinition(                           // ... well kinded declarations
                toParamList(definition, scalaParams), params, returnType) =>
              val name = originalName.capitalize

              val outputPath = (path / os.up) / s"$name.scala"
              println(s"Generate $outputPath")

              val packageName = path.relativeTo(shinePath).segments.dropRight(1).foldLeft[Term.Ref](Term.Name("shine")) {
                case (t, name) => Term.Select(t, Term.Name(name))
              }

              // ... and generate a case class definition with some imports
              val code = s"""// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
                            |// This file is automatically generated and should not be changed manually //
                            |// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! //
                            |${q"""
package $packageName {

import arithexpr.arithmetic._
import shine.DPIA.Phrases._
import shine.DPIA.Types.DataType._
import shine.DPIA.Types._
import shine.DPIA._

${generateCaseClass(Type.Name(name), toParamList(definition, scalaParams), params, returnType)}

}""".toString()}
                            |""".stripMargin

              os.write.over(outputPath, code)                           // ... and finally write out to disk.
            case PrimitiveDeclaration(Identifier(name), _, params, returnType) =>
              println(s"Could not generate code for `$name' as parameters " +
                s"`$params' and/or `$returnType' are not well kinded.")
          }
      }
    })
  }

  // parse scala parameters (i.e. parameters that are not part of the DPIA language) into a list of parameters
  def toParamList(definition: String, scalaParams: Option[(Int, Int)]): Option[List[scala.meta.Term.Param]] = {
    scalaParams.map { case (start, end) =>
      s"def foo(${definition.substring(start, end)})".parse[Stat].get match {
        case declDef: Decl.Def => declDef.paramss.head
      }
    }
  }

  def generateCaseClass(name: scala.meta.Type.Name,
                        scalaParams: Option[List[scala.meta.Term.Param]],
                        params: Seq[DPIA.Decl.AST.Param],
                        returnType: DPIA.Type.AST): scala.meta.Defn.Class = {
    // determine the super class and the scale type of the `t` member
    val (scalaReturnType, superClass) = returnType match {
      case DPIA.Type.AST.ExpType(_, _)  => (t"ExpType",   init"ExpPrimitive")
      case DPIA.Type.AST.AccType(_)     => (t"AccType",   init"AccPrimitive")
      case DPIA.Type.AST.CommType       => (t"CommType",  init"CommandPrimitive")
      case _ => throw new Exception(s"Expected `exp', `acc' or `comm' as return type for ${name.value}")
    }
    val generatedParams = generateParams(scalaParams, params)
    q"""
      final case class $name(...$generatedParams) extends $superClass {
        {
          ..${generateTypeChecks(params).stats}
        }

        ..${if (scalaReturnType != t"CommType") {
            List(q"override val t: $scalaReturnType = ${generateTerm(returnType)}")
          } else List() }

        ${generateVisitAndRebuild(name, generatedParams)}

        ..${if (scalaParams.nonEmpty && generatedParams.last.size > 1) {
            // generate a unwrap function, that returns the second parameter list as a tuple
              List(generateUnwrap(generatedParams.last))
            } else List() }
      }
    """
  }

  // generates lists of scala parameters to the generated case class
  // if there are scala params there will be two parameter list, otherwise there will be one
  def generateParams(scalaParams: Option[List[scala.meta.Term.Param]],
                     params: Seq[DPIA.Decl.AST.Param]): List[List[scala.meta.Term.Param]] = scalaParams match {
    case Some(scalaParamList) =>
      List(scalaParamList) ++ List(params.map(generateParam).toList)
    case None =>
      List(params.map(generateParam).toList)
  }

  def generateParam(param: DPIA.Decl.AST.Param): scala.meta.Term.Param =
    param"""val ${Term.Name(param.id.name)}: ${                           // generates something like
      param.ty match {
        case KindOrType.Kind(kindAST) => generateType(kindAST)          // e.g. val dt: DataType
        case KindOrType.Type(DPIA.Type.AST.VariadicType(_, typeAST)) =>
          t"Seq[Phrase[${generatePhraseType(typeAST)}]]"                // e.g. val args: Seq[Phrase[ExpType]]
        case KindOrType.Type(typeAST) =>
          t"Phrase[${generatePhraseType(typeAST)}]"                     // e.g. val input: Phrase[ExpType]
      }
    }"""

  // generate the Scala type for representing DPIA/Rise types of different kinds
  def generateType(kindAST: DPIA.Kind.AST): scala.meta.Type = kindAST match {
    case DPIA.Kind.AST.RiseKind(riseKind) => riseKind match {
        case rise.Kind.AST.Data =>          Type.Name("DataType")
        case rise.Kind.AST.Address =>       Type.Name("AddressSpace")
        case rise.Kind.AST.Nat2Nat =>       Type.Name("NatToNat")
        case rise.Kind.AST.Nat2Data =>      Type.Name("NatToData")
        case rise.Kind.AST.Nat =>           Type.Name("Nat")
        case rise.Kind.AST.Fragment =>      Type.Name("FragmentKind")
        case rise.Kind.AST.MatrixLayout =>  Type.Name("MatrixLayout")
     }
    case DPIA.Kind.AST.Access =>            Type.Name("AccessType")
    case DPIA.Kind.AST.VariadicKind(_, kind) => t"Seq[${generateType(kind)}]"
  }

  // generate Scala type for different phrase types
  def generatePhraseType(typeAST: DPIA.Type.AST): scala.meta.Type = typeAST match {
    case DPIA.Type.AST.ExpType(_, _)          => t"ExpType"
    case DPIA.Type.AST.AccType(_)             => t"AccType"
    case DPIA.Type.AST.CommType               => t"CommType"
    case DPIA.Type.AST.PairType(lhs, rhs) => t"PhrasePairType[${generatePhraseType(lhs)}, ${generatePhraseType(rhs)}]"
    case DPIA.Type.AST.FunType(inT, outT) => t"FunType[${generatePhraseType(inT)}, ${generatePhraseType(outT)}]"
    case DPIA.Type.AST.DepFunType(id, kind, t) => t"DepFunType[${generateKindType(kind)}, ${generatePhraseType(t)}]"
    case DPIA.Type.AST.Identifier(name) => Type.Name(name)
    case DPIA.Type.AST.VariadicType(_, _) => throw new Exception("Can not generate Phrase Type for Variadic Type")
  }

  // generate Scala type for representing the DPIA/rise kinds themselves
  def generateKindType(kindAST: DPIA.Kind.AST): scala.meta.Type = kindAST match {
    case DPIA.Kind.AST.RiseKind(riseKind) => riseKind match {
        case rise.Kind.AST.Data =>      Type.Name("DataKind")
        case rise.Kind.AST.Address =>   Type.Name("AddressSpaceKind")
        case rise.Kind.AST.Nat2Nat =>   Type.Name("NatToNatKind")
        case rise.Kind.AST.Nat2Data =>  Type.Name("NatToDataKind")
        case rise.Kind.AST.Nat =>       Type.Name("NatKind")
        case rise.Kind.AST.Fragment => throw new Exception("Can not generate Kind for Fragment")
        case rise.Kind.AST.MatrixLayout => throw new Exception("Can not generate Kind for Matrix Layout")
      }
    case DPIA.Kind.AST.Access =>        Type.Name("AccessKind")
    case DPIA.Kind.AST.VariadicKind(_, _) => throw new Exception("Can not generate Kind for Variadic Kind")
  }

  // generate type checks in the body of the generated case classes, e.g. for map:
  //    f :: FunType(expT(dt1, a), expT(dt2, a))
  //    array :: expT(ArrayType(n, dt1), a)
  def generateTypeChecks(params: Seq[DPIA.Decl.AST.Param]): scala.meta.Term.Block =
    q"""{
      ..${params.
            filter(param => param.ty.isInstanceOf[KindOrType.Type]). // only check types for params with phrase types
            map(param => param.ty match {
              // special treatment for params with variadic type
              case KindOrType.Type(DPIA.Type.AST.VariadicType(n, typeAST)) =>
                generateVariadicTypeCheck(param, n, typeAST)
              // for params with dependent function type we generate something like this, e.g. for iterate:
              //    f :: ({
              //        val l = f.t.x
              //        DepFunType[NatKind, PhraseType](l,
              //            FunType(expT(ArrayType(l * n, dt), read), expT(ArrayType(l, dt), write)))
              //      })
              case KindOrType.Type(typeAST@DPIA.Type.AST.DepFunType(id, _, _)) =>
                q"""${Term.Name(param.id.name)} :: {
                   val ${Pat.Var(name = Term.Name(id.name))} = ${Term.Name(param.id.name)}.t.x
                   ${generateTerm(typeAST)}
                 }"""
              // for all other parameters we generate the simple form: e :: t
              case KindOrType.Type(typeAST) =>
                q"${Term.Name(param.id.name)} :: ${generateTerm(typeAST)}"
              case KindOrType.Kind(_) => throw new Exception("Generation of type checks not necessary for kinds")
            }).toList}
    }"""

  def generateVariadicTypeCheck(param: DPIA.Decl.AST.Param,
                                n: DPIA.Type.AST.Identifier,
                                typeAST: DPIA.Type.AST): scala.meta.Term =
    // we check if there are unrolled ids (i.e. *id) in the expression
    getUnrolledIds(typeAST) match {
      case Seq() => // ... if there are none, then we can generate the simple form with an foreach
        q"${Term.Name(param.id.name)}.foreach(_ :: ${generateTerm(typeAST)})"
      case unrolledIds => // ... if there are, then we have to zip the unrolled ids with the param
        // we generate something like this, e.g. for foreign function call:
        //    args.zip(inTs).foreach({ case (args, inTs) => args :: expT(inTs, read) })

        // generate a chain of nested zip calls (in case there are multiple unrolled ids)
        val zips = unrolledIds.foldLeft[Term](Term.Name(param.id.name)) {
          case (term, id) => q"$term.zip(${Term.Name(id.name)})"
        }
        val p = Term.Name(param.id.name)
        // ... generate the case pattern reusing the variable names of the param and unrolled ids
        //     (this avoids the need to rename the variables in the `typeAST`)
        val pattern = unrolledIds.foldRight[Pat](Pat.Var(p)) {
          case (id, pattern) => p"($pattern, ${Pat.Var(Term.Name(id.name))})"
        }
        // ... putting everything together with a foreach into the final form
        q"""$zips.foreach {
          case ($pattern) => $p :: ${generateTerm(typeAST)}
        }"""
    }

  def getUnrolledIds(typeAST: DPIA.Type.AST): Seq[rise.Type.AST.UnrolledIdentifier] = typeAST match {
    case DPIA.Type.AST.ExpType(dataType, _) => getUnrolledIds(dataType)
    case DPIA.Type.AST.AccType(dataType) => getUnrolledIds(dataType)
    case DPIA.Type.AST.CommType => Seq()
    case DPIA.Type.AST.PairType(lhs, rhs) => getUnrolledIds(lhs) concat getUnrolledIds(rhs)
    case DPIA.Type.AST.FunType(inT, outT) => getUnrolledIds(inT) concat getUnrolledIds(outT)
    case DPIA.Type.AST.DepFunType(_, _, t) => getUnrolledIds(t)
    case DPIA.Type.AST.Identifier(_) => Seq()
    case DPIA.Type.AST.VariadicType(_, _) => throw new Exception("This function should not be called on a variadic type")
  }

  def getUnrolledIds(typeAST: rise.Type.AST): Seq[rise.Type.AST.UnrolledIdentifier] = typeAST match {
    case rise.Type.AST.Identifier(_) => Seq()
    case id@rise.Type.AST.UnrolledIdentifier(_) => Seq(id)
    case rise.Type.AST.FunType(inT, outT) => getUnrolledIds(inT) concat getUnrolledIds(outT)
    case rise.Type.AST.DepFunType(_, _, t) => getUnrolledIds(t)
    case rise.Type.AST.ImplicitDepFunType(_, _, t) => getUnrolledIds(t)
    case rise.Type.AST.ScalarType(_) => Seq()
    case rise.Type.AST.NatType => Seq()
    case rise.Type.AST.OpaqueType(_) => Seq()
    case rise.Type.AST.VectorType(_, elemType) => getUnrolledIds(elemType)
    case rise.Type.AST.IndexType(_) => Seq()
    case rise.Type.AST.PairType(lhs, rhs) => getUnrolledIds(lhs) concat getUnrolledIds(rhs)
    case rise.Type.AST.DepPairType(_, _, t) => getUnrolledIds(t)
    case rise.Type.AST.NatToDataApply(_, _) => Seq()
    case rise.Type.AST.NatToDataLambda(_, t) => getUnrolledIds(t)
    case rise.Type.AST.ArrayType(_, elemType) => getUnrolledIds(elemType)
    case rise.Type.AST.DepArrayType(_, _) => Seq()
    case rise.Type.AST.FragmentType(_, _, _, dt, _, _) => getUnrolledIds(dt)
    case rise.Type.AST.ManagedBufferType(t) => getUnrolledIds(t)
    case rise.Type.AST.VariadicFunType(_, _, _) | rise.Type.AST.VariadicDepFunType(_, _, _, _) =>
      throw new Exception("This function should not be called on a variadic type")
  }

  // generate Scala term that represents the given DPIA/rise type
  def generateTerm(typeAST: DPIA.Type.AST): scala.meta.Term = typeAST match {
    case DPIA.Type.AST.Identifier(name) => Term.Name(name)
    case DPIA.Type.AST.ExpType(dataType, access) =>
      q"expT(${RisePrimitives.generateDataType(dataType)}, ${generateTerm(access)})"
    case DPIA.Type.AST.AccType(dataType) =>
      q"accT(${RisePrimitives.generateDataType(dataType)})"
    case DPIA.Type.AST.CommType =>
      q"comm"
    case DPIA.Type.AST.PairType(lhs, rhs) =>
      q"PhrasePairType(${generateTerm(lhs)}, ${generateTerm(rhs)})"
    case DPIA.Type.AST.FunType(inT, outT) =>
      q"FunType(${generateTerm(inT)}, ${generateTerm(outT)})"
    case DPIA.Type.AST.DepFunType(id, kind, t) =>
      q"DepFunType[${generateKindType(kind)}, PhraseType](${Term.Name(id.name)}, ${generateTerm(t)})"
    case DPIA.Type.AST.VariadicType(_, _) => throw new Exception("Can not generate Term for Variadic Type")
  }

  def generateTerm(accessAST: DPIA.Type.Access.AST): scala.meta.Term = accessAST match {
    case DPIA.Type.Access.AST.Identifier(name) => Term.Name(name)
    case DPIA.Type.Access.AST.Read => Term.Name("read")
    case DPIA.Type.Access.AST.Write =>Term.Name("write")
  }

  def generateVisitAndRebuild(name: scala.meta.Type.Name,
                              paramLists: List[List[scala.meta.Term.Param]]): scala.meta.Defn.Def = {
    // little pattern matching helper that ignores if a type name is written with a package prefix
    object TypeIs {
      def unapply(ty: Type): Option[String] = ty match {
        case Type.Name(name) => Some(name)
        case Type.Select(_, Type.Name(name)) => Some(name)
        case _ => None
      }
    }

    // inject a call to the visitor depending on the param's generated scala type
    def injectVisitCall(param: Term.Param): Term = param.decltpe match {
      case Some(ty) => ty match {
        // the different kinds ...
        case TypeIs("Nat") | TypeIs("NatIdentifier") =>
          q"v.nat(${Term.Name(param.name.value)})"
        case TypeIs("DataType") | TypeIs("ScalarType") | TypeIs("BasicType") =>
          q"v.data(${Term.Name(param.name.value)})"
        case TypeIs("NatToNat") =>
          q"v.natToNat(${Term.Name(param.name.value)})"
        case TypeIs("NatToData") =>
          q"v.natToData(${Term.Name(param.name.value)})"
        case TypeIs("AccessType") =>
          q"v.access(${Term.Name(param.name.value)})"
        case TypeIs("AddressSpace") =>
          q"v.addressSpace(${Term.Name(param.name.value)})"
        case Type.Apply(Type.Name("Vector"), List(TypeIs("DataType"))) // Vector[DataType]
             |  Type.Apply(Type.Name("Seq"), List(TypeIs("DataType"))) => // Seq[DataType]
          q"${Term.Name(param.name.value)}.map(v.data)"

        // ... phrases ...
        case t"Phrase[$_]" => q"VisitAndRebuild(${Term.Name(param.name.value)}, v)"
        case t"Seq[Phrase[$_]]" => q"${Term.Name(param.name.value)}.map(VisitAndRebuild(_, v))"

        // ... and finally special cases, that maybe should be eliminated in the future
        case TypeIs("LocalSize") | TypeIs("GlobalSize") =>
          q"${Term.Name(param.name.value)}.visitAndRebuild(v)"
        case t"Map[Identifier[_ <: PhraseType], $_]" =>
          q"""${Term.Name(param.name.value)}.map{ case (key, value) =>
            VisitAndRebuild(key, v).asInstanceOf[Identifier[_ <: PhraseType]] -> value
          }"""

        case _ =>
          Term.Name(param.name.value)
      }
      case None => throw new Exception(s"Expected type declaration")
    }

    q"""override def visitAndRebuild(v: VisitAndRebuild.Visitor): $name =
       new $name(...${paramLists.map(_.map(injectVisitCall))})
     """
  }

  def generateUnwrap(paramList: List[scala.meta.Term.Param]): scala.meta.Defn.Def = {
    val (types, names) = paramList.map({
      case Term.Param(_, name, Some(typ), _) => (typ, Term.Name(name.value))
    }).unzip
    q"""
      def unwrap: (..$types) = (..$names)
     """
  }
}
