package parser


import OpType.{BinOpType, UnaryOpType}
import parser.ErrorMessage.{AllLinesAreEmpty, EndOfFile, EndOfLine, ExpectedArrowButGotTwoDash, F32DeclaredAsI32, F32DeclaredAsI8, IdentifierBeginsWithAF32Number, IdentifierBeginsWithDigits, IdentifierExpectedNotTypeIdentifier, IdentifierWithNotAllowedSymbol, NOTanBinOperator, NotExpectedToken, NotExpectedTwoBackslash, NumberWithUnknownSymbol, OnlyOneEqualSign, PreAndErrorToken, ThisTokenShouldntBeHereExpectedArrowOrDots, ToShortToBeThisToken, TypeIdentifierExpectedNotIdentifier, UnknownKind, UnknownSymbol, UnknownType, debug}


object RecognizeLexeme{
  val knownCharacters = Set('(', ')', '\\', ':', '-', '+', '*', '/', '%' , '>', '<', '=' ,'!', ',', '.', '[', ']')
  /*
  is the Symbol '(', ')',  '\', ':', '-', '+', '*', '/', '%' , '>', '<', '=' or '!'
  It is not relevant here, that "asdf=3234" is not allowed,
  it is only relevant here, that '=' is a known symbol
   */
  def otherKnownSymbol(c:Char): Boolean = {
    knownCharacters(c) //set.contains(c)
  }
  val binarySymbol= Set('-', '+', '*', '/', '%' , '>', '<', '=')
  val unarySymbol= Set('~', '!')
  val scalarTypes= Set("Bool", "I16","I32","F64","NatTyp")
  val kinds = Set("Data","AddrSpace","Nat")
  /*
  it is only relevant here, that '=' is a known symbol
   */
  def isBinaryOperatorSymbol(c:Char): Boolean = {
    binarySymbol(c) //set.contains(c)
  }
}

//alles muss in ein Try gemappt werden
//Try{ file= openFile(); parse(file);}catch{...}finally{file.close}

/*
this recognizes the Lexeme in the File which represents the right Token
 */
case class RecognizeLexeme(fileReader: FileReader){
  val tokens:List[Token] = lexer()
  type TokenAndPos = (List[Token],Int,Int)
  type TokenList = List[Either[Token,PreAndErrorToken]]

  private def lexer(): List[Token] = {
    val list:List[Token] = lexNamedExprOrTypAnnotatedIdentOrForeignFct(0,0, Nil)
    val newL = list.reverse
    debug("Lexer finished: " + newL, "lexer")
    newL
  }

  private def lexNamedExprOrTypAnnotatedIdentOrForeignFct(oldColumn:Int, oldRow:Int, l:List[Token]):List[Token] =  {
    val arr: Array[String]= fileReader.sourceLines
    var row = oldRow
    var column = oldColumn
    require(row>=0, "row is not allowed to be negative")
    require(column>=0, "column is not allowed to be negative")

    val list= l

    isEnd(fileReader, column, row, arr) match {
      case Left((c, r)) => {
        column = c
        row = r
        //debug("lexing begins at : ( "+ column +" , " + row + " )" )
      }
      case Right(EndOfFile(span)) => throw EndOfFile(span)
      case Right(EndOfLine(span)) => throw AllLinesAreEmpty(span)
      case Right(p) => throw p
    }

    if (arr(column)(row).isLetter) {
      lexIdentifier(column, row) match {
        case (Left(a), r) => {
          a match {
            case Identifier("foreign", span) => return beginForeignFct(column, row, list)._1
            case _ =>
          }
          var newRow = r
          //            val i: Token = a
          skipWhitespaceWhitoutNewLine(column, newRow) match {
            case (c, r) => {
              newRow = r
            }
          }
          arr(column).substring(newRow, newRow + 1) match {
            case ":" => {
              if (arr(column).length >= newRow + 2) {
                arr(column).substring(newRow, newRow + 2) match {
                  case "::" => {
                    return beginTypAnnotatedIdent(column, row, list)._1
                  }
                  case a => throw NotExpectedToken("'::' or '='", a, Span(fileReader,
                    Range(Location(column, newRow),Location(column, newRow+2))))
                }
              } else {
                throw NotExpectedToken("'::' or '='", arr(column).substring(newRow, newRow + 1),
                  Span(fileReader,
                  Range(Location(column, newRow),Location(column, newRow+1))))
              }
            }
            case "=" => {
              if (arr(column).length >= newRow + 2) {
                arr(column).substring(newRow, newRow + 2) match {
                  case "==" => throw NotExpectedToken("'::' or '='", "==", Span(fileReader,
                    Range(Location(column, newRow),Location(column, newRow+2))))
                  case "=>" => throw NotExpectedToken("'::' or '='", "=>", Span(fileReader,
                    Range(Location(column, newRow),Location(column, newRow+2))))
                  case a => {
                    return beginNamedExpr(column, row, list)._1
                  }
                }
              } else {
                throw return beginNamedExpr(column, row, list)._1
              }
            }
            case a => throw NotExpectedToken("'::' or '='", a, Span(fileReader,
              Range(Location(column, newRow),Location(column, newRow+2))))
          }
        }
        case (Right(a),_)=> throw a
      }
    }else{
    throw new IllegalStateException(
      "Here is at the Beginning in line " + column + " a Identifier expected, but here is no Identifier!")
    }
    throw new IllegalStateException("Until Here should nothing come")
  }


  private def lexerForeignFct(oldColumn:Int, oldRow:Int, l:List[Token]):TokenAndPos = {
    val arr: Array[String]= fileReader.sourceLines
    var row = oldRow
    var column = oldColumn
    require(row>=0, "row is not allowed to be negative")
    require(column>=0, "column is not allowed to be negative")
    require(arr.length > column, "array does not have so much columns")
    require(arr(column).length > row, "arr(column) has less than row chars")

    var list= l

    isEnd(fileReader, column, row, arr) match {
      case Left((c, r)) => {
        column = c
        row = r
      }
      case Right(EndOfFile(_)) => throw new RuntimeException("Here occoured a EndOfFile Exeption," +
        " but this should not be able to happen")
      case Right(EndOfLine(span)) => throw new RuntimeException("At position ("
        + span.range.begin.column + "," + span.range.begin.row + " is an expression expected " +
        ", but there is nothing! '" + arr(column) + "'")
      case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
    }

    lexIdentifier(column, row) match {
      case (Left(a), r) => {
        row = r
        a match {
          case Identifier("foreign",sp)=> list=list.::(ForeignKeyword(sp))
          case _ => throw new IllegalStateException("Foreign Keyword expected: "+ a)
        }

      }
      case (Right(a), _) => {
        throw a
      }
    }

    isEnd(fileReader, column, row, arr) match {
      case Left((c, r)) => {
        column = c
        row = r
      }
      case Right(EndOfFile(_)) => throw new RuntimeException("Here occoured a EndOfFile Exeption," +
        " but this should not be able to happen")
      case Right(EndOfLine(span)) => throw new RuntimeException("At position ("
        + span.range.begin.column + "," + span.range.begin.row + " is an expression expected " +
        ", but there is nothing! '" + arr(column) + "'")
      case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
    }

    lexIdentifier(column, row) match {
      case (Left(a), r) => {
        row = r
        list=list.::(a)
      }
      case (Right(a), _) => {
        throw a
      }
    }

    lexLParentheses(column, row) match {
      case Left(a) => {
        row = row+1
        list=list.::(a)
      }
      case Right(a) => {
        throw a
      }
    }
    val res = lexerForeignFctParameterList(column, row,list)
    res
  }
  private def lexerNamedExpr(oldColumn:Int, oldRow:Int, l:List[Token]):TokenAndPos = {
    //debug("lexerNamedExpr: "+ l)
    val arr: Array[String]= fileReader.sourceLines
    var row = oldRow
    var column = oldColumn
    require(row>=0, "row is not allowed to be negative")
    require(column>=0, "column is not allowed to be negative")
    require(arr.length > column, "array does not have so much columns")
    require(arr(column).length > row, "arr(column) has less than row chars")

    var list= l

    isEnd(fileReader, column, row, arr) match {
      case Left((c, r)) => {
        column = c
        row = r
      }
      case Right(EndOfFile(_)) => throw new RuntimeException("Here occoured a EndOfFile Exeption," +
        " but this should not be able to happen")
      case Right(EndOfLine(span)) => throw new RuntimeException("At position ("
        + span.range.begin.column + "," + span.range.begin.row + " is an expression expected " +
        ", but there is nothing! '" + arr(column) + "'")
      case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
    }

    lexIdentifier(column, row) match {
      case (Left(a), r) => {
        row = r
        list=list.::(a)
      }
      case (Right(a), _) => {
        throw a
      }
    }

    skipWhitespaceWhitoutNewLine(column, row) match {
      case (c,r) =>{
        column = c
        row = r
      }
    }

    lexEqualsSign(column, row) match {
      case Left(a) => {
        row = row+1
        list=list.::(a)
      }
      case Right(a) => {
        throw a
      }
    }

    val res = lexerExpression(column, row,list)
    res
  }

  private def lexerTypAnnotatedIdent(oldColumn:Int, oldRow:Int, l:List[Token]):TokenAndPos = {
    //debug("lexerTypAnnotatedIdent: "+ l)
    val arr: Array[String]= fileReader.sourceLines
    var row = oldRow
    var column = oldColumn
    require(row>=0, "row is not allowed to be negative")
    require(column>=0, "column is not allowed to be negative")
    require(arr.length > column, "array does not have so much columns")
    require(arr(column).length > row, "arr(column) has less than row chars")

    //in this list we add all
    var list= l

    //ignore whitespaces
    skipWhitespace(column, row) match {
      case (c,r) =>{
        column = c
        row = r
      }
    }

    lexIdentifier(column, row) match {
      case (Left(a), r) => {
        row = r
        list=list.::(a)
      }
      case (Right(a), _) => {
        throw a
      }
    }

    skipWhitespaceWhitoutNewLine(column, row) match {
      case (c,r) =>{
        column = c
        row = r
      }
    }

    lexDoubleDots(column, row) match {
      case Left(a) => {
        row = row+2
        list=list.::(a)
      }
      case Right(a) => {
        throw a
      }
    }

    (lexerTypAnnotationExpression(column, row,list), column, row)
  }


  private def isEnd(fileReader: FileReader, c: Int, r: Int, arr:Array[String]): Either[(Int, Int), PreAndErrorToken] ={
    //ignore whitespaces
    val (column, row) = skipWhitespace(c, r)
    //are you able to take arr(column)(row)?
    if(arr.length <= column){
      var h:String = "'\n"
      for(x <-arr){
        h = h ++ x ++ "\n"
      }
      h = h++ "'"
      val loc:Location = Location(column, row) //endLocation is equal to startLocation
      Right(ErrorMessage.EndOfFile(new Span(fileReader, loc)))
    }else if(arr(column).length <= row ){
      val loc:Location = Location(column, row) //endLocation is equal to startLocation
      Right(ErrorMessage.EndOfLine(new Span(fileReader, loc)))
    }else{
      Left((column, row))
    }
  }

  /*
      if (RecognizeLexeme.isBinaryOperatorSymbol(arr(column)(row))) {
      lexBinOperator(column, row) match {
        case Left(BinOp(BinOpType.EQ, span)) => {
          row = row + 2
          list = list.::(BinOp(BinOpType.EQ, span))
        }
        case Left(a) => {
          row = row + 1
          //   //debug("\n\n"+ a.toString)
          list = list.::(a)
        }
        case Right(a) => {
          lexDotsOrArrow(column,row) match {
            case Left(arrow) =>{
              row = row+2
              list = list.::(arrow)
            }
            case Right(e) => throw e
          }
        }
      }
    } else {
      //debug("mitte: "+ arr(column)(row))
      arr(column)(row) match {
        case '\\' =>{
          val loc: Location = Location(column, row) //endLocation is equal to startLocation
          list = list.::(Backslash(Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
          row = row + 1
        }
        case '.' => {
          val loc: Location = Location(column, row) //endLocation is equal to startLocation
          list = list.::(Dot(Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
          row = row + 1
        }
        case ':' => {
          val loc: Location = Location(column, row) //endLocation is equal to startLocation
          list = list.::(Colon(Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
          row = row + 1
        }
        case '(' => {
          val loc: Location = Location(column, row) //endLocation is equal to startLocation
          list = list.::(LParentheses(Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
          row = row + 1
        }
        case ')' => {
          val loc: Location = Location(column, row) //endLocation is equal to startLocation
          list = list.::(RParentheses(Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
          row = row + 1
        }
        case '!' => {
          val loc: Location = Location(column, row) //endLocation is equal to startLocation
          list = list.::(UnOp(UnaryOpType.NOT, Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
          row = row + 1

          isEnd(fileReader, column, row, arr) match {
            case Left((c, r)) => {
              column = c
              row = r
            }
            case Right(EndOfFile(_)) => throw new RuntimeException("an Negation needs an Expression after it")
            case Right(EndOfLine(span)) => throw new RuntimeException("At position ("
              + span.range.begin.column + "," + span.range.begin.row + " is an expression expected " +
              ", but there is nothing! '" + arr(column) + "'")
            case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
          }
          return lexerExpression(column, row, list)
        }
        case '~' => { //TODO: Is this symbol as a Neg-Sign ok?
          val loc: Location = Location(column, row) //endLocation is equal to startLocation
          list = list.::(UnOp(UnaryOpType.NEG, Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
          row = row + 1

          isEnd(fileReader, column, row, arr) match {
            case Left((c, r)) => {
              column = c
              row = r
            }
            case Right(EndOfFile(_)) => throw new RuntimeException("an Negation needs an Expression after it")
            case Right(EndOfLine(span)) => throw new RuntimeException("At position ("
              + span.range.begin.column + "," + span.range.begin.row + " is an expression expected " +
              ", but there is nothing! '" + arr(column) + "'")
            case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
          }
          return lexerExpression(column, row, list)
        }
        case a => {
          if (a.isDigit) {
            //more than one step but column keeps the same
            lexNumber(column, row) match {
              case (Left(a), r) => {
                row = r
                list = list.::(a)
              }
              case (_, _) => {
                  typeRecognizingInNoAppExpr(column,row, list, a, arr) match{
                    case Left((c,r,l))=>{
                      column =c
                      row = r
                      list = l
                    }
                    case Right(e) => throw e
              }
            }
          }
        }else{
            if (a.isWhitespace && column+1>=arr.length && arr(column).length-1 <=row) {
              //debug("exit typeRecognizingInNoAppExpr:: "+"column: "+ column + ", row: " + row)
              return (list,column, row)
            }
              typeRecognizingInNoAppExpr(column,row, list, a, arr) match{
                case Left((c,r,l))=>{
                  column =c
                  row = r
                  list = l
                }
                case Right(e) => throw e
              }
          }
        }
      }
    }
   */
  private def lexToken(oldColumn:Int, oldRow:Int, l:List[Token]):Either[(Int, Int, List[Token]),
    PreAndErrorToken] = {
    debug(l.toString() + " ( "+ oldColumn + " , " + oldRow + " )", "lexToken")
    val arr: Array[String] = fileReader.sourceLines
    var row = oldRow
    var column = oldColumn
    require(row >= 0, "row is not allowed to be negative")
    require(column >= 0, "column is not allowed to be negative")

    var list = l
    arr(column)(row) match {
      case '\\' =>{
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        list = list.::(Backslash(Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
        row = row + 1
      }
      case '~' => { //TODO: Is this symbol as a Neg-Sign ok?
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        list = list.::(UnOp(UnaryOpType.NEG, Span(fileReader, Range(loc, Location(loc.column, loc.row + 1)))))
        row = row + 1
      }
      case '!' => {
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        list = list.::(UnOp(UnaryOpType.NOT, Span(fileReader, Range(loc, Location(loc.column, loc.row + 1)))))
        row = row + 1
      }
      case '(' => {
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        list = list.::(LParentheses(Span(fileReader,
          Range(loc, Location(loc.column, loc.row+1)))))
        row = row + 1
      }
      case ')' => {
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        list = list.::(RParentheses(Span(fileReader,
          Range(loc, Location(loc.column, loc.row+1)))))
        row = row + 1
      }
      case '[' => {
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        list = list.::(LBracket(Span(fileReader,
          Range(loc, Location(loc.column, loc.row+1)))))
        row = row + 1
      }
      case ']' => {
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        list = list.::(RBracket(Span(fileReader,
          Range(loc, Location(loc.column, loc.row+1)))))
        row = row + 1
      }
      case ',' => {
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        list = list.::(Comma(Span(fileReader,
          Range(loc, Location(loc.column, loc.row+1)))))
        row = row + 1
      }
      case '.' => {
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        list = list.::(Dot(Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
        row = row + 1
      }
      case ':' => {
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        list = list.::(Colon(Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
        row = row + 1
      }
      case a =>lexBinOperator(column, row) match {
        case Left(BinOp(BinOpType.EQ, span)) => {
          row = row + 2
          list = list.::(BinOp(BinOpType.EQ, span))
        }
        case Left(a) => {
          row = row + 1
          //   //debug("\n\n"+ a.toString)
          list = list.::(a)
        }
        case Right(_) => lexDeporNormalArrow(column, row, arr, "->") match {
          case Left(tok) => {
            row = row + 2
            list = list.::(tok)
          }
          case Right(e) => if (a.isDigit) {
            lexVectorType(column, row) match {
              case (Left(a), r) => {
                row = r
                list = list.::(a)
              }
              case (Right(e), r) => {
                lexNumber(column, row) match {
                  case (Right(e),_)=>return Right(e)
                  case (Left(number),r) => {
                    row = r
                    list = list.::(number)
                  }
                }
              }
            }
          } else if (arr(column).length > row + 3 &&
            arr(column).substring(row, row + 3).equals("Idx")) {
            val beginLoc = Location(column, row)
            val endLoc = Location(column, row + 2)
            val idx = TypeIdentifier("Idx", Span(fileReader, Range(beginLoc, endLoc)))
            row = row + 3
            val lB = lexLBracket(column, row) match {
              case Right(e) => return Right(e)
              case Left(t) => t
            }
            row = row + 1
            skipWhitespaceWhitoutNewLine(column, row) match {
              case (c, r) => {
                column = c
                row = r
              }
            }
            val (nat, r) = lexNatNumber(column, row)
            row = r
            skipWhitespaceWhitoutNewLine(column, row) match {
              case (c, r) => {
                column = c
                row = r
              }
            }
            val rB = lexRBracket(column, row) match {
              case Right(e) => return Right(e)
              case Left(t) => t
            }
            row = row + 1

            list = list.::(idx).::(lB).::(nat).::(rB)
          } else {
            lexScalarType(column, row) match {
              case (Left(a), r) => {
                row = r
                list = list.::(a)
              }
              case (Right(_), _) => lexKind(column, row) match {
                case (Left(a), r) => {
                  row = r
                  list = list.::(a)
                }
                case (Right(e), _) => {
                  //            debug("column: "+ column + ", row: " + row)
                  if (!arr(column)(row).isLetterOrDigit) {
                    if (arr(column)(row).isWhitespace && arr(column).length <= row + 1) {
                      val loc: Location = Location(column, row) //endLocation is equal to startLocation
                      //                debug("column: "+ column + ", row: " + row)
                      return Right(ErrorMessage.EndOfLine(new Span(fileReader, loc)))
                    }
                    if (arr(column)(row).equals('#')) {
                      throw new IllegalStateException(
                        "Every '#' should have been removed in the preLexer. This is an IllegalState of the Lexer.")
                    } else {
                      val loc: Location = Location(column, row) //endLocation is equal to startLocation
                      return Right(ErrorMessage.NotExpectedToken("Some Number or String",
                        arr(column)(row).toString, Span(fileReader, Range(loc, Location(loc.column, loc.row + 1)))))
                    }
                  }
                  lexIdentifier(column, row) match {
                    case (Left(ident), r) => {
                      row = r
                      list = list.::(ident)
                    }
                    case (Right(e), _) => return Right(e)
                  }
                }
              }
            }
          }
        }
      }
    }
    Left((column, row, list))
  }

  private def lexTypAnnotationToken(oldColumn:Int, oldRow:Int, l:List[Token]):Either[(Int, Int, List[Token]),
    PreAndErrorToken] = {
    //debug("lexerTypAnnotationExpression: "+ l + " ( "+ oldColumn + " , " + oldRow + " )")
    val arr: Array[String] = fileReader.sourceLines
    var row = oldRow
    var column = oldColumn
    require(row >= 0, "row is not allowed to be negative")
    require(column >= 0, "column is not allowed to be negative")
    skipWhitespaceWhitoutNewLine(column, row) match {
      case (c, r) => {
        column = c
        row = r
      }
    }
    if (arr(column).length <= row) {
      val loc: Location = Location(column, row)
      return Right(ErrorMessage.EndOfLine(new Span(fileReader, loc)))
    }
    lexToken(column, row, l) match {
      case Left((column, row, list)) => Left((column, row, list))
      case Right(e) => Right(e)
    }
  }

  def differentCasesSymbolInTypeRecognizing(c:Int, r:Int, li:List[Token], symbol: Char, arr:Array[String]):Either[(Int, Int, List[Token]),
    PreAndErrorToken] = {
    var column = c
    var row = r
    var list = li
    if (symbol.isDigit) {
      lexVectorType(column,row) match {
        case (Left(a), r) => {
          row = r
          list = list.::(a)
        }
        case (Right(e), r) => {
          lexNatNumber(column, row) match {
            case (nat, r) => {
              row = r
              skipWhitespaceWhitoutNewLine(column, row) match {
                case (c1, r1) => {
                  column = c1
                  row = r1
                }
              }
              if(arr(column).length <= row){
                val loc = Location(column, row)
                return Right(ErrorMessage.EndOfLine(new Span(fileReader, loc)))
              }
              lexDot(column, row) match {
                case Left(dot) => {
                  row = row + 1
                  skipWhitespaceWhitoutNewLine(column, row) match {
                    case (c, r) => {
                      column = c
                      row = r
                    }
                  }
                  list = list.::(nat).::(dot)
                }
                case Right(e) => return Right(e)
              }
            }
          }
        }
      }
    } else if (arr(column).length > row + 2 &&
      arr(column).substring(row, row + 2).equals("=>")) {
      val beginLoc = Location(column, row)
      val endLoc = Location(column, row + 1)
      val depArrow = DepArrow(Span(fileReader, Range(beginLoc, endLoc)))
      row = row + 2
      list = list.::(depArrow)
    } else if (arr(column).length > row + 3 &&
      arr(column).substring(row, row + 3).equals("Idx")) {
      val beginLoc = Location(column, row)
      val endLoc = Location(column, row + 2)
      val idx = TypeIdentifier("Idx", Span(fileReader, Range(beginLoc, endLoc)))
      row = row + 3
      val lB = lexLBracket(column, row) match {
        case Right(e) => return Right(e)
        case Left(t) => t
      }
      row = row + 1
      skipWhitespaceWhitoutNewLine(column, row) match {
        case (c, r) => {
          column = c
          row = r
        }
      }
      val (nat, r) = lexNatNumber(column, row)
      row = r
      skipWhitespaceWhitoutNewLine(column, row) match {
        case (c, r) => {
          column = c
          row = r
        }
      }
      val rB = lexRBracket(column, row) match {
        case Right(e) => return Right(e)
        case Left(t) => t
      }
      row = row + 1

      list = list.::(idx).::(lB).::(nat).::(rB)
    } else {
      lexScalarType(column, row) match {
        case (Left(a), r) => {
          row = r
          list = list.::(a)
        }
        case (Right(_), _) => lexKind(column, row) match {
          case (Left(a), r) => {
            row = r
            skipWhitespaceWhitoutNewLine(column, row) match {
              case (c, r) => {
                column = c
                row = r
              }
            }
            lexDepArrow(column, row) match {
              case Left(b) => {
                row = row + 2
                list = list.::(a).::(b)
              }
              case Right(e) => {
                return Right(e)
              }
            }
          }
          case (Right(e), _) => {
//            debug("column: "+ column + ", row: " + row)
            if(!arr(column)(row).isLetterOrDigit){
              if(arr(column)(row).isWhitespace && arr(column).length <=row+1){
                val loc: Location = Location(column, row) //endLocation is equal to startLocation
//                debug("column: "+ column + ", row: " + row)
                return Right(ErrorMessage.EndOfLine(new Span(fileReader, loc)))
              }
              if(arr(column)(row).equals('#')){
                throw new IllegalStateException(
                  "Every '#' should have been removed in the preLexer. This is an IllegalState of the Lexer.")
              }else{
                val loc: Location = Location(column, row) //endLocation is equal to startLocation
                return Right(ErrorMessage.NotExpectedToken("Some Number or String",
                  arr(column)(row).toString, Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
              }
            }
            lexIdentifier(column, row) match {
              case (Left(ident), r) => {
                row = r
                if (ident.isInstanceOf[TypeIdentifier]) {
                  list = list.::(ident)
                } else {
                  return Right(ErrorMessage.TypeIdentifierExpectedNotIdentifier(ident.toString, ident.s))
                }
              }
              case (Right(e), _) => return Right(e)
            }
          }
        }
      }
    }
    Left((column, row, list))
  }

  private def lexTypAnnotationTokenLoopInNamedExpr(oldColumn:Int, oldRow:Int, l:List[Token]):(Int, Int, List[Token]) = {
    //debug("lexerTypAnnotationExpression: "+ l + " ( "+ oldColumn + " , " + oldRow + " )")
    var (column, row, list) = lexTypAnnotationToken(oldColumn, oldRow, l) match {
      case Left(a) => a
      case Right(_) => return (oldColumn, oldRow, l)
    }

    skipWhitespaceWhitoutNewLine(column, row) match {
      case (c, r) => {
        column = c
        row = r
      }
    }
    lexTypAnnotationTokenLoopInNamedExpr(column,row, list)
  }

  private def startNewTypAnnoationOrNewExpr(column:Int, row:Int, list:List[Token],arr: Array[String] =
  fileReader.sourceLines):List[Token]={
    if (arr(column)(row).isLetter) {

      lexIdentifier(column, row) match {
        case (Left(a), r) => {
          a match {
            case Identifier("foreign", span) => return endTypAnnotatedIdentBeginForeignFct(column, row, list)._1
            case _ =>
          }
          var newRow = r
          skipWhitespaceWhitoutNewLine(column, newRow) match {
            case (_, r) => {
              newRow = r
            }
          }

          arr(column).substring(newRow, newRow + 1) match {
            case ":" => {
              if (arr(column).length >= newRow + 2) {
                arr(column).substring(newRow, newRow + 2) match {
                  case "::" => {
                    return endTypAnnotatedIdentBeginTypAnnotatedIdent(column, row, list)._1
                  }
                  case a =>throw NotExpectedToken("'::' or '='", a, Span(fileReader,
                      Range(Location(column, newRow),Location(column, newRow+2))))
                }
              } else {
                throw NotExpectedToken("'::' or '='", arr(column).substring(newRow, newRow + 1), Span(fileReader,
                      Range(Location(column, newRow),Location(column, newRow+1))))
              }
            }
            case "=" => {
              if (arr(column).length >= row + 2) {
                arr(column).substring(row, row + 2) match {
                  case "==" =>throw NotExpectedToken("'::' or '='", "==", Span(fileReader,
                      Range(Location(column, newRow),Location(column, newRow+2))))
                  case "=>" => throw NotExpectedToken("'::' or '='", "=>", Span(fileReader,
                    Range(Location(column, newRow),Location(column, newRow+2))))
                  case a => {
                    return endTypAnnotatedIdentBeginNamedExpr(column, row, list)._1
                  }
                }
              } else {
                return endTypAnnotatedIdentBeginNamedExpr(column, row, list)._1
              }
            }
            case a =>throw NotExpectedToken("'::' or '='", a, Span(fileReader,
              Range(Location(column, newRow),Location(column, newRow+2))))
          }
        }
        case (Right(a), _) => throw a
      }
    }else {
      throw new IllegalStateException("Here should be an Identifier, but whitout an Identifier nothing new can be started")
    }
  }

  private def howToContinueLexerTypAnnoationExpression(oldColumn:Int, oldRow:Int, list:List[Token],arr: Array[String] = fileReader.sourceLines):List[Token] = {
    var column = oldColumn
    var row = oldRow

    if(row >= arr(column).length){
      //end of Line is reached is reached and TypAnnotatedIdent has to be in one line
      //debug("before isEnd1: " + column + " , " + row )
      isEnd(fileReader, column, row, arr) match {
        case Left((c, r)) => {
          column = c
          row = r
        }
        case Right(EndOfFile(_)) => return list
        case Right(EndOfLine(_)) => return list
        case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
      }
      //debug("after isEnd1: " + column + " , " + row )
      startNewTypAnnoationOrNewExpr(column,row,list,arr)
    }else{ //a second expression is accepted
      lexerTypAnnotationExpression(column, row, list)
    }
  }

  private def lexerTypAnnotationExpression(oldColumn:Int, oldRow:Int, l:List[Token]):List[Token] = {
    //debug("lexerTypAnnotationExpression: "+ l + " ( "+ oldColumn + " , " + oldRow + " )")
    var (column, row, list):(Int,Int,List[Token]) = lexTypAnnotationToken(oldColumn, oldRow, l) match{
      case Left(a) => a
      case Right(EndOfLine(_)) => return startNewTypAnnoationOrNewExpr(oldColumn+1,0,l)
      case Right(e) => {
        throw e
        return l
      }
    }

    skipWhitespaceWhitoutNewLine(column, row) match {
      case (c,r) =>{
        column = c
        row = r
      }
    }
    howToContinueLexerTypAnnoationExpression(column,row,list)
  }

  private def lexerForeignFctBody(oldColumn:Int, oldRow:Int, l:List[Token]):(List[Token],Int,Int)={
    val arr: Array[String] = fileReader.sourceLines
    var row = oldRow
    var column = oldColumn
    require(row >= 0, "row is not allowed to be negative")
    require(column >= 0, "column is not allowed to be negative")
    var list = l

    isEnd(fileReader, column, row, arr) match {
      case Left((c, r)) => {
        column = c
        row = r
      }
      case Right(EndOfFile(_)) => throw new RuntimeException("Here occoured a EndOfFile Exeption," +
        " but this should not be able to happen")
      case Right(EndOfLine(span)) => throw new RuntimeException("At position ("
        + span.range.begin.column + "," + span.range.begin.row + " is an expression expected " +
        ", but there is nothing! '" + arr(column) + "'")
      case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
    }
    val len = arr.apply(column).length
    val currentColumn=arr.apply(column).substring(row, len)
    if(currentColumn.matches("(.*)[}]")){
      val RBracesPos = currentColumn.lastIndexOf("}")
      val startLocBrace = Location(column, RBracesPos)
      val endLocBrace= Location(column, RBracesPos+1)
      if(currentColumn.matches("(\\s)*[}]")){
        list = list.::(RBraces(Span(fileReader, Range(startLocBrace, endLocBrace))))
      }else{
        val startLoc = Location(column, row)
        val endLoc= Location(column,RBracesPos)
        list = list.::(ForeignFctBodyColumn(currentColumn, Span(fileReader, Range(startLoc, endLoc))))
          .::(RBraces(Span(fileReader,
            Range(Location(column, RBracesPos), Location(column, RBracesPos+1)))))
      }
      if(arr.length <= column+1){
        row = arr(column).length
        return (list, column,row)
      }
      row = 0
      column = column+1
      isEnd(fileReader, column, row, arr) match {
        case Left((c, r)) => {
          column = c
          row = r
        }
        case Right(EndOfFile(_)) => throw new RuntimeException("Here occoured a EndOfFile Exeption," +
          " but this should not be able to happen")
        case Right(EndOfLine(span)) => throw new RuntimeException("At position ("
          + span.range.begin.column + "," + span.range.begin.row + " is an expression expected " +
          ", but there is nothing! '" + arr(column) + "'")
        case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
      }
      lexIdentifier(column, row) match {
        case (Right(_), _) => (list,column,row)
        case (Left(a), r) => {
          //debug(a)
          var newRow = r
          //            val i: Token = a
          skipWhitespaceWhitoutNewLine(column, newRow) match {
            case (c, r) => {
              newRow = r
            }
          }
      arr(column).substring(newRow, newRow + 1) match {
        case ":" => {
                endForeignFctBeginTypAnnotatedIdent(column, row, list)
          }
        case "=" => {
          endForeignFctBeginNamedExpr(column, row, list)
        }
        case a =>endForeignFctBeginForeignFct(column,row,list)
      }}}
    }else{
      val startLoc = Location(column, row)
      val endLoc= Location(column,len)
      list = list.::(ForeignFctBodyColumn(currentColumn, Span(fileReader, Range(startLoc, endLoc))))
      row = 0
      column = column+1
      lexerForeignFctBody(column,row,list)
    }
  }

  private def lexerForeignFctParameterList(oldColumn:Int, oldRow:Int, l:List[Token]):(List[Token],Int,Int)={
    val arr: Array[String] = fileReader.sourceLines
    var row = oldRow
    var column = oldColumn
    require(row >= 0, "row is not allowed to be negative")
    require(column >= 0, "column is not allowed to be negative")
    var list = l

    isEnd(fileReader, column, row, arr) match {
      case Left((c, r)) => {
        column = c
        row = r
      }
      case Right(EndOfFile(_)) => throw new RuntimeException("Here occoured a EndOfFile Exeption," +
        " but this should not be able to happen")
      case Right(EndOfLine(span)) => throw new RuntimeException("At position ("
        + span.range.begin.column + "," + span.range.begin.row + " is an expression expected " +
        ", but there is nothing! '" + arr(column) + "'")
      case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
    }

    val loc: Location = Location(column, row) //endLocation is equal to startLocation
    arr(column)(row) match {
      case ')' => {
        list = list.::(RParentheses(Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
        row = row + 1
        isEnd(fileReader, column, row, arr) match {
          case Left((c, r)) => {
            column = c
            row = r
          }
          case Right(EndOfFile(_)) => throw new RuntimeException("Here occoured a EndOfFile Exeption," +
            " but this should not be able to happen")
          case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
        }
        arr(column)(row) match{
          case '{' => {
            list = list.::(LBraces(Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
            row = row + 1
            return lexerForeignFctBody(column,row, list)
          }
          case a => throw new RuntimeException("Here is an '{' expected: "+ a)
        }
      }
      case ',' => {
        list = list.::(Comma(Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
        row = row + 1
      }
      case a => {
        lexIdentifier(column, row) match {
          case (Left(TypeIdentifier(name, span)), r) => {
            row = r
            list=list.::(TypeIdentifier(name, span))
          }
          case (Left(Identifier(name, span)), r) => {
            row = r
            list=list.::(Identifier(name, span))
          }
          case (Left(a), _) => throw ErrorMessage.NotExpectedToken("Identifier or TypeIdentifier", a.toString, a.s)
          case (Right(a), _) => {
            throw a
          }
        }
      }
    }
      isEnd(fileReader, column, row, arr) match {
        case Left((c, r)) => {
          column = c
          row = r
        }
        case Right(EndOfFile(_)) => throw new RuntimeException("Here occoured a EndOfFile Exeption," +
          " but this should not be able to happen")
        case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
      }
      //debug("after isEnd: " + column + " , " + row + " arr(column)(row)= '" + arr(column)(row)+ "'")

      if(arr.length<=column+1){
        throw new IllegalStateException("ForeignFct does not seem to have an body: "+ list)
      }else{
        lexerForeignFctParameterList(column, row, list)
      }

  }

  private def lexerExpression(oldColumn:Int, oldRow:Int, l:List[Token]):(List[Token], Int, Int) = {
    //debug("lexerExpression: "+ l + " ( "+ oldColumn + " , " + oldRow + " )")
    val arr: Array[String] = fileReader.sourceLines
    var row = oldRow
    var column = oldColumn
    require(row >= 0, "row is not allowed to be negative")
    require(column >= 0, "column is not allowed to be negative")

    var list = l
    isEnd(fileReader, column, row, arr) match {
      case Left((c, r)) => {
        column = c
        row = r
      }
      case Right(EndOfFile(_)) => throw new RuntimeException("Here occoured a EndOfFile Exeption," +
        " but this should not be able to happen")
      case Right(EndOfLine(span)) => throw new RuntimeException("At position ("
        + span.range.begin.column + "," + span.range.begin.row + " is an expression expected " +
        ", but there is nothing! '" + arr(column) + "'")
      case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
    }
    lexToken(column, row, l) match {
      case Left((co, ro, li)) => {
        column = co
        row = ro
        list = li
      }
      case Right(EndOfLine(_)) =>
      case Right(e) => throw e
    }

    //ignore whitespaces
    skipWhitespaceWhitoutNewLine(column, row) match {
      case (c, r) => {
        column = c
        row = r
      }
    }
    if (arr(column).length >= row) {
      //debug("before isEnd: " + column + " , " + row )
      isEnd(fileReader, column, row, arr) match {
        case Left((c, r)) => {
          column = c
          row = r
        }
        case Right(EndOfFile(_)) => throw new RuntimeException("Here occoured a EndOfFile Exeption," +
          " but this should not be able to happen")
        case Right(EndOfLine(_)) => {
          //debug("EndOfLine in lexerExpression: " + list)
          return (list, column, row)
        } //end is reached
        case Right(p) => throw new RuntimeException("This PreAndErrorToken was not expected: " + p)
      }
      //debug("after isEnd: " + column + " , " + row + " arr(column)(row)= '" + arr(column)(row)+ "'")

      if (arr(column)(row).isLetter) {
        lexIdentifier(column, row) match {

          case (Left(a), r) => {
            a match {
              case Identifier("foreign", span) => return endTypAnnotatedIdentBeginForeignFct(column, row, list)
              case _ =>
            }
            var newRow = r
            //            val i: Token = a
            skipWhitespaceWhitoutNewLine(column, newRow) match {
              case (c, r) => {
                newRow = r
              }
            }
            //debug("Alice Wonderland: "+ list + " <<<<>>>> " + column + " , " + row)
            if (newRow >= arr(column).length) {
              //debug("escape: " + row + " , "+ column)
              return lexerExpression(column, row, list)
            }
            arr(column).substring(newRow, newRow + 1) match {
              case ":" => {
                if (arr(column).length >= newRow + 2) {
                  arr(column).substring(newRow, newRow + 2) match {
                    case "::" => {
                      return endNamedExprBeginTypAnnotatedIdent(column, row, list)
                    }
                    case a =>
                  }
                } else {

                }
              }
              case "=" => {
                if (arr(column).length >= newRow + 2) {
                  arr(column).substring(newRow, newRow + 2) match {
                    case "==" =>
                    case "=>" =>
                    case a => {
                      //debug("endNamedExpr1: " + list)
                      return endNamedExprBeginNamedExpr(column, row, list)
                    }
                  }
                } else {
                  //debug("endNamedExpr2: " + list)
                  return endNamedExprBeginNamedExpr(column, row, list)
                }
              }
              case a =>
            }
          }
          case (Right(_), _) =>
        }
      } else {
        //debug("not in the end of Line: " + list)
      }
    }


    if (column >= arr.length || (column == arr.length - 1 && row >= arr(column).length)) {
      return (list, column, row) //end is reached
    } else { //a second expression is accepted
      //debug("Neustart: " + list + "  ( "+  column + " , " + row + " )")
      return lexerExpression(column, row, list)
    }
  }

  def typeRecognizingInNoAppExpr(c:Int, r:Int, li:List[Token], symbol: Char, arr:Array[String]):Either[(Int, Int, List[Token]),
    PreAndErrorToken] = {
    var column = c
    var row = r
    var list = li
    //debug("typeRecognizingInNoAppExpr:: "+ "column: "+ column + ", row: " + row)
  differentCasesSymbolInTypeRecognizing(column, row, list, symbol, arr) match{
    case Left((c,r,l))=>{
      column =c
      row = r
      list = l
    }
    case Right(_)=>{
      if (symbol.isLetter) { //Todo:Add here lexType
        lexIdentifier(column, row) match {
          case (Left(a), r) => {
            row = r
            list = list.::(a)
          }
          case (Right(a), _) => {
            throw a
          }
        }
      } else if (RecognizeLexeme.otherKnownSymbol(symbol)) {
        //Todo:Maybe this with lexTypAnnotationToken works fine, so that I can delete this here
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        val ex = ErrorMessage.NotExpectedToken("an Identifier or a Number or \\ or a Brace or a UnOperator or a BinOperator",
          "" + symbol, Span(fileReader, Range(loc, Location(loc.column, loc.row+1))))
        throw ex
      } else if (symbol.isWhitespace && column+1>=arr.length && arr(column).length-1 <=row) {
        //debug("exit typeRecognizingInNoAppExpr:: "+"column: "+ column + ", row: " + row)
        return Left((column, row,list))
      } else {
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        val ex = UnknownSymbol(symbol, Span(fileReader, Range(loc, Location(loc.column, loc.row+1))))
        throw ex
      }
    }
  }
    Left((column, row, list))
}

  private def endNamedExprBeginTypAnnotatedIdent(column: Int, row: Int, l: List[Token]):TokenAndPos = {
    var list = l
    val loc: Location = Location(column, row)
    val span = Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))
    list = list.::(EndNamedExpr(span))
    list = list.::(BeginTypAnnotatedIdent(span))
    var (newList, c, r) = lexerTypAnnotatedIdent(column, row, list)
    if((!newList.head.isInstanceOf[EndTypAnnotatedIdent])&&(!newList.head.isInstanceOf[EndNamedExpr])&&(!newList.head.isInstanceOf[EndForeignFct])){
      newList = newList.::(EndTypAnnotatedIdent(new Span(fileReader, Location(c, r))))
    }
    //debug("endNamedExprBeginTypAnnotatedIdent ended: "+  newList)
    (newList, c, r)
  }
  private def endNamedExprBeginNamedExpr(column: Int, row: Int, l: List[Token]):TokenAndPos = {
    var list = l
    val loc: Location = Location(column, row)
    val span = Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))
    list = list.::(EndNamedExpr(span))
    list = list.::(BeginNamedExpr(span))
    var (newList, c, r) = lexerNamedExpr(column, row, list)
    if((!newList.head.isInstanceOf[EndTypAnnotatedIdent])&&(!newList.head.isInstanceOf[EndNamedExpr])&&(!newList.head.isInstanceOf[EndForeignFct])){
      newList = newList.::(EndNamedExpr(new Span(fileReader, Location(c, r))))
    }
    //debug("endNamedExprBeginNamedExpr ended: "+  newList)
    (newList, c, r)
  }
  private def endNamedExprBeginForeignFct(column: Int, row: Int, l: List[Token]):TokenAndPos = {
    var list = l
    val loc: Location = Location(column, row)
    val span = Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))
    list = list.::(EndNamedExpr(span))
    list = list.::(BeginForeignFct(span))
    var (newList, c, r) = lexerForeignFct(column, row, list)
    if((!newList.head.isInstanceOf[EndTypAnnotatedIdent])&&(!newList.head.isInstanceOf[EndNamedExpr])&&(!newList.head.isInstanceOf[EndForeignFct])){
      newList = newList.::(EndForeignFct(new Span(fileReader, Location(c, r))))
    }
    //debug("endNamedExprBeginNamedExpr ended: "+  newList)
    (newList, c, r)
  }

  private def endTypAnnotatedIdentBeginTypAnnotatedIdent(column: Int, row: Int, l: List[Token]):TokenAndPos = {
    var list = l
    val loc: Location = Location(column, row)
    val span = Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))
    list = list.::(EndTypAnnotatedIdent(span))
    list = list.::(BeginTypAnnotatedIdent(span))
    var (newList, c, r) = lexerTypAnnotatedIdent(column, row, list)
    if((!newList.head.isInstanceOf[EndTypAnnotatedIdent])&&(!newList.head.isInstanceOf[EndNamedExpr])&&(!newList.head.isInstanceOf[EndForeignFct])){
      newList = newList.::(EndTypAnnotatedIdent(new Span(fileReader, Location(c, r))))
    }
    //debug("endTypAnnotatedIdentBeginTypAnnotatedIdent ended: "+  newList)
    (newList, c, r)
  }
  private def endTypAnnotatedIdentBeginNamedExpr(column: Int, row: Int, l: List[Token]):TokenAndPos = {
    var list = l
    val loc: Location = Location(column, row)
    val span = Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))
    list = list.::(EndTypAnnotatedIdent(span))
    list = list.::(BeginNamedExpr(span))
    var (newList, c, r) = lexerNamedExpr(column, row, list)
    if((!newList.head.isInstanceOf[EndTypAnnotatedIdent])&&(!newList.head.isInstanceOf[EndNamedExpr])&&(!newList.head.isInstanceOf[EndForeignFct])){
      newList = newList.::(EndNamedExpr(new Span(fileReader, Location(c, r))))
    }
    //debug("endTypAnnotatedIdentBeginNamedExpr ended: "+  newList)
    (newList, c, r)
  }
  private def endTypAnnotatedIdentBeginForeignFct(column: Int, row: Int, l: List[Token]):TokenAndPos = {
    var list = l
    val loc: Location = Location(column, row)
    val span = Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))
    list = list.::(EndTypAnnotatedIdent(span))
    list = list.::(BeginForeignFct(span))
    var (newList, c, r) = lexerForeignFct(column, row, list)
    if((!newList.head.isInstanceOf[EndTypAnnotatedIdent])&&(!newList.head.isInstanceOf[EndNamedExpr])&&(!newList.head.isInstanceOf[EndForeignFct])){
      newList = newList.::(EndForeignFct(new Span(fileReader, Location(c, r))))
    }
    //debug("endTypAnnotatedIdentBeginNamedExpr ended: "+  newList)
    (newList, c, r)
  }

  private def endForeignFctBeginForeignFct(column: Int, row: Int, l: List[Token]):TokenAndPos = {
    var list = l
    val loc: Location = Location(column, row)
    val span = Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))
    list = list.::(EndForeignFct(span))
    list = list.::(BeginForeignFct(span))
    var (newList, c, r) = lexerForeignFct(column, row, list)
    if((!newList.head.isInstanceOf[EndTypAnnotatedIdent])&&(!newList.head.isInstanceOf[EndNamedExpr])&&(!newList.head.isInstanceOf[EndForeignFct])){
      newList = newList.::(EndForeignFct(new Span(fileReader, Location(c, r))))
    }
    //debug("endTypAnnotatedIdentBeginNamedExpr ended: "+  newList)
    (newList, c, r)
  }
  private def endForeignFctBeginTypAnnotatedIdent(column: Int, row: Int, l: List[Token]):TokenAndPos = {
    var list = l
    val loc: Location = Location(column, row)
    val span = Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))
    list = list.::(EndForeignFct(span))
    list = list.::(BeginTypAnnotatedIdent(span))
    var (newList, c, r) = lexerTypAnnotatedIdent(column, row, list)
    if((!newList.head.isInstanceOf[EndTypAnnotatedIdent])&&(!newList.head.isInstanceOf[EndNamedExpr])&&(!newList.head.isInstanceOf[EndForeignFct])){
      newList = newList.::(EndTypAnnotatedIdent(new Span(fileReader, Location(c, r))))
    }
    //debug("endTypAnnotatedIdentBeginNamedExpr ended: "+  newList)
    (newList, c, r)
  }
  private def endForeignFctBeginNamedExpr(column: Int, row: Int, l: List[Token]):TokenAndPos = {
    var list = l
    val loc: Location = Location(column, row)
    val span = Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))
    list = list.::(EndForeignFct(span))
    list = list.::(BeginNamedExpr(span))
    var (newList, c, r) = lexerNamedExpr(column, row, list)
    if((!newList.head.isInstanceOf[EndTypAnnotatedIdent])&&(!newList.head.isInstanceOf[EndNamedExpr])&&(!newList.head.isInstanceOf[EndForeignFct])){
      newList = newList.::(EndNamedExpr(new Span(fileReader, Location(c, r))))
    }
    //debug("endTypAnnotatedIdentBeginNamedExpr ended: "+  newList)
    (newList, c, r)
  }
  private def beginTypAnnotatedIdent(column: Int, row: Int, l: List[Token]):TokenAndPos = {
    var list = l
    val loc: Location = Location(column, row)
    val span = Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))
    list = list.::(BeginTypAnnotatedIdent(span))
    lexerTypAnnotatedIdent(column, row, list)
  }

  private def beginForeignFct(column: Int, row: Int, l: List[Token]):TokenAndPos = {
    var list = l
    val loc: Location = Location(column, row)
    val span = Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))
    list = list.::(BeginForeignFct(span))
    var (newList, c, r) = lexerForeignFct(column, row, list)
    if((!newList.head.isInstanceOf[EndTypAnnotatedIdent])&&(!newList.head.isInstanceOf[EndNamedExpr])&&(!newList.head.isInstanceOf[EndForeignFct])){
      newList = newList.::(EndForeignFct(new Span(fileReader, Location(c, r))))
    }
    //debug("endTypAnnotatedIdentBeginNamedExpr ended: "+  newList)
    (newList, c, r)
  }

  private def beginNamedExpr(column: Int, row: Int, l: List[Token]):TokenAndPos = {
    var list = l
    val loc: Location = Location(column, row)
    val span = Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))
    list = list.::(BeginNamedExpr(span))
    var (newList, c, r) = lexerNamedExpr(column, row, list)
    if((!newList.head.isInstanceOf[EndTypAnnotatedIdent])&&(!newList.head.isInstanceOf[EndNamedExpr])&&(!newList.head.isInstanceOf[EndForeignFct])){
      newList = newList.::(EndForeignFct(new Span(fileReader, Location(c, r))))
    }
    //debug("endTypAnnotatedIdentBeginNamedExpr ended: "+  newList)
    (newList, c, r)
  }


  /*
  skip the Whitespaces
  return (column, row)
   */
  private def skipWhitespace(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):(Int, Int)= {
  if(arr(column).length <= row){
      if(arr.length <= column+1){
        return (column, row)
      }else{
        val c = column +1
        val r = 0
        return skipWhitespace(c,r)
      }
    }
//  debug("öööö"+ arr(column).length + " : '"+ arr(column)+ "'")
    if (arr(column)(row).isWhitespace) {
      if (arr(column).length > row + 1) {
        skipWhitespace(column, row + 1)
      } else if (arr.length > column + 1) {
        skipWhitespace(column + 1, 0)
      } else {
        (column, row)
      }
    }else{
      (column, row)
    }
  }

  private def skipWhitespaceWhitoutNewLine(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):(Int, Int)
    = {
    if(arr(column).length<=row){
      return (column, row)
    }
    if (arr(column)(row).isWhitespace) {
      if (arr(column).length > row + 1) {
        skipWhitespaceWhitoutNewLine(column, row + 1)
      } else {
        (column, row)
      }
    }else{
      (column, row)
    }
  }

  private def isTwoBackslash(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):Boolean= {
    arr(column).length> row+1 && arr(column).substring(row, row+2).equals("\\\\")
  }

  /*
  we expect to see a Backslash
  requirements:  no whitespace at arr(column)(row)
   */
  private def lexBackslash(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):
  Either[Token,PreAndErrorToken]= {
    arr(column)(row) match {
      case '\\' => {
        val loc:Location = Location(column, row)
        if(isTwoBackslash(column,row)){
          val endLoc:Location = Location(column, arr(column).length)
          Right(NotExpectedTwoBackslash("\\", Span(fileReader,Range(loc,endLoc))))
        }
        Left(Backslash(Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
      case a => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Right(ErrorMessage.NotExpectedToken("\\", ""+ a, Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
    }
  }

  private def lexLBracket(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):
  Either[Token,PreAndErrorToken]= {
    arr(column)(row) match {
      case '[' => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Left(LBracket(Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
      case a => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Right(ErrorMessage.NotExpectedToken("[", ""+ a, Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
    }
  }
  private def lexLParentheses(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):
  Either[Token,PreAndErrorToken]= {
    arr(column)(row) match {
      case '(' => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Left(LParentheses(Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
      case a => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Right(ErrorMessage.NotExpectedToken("(", ""+ a, Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
    }
  }
  private def lexLBraces(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):
  Either[Token,PreAndErrorToken]= {
    arr(column)(row) match {
      case '{' => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Left(LBraces(Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
      case a => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Right(ErrorMessage.NotExpectedToken("{", ""+ a, Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
    }
  }

  private def lexRBracket(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):
  Either[Token,PreAndErrorToken]= {
    arr(column)(row) match {
      case ']' => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Left(RBracket(Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
      case a => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Right(ErrorMessage.NotExpectedToken("]", ""+ a, Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
    }
  }


//  private def lexColon(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):Either[Token,PreAndErrorToken]={
//    arr(column)(row) match {
//      case ':' => {
//        val loc:Location = Location(column, row) //endLocation is equal to startLocation
//        Left(Colon(Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
//      }
//      case a => {
//        val loc:Location = Location(column, row) //endLocation is equal to startLocation
//        Right(NotExpectedToken(":", ""+ a, Span(fileReader,Range(loc,Location(loc.column, loc.row+1))))
//      }
//    }
//  }

  private def lexDot(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):Either[Token,PreAndErrorToken]={
    arr(column)(row) match {
      case '.' => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Left(Dot(Span(fileReader,Range(loc, Location(loc.column, loc.row+1)))))
      }
      case a => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Right(ErrorMessage.NotExpectedToken(".", ""+ a, Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
    }
  }


private def lexDeporNormalArrow(column:Int, row: Int, arr: Array[String], symbol: String):
Either[Token,PreAndErrorToken]={
  if(arr(column).length <= row +1){
    val loc:Location = Location(column, row) //endLocation is equal to startLocation
    Right(ErrorMessage.ToShortToBeThisToken(2, symbol, Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
  }else{
    val beginLoc:Location = Location(column, row)
    val endLoc:Location = Location(column, row+1)
    val span:Span = Span(fileReader,Range(beginLoc, endLoc))
    arr(column).substring(row, row+2) match {
      case "->" => {
        Left(Arrow(span))
      }
      case "=>" => {
        Left(DepArrow(span))
      }
      case a => {
        Right(ErrorMessage.NotExpectedToken(symbol, a, span))
      }
    }
  }
}

  /*
we expect to see Dots or an Arrow
requirements:  no whitespace at arr(column)(row)
 */
  private def lexDotsOrArrow(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):
  Either[Token,PreAndErrorToken]= {
    if(arr(column).length<=row){
      val loc = Location(column, row)
      val lineWithComments = fileReader.sourceLines_withoutPreLexer(column)
      if(lineWithComments.contains("--")){
        if(lineWithComments.indexOf("--") == arr(column).length){
          val locEnd = Location(column, row+2)
          return Right(ExpectedArrowButGotTwoDash(Span(fileReader,Range(loc,locEnd))))
        }
      }
      //debug("EndOfLine:"+ arr(column).length +"<="+row + " , "+arr(column))
      return Right(ErrorMessage.EndOfLine(new Span(fileReader, loc)))
    }
    arr(column)(row) match {
      case ':' => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Left(Colon(Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
      case '-' => {
        lexDeporNormalArrow(column, row, arr, "->")
      }
      case '=' => {
        lexDeporNormalArrow(column, row, arr, "=>")
      }
      case a => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Right(ErrorMessage.NotExpectedToken(":", ""+ a, Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
    }
  }

  private def lexEqualsSign(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):
  Either[Token,PreAndErrorToken]= {
    arr(column)(row) match {
      case '=' => {
        if(arr(column).length <= row +1){
          val loc:Location = Location(column, row) //endLocation is equal to startLocation
          Left(EqualsSign(Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
        }else{
          val beginLoc:Location = Location(column, row)
          val endLoc:Location = Location(column, row+1)
          val span:Span = Span(fileReader,Range(beginLoc, endLoc))
          arr(column).substring(row, row+2) match {
            case "==" => {
              Right(ErrorMessage.NotExpectedToken("=", "==", span))
            }
            case "=>" => {
              Right(ErrorMessage.NotExpectedToken("=", "=>", span))
            }
            case a => {
              val loc:Location = Location(column, row) //endLocation is equal to startLocation
              Left(EqualsSign(Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
            }
          }
        }
      }
      case a => {
        val loc:Location = Location(column, row) //endLocation is equal to startLocation
        Right(ErrorMessage.NotExpectedToken("=", ""+ a, Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
      }
    }
  }

  private def lexDoubleDots(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):
  Either[Token,PreAndErrorToken]= {
    if(arr(column).length>=row+2){
      val loc:Location = Location(column, row)
      Right(ErrorMessage.EndOfLine(new Span(fileReader, loc)))
    }
    val beginLoc:Location = Location(column, row)
    val endLoc:Location = Location(column, row+1)
    arr(column).substring(row,row+2) match {
      case "::" => {
        Left(DoubleColons(Span(fileReader,Range(beginLoc, endLoc))))
      }
      case a => {
        Right(ErrorMessage.NotExpectedToken("::", ""+ a, Span(fileReader,Range(beginLoc, endLoc))))
      }
    }
  }

/*
this lexes if it is an binary operator

if '==' then two steps else only one step
 */
  private def lexBinOperator(column:Int, row:Int, arr: Array[String] = fileReader.sourceLines):
  Either[Token,PreAndErrorToken]= {
    arr(column)(row) match {
      case '-' => {
        if (arr(column).length <= row + 1 || arr(column).substring(row, row + 2) != "->") { // -
          val loc: Location = Location(column, row) //endLocation is equal to startLocation
          Left(BinOp(OpType.BinOpType.SUB, Span(fileReader,Range(loc,Location(loc.column, loc.row+1)))))
        } else { // ->
          val locStart: Location = Location(column, row)
          val locEnd: Location = Location(column, row + 1)
          Right(ErrorMessage.NotExpectedToken("-", "->", Span(fileReader, Range(locStart, locEnd))))
        }
      }
      case '+' => { // +
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        Left(BinOp(OpType.BinOpType.ADD, Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
      }
      case '*' => { // *
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        Left(BinOp(OpType.BinOpType.MUL, Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
      }
      case '/' => { // /
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        Left(BinOp(OpType.BinOpType.DIV, Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
      }
      case '%' => { // %
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        Left(BinOp(OpType.BinOpType.MOD, Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
      }
      case '<' => { // <
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        Left(BinOp(OpType.BinOpType.LT, Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
      }
      case '>' => { // >
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        Left(BinOp(OpType.BinOpType.GT, Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
      }
      case '=' => {
        if (arr(column).length <= row + 1 || arr(column).substring(row, row + 2) != "=="||arr(column).substring(row, row + 2) != "=>") {
          val loc: Location = Location(column, row)
          Right(OnlyOneEqualSign(Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
        } else if (arr(column).substring(row, row + 2) != "=>") {
          val loc: Location = Location(column, row) //endLocation is equal to startLocation
          Right(NotExpectedToken("==", "=>", Span(fileReader, Range(loc, Location(loc.column, loc.row + 1)))))
        }
        else { // ==
          val beginLoc: Location = Location(column, row)
          val endLoc: Location = Location(column, row + 1)
          Left(BinOp(OpType.BinOpType.EQ, Span(fileReader, Range(beginLoc, endLoc))))
        }
      }
      case a => {
        val loc: Location = Location(column, row) //endLocation is equal to startLocation
        Right(NOTanBinOperator("" + a  , Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))))
      }
    }
  }


  private def getConcreteScalarType(substring:String, span:Span):Either[ConcreteType, PreAndErrorToken]={
    substring match {//different Types in RISE //Todo: not completed yet
      //Types
      case "Bool" => Left(BoolType())
      case "I16"   => Left(ShortTyp())
      case "I32"  => Left(IntTyp())
      case "F32"  => Left(FloatTyp())
      case "F64"  => Left(DoubleType())
      case "NatTyp"  => Left(NatTyp())
      case _ => Right(UnknownType(substring, span))
    }
  }

  private def lexScalarType(column:Int, row:Int,  arr:Array[String] = fileReader.sourceLines):
  (Either[Token,PreAndErrorToken],Int) = {
    val (pos, substring, locStart) = lexName(column, row, arr)
    if(pos < arr(column).length && !(arr(column)(pos).isWhitespace | RecognizeLexeme.otherKnownSymbol(arr(column)(pos)))){
      val locEnd:Location = Location(column, pos+1)
      (Right(ErrorMessage.UnknownType(substring, Span(fileReader,Range(locStart, locEnd)))),pos+1)
    }else{
      val locEnd:Location = Location(column, pos)
      val span =  Span(fileReader,Range(locStart, locEnd))
      getConcreteScalarType(substring,span) match{
        case Left(concreteType)=>(Left(ScalarType(concreteType, span)),pos)
        case Right(error) => {
          //debug("In lexScalarType: "+ error)
          (Right(error), pos)
        }
      }
    }
  }

  private def lexVectorType(column:Int, row:Int,  arr:Array[String] = fileReader.sourceLines):
  (Either[Token,PreAndErrorToken],Int) = {
    if(row+3 >= arr(column).length){
      val loc = Location(column, row)
      return (Right(ErrorMessage.EndOfLine(new Span(fileReader, loc))),row)
    }
    //debug("lexVectorType: "+ arr(column).substring(row,row+2))
    arr(column).substring(row,row+2) match{
      case "2x" => lexVectorTypeWithGivenLength(column, row+2, 2)
      case "4x" => lexVectorTypeWithGivenLength(column, row+2, 4)
      case _ => arr(column).substring(row,row+3) match{
        case "8x" =>lexVectorTypeWithGivenLength(column, row+3, 8)
        case "16x" =>lexVectorTypeWithGivenLength(column, row+3, 16)
        case _ =>{
          val locBegin = Location(column, row)
          val locEnd = Location(column, row+3)
          (Right(ErrorMessage.UnknownType(arr(column).substring(row,row+3),
            Span(fileReader, Range(locBegin, locEnd)))),row)
        }
      }
    }
  }

  private def lexVectorTypeWithGivenLength(column:Int, row:Int, len:Int, arr:Array[String] = fileReader.sourceLines):
  (Either[Token,PreAndErrorToken],Int) = {
    val (pos, substring, locStart) = lexName(column, row, arr)
    if(pos < arr(column).length && !(arr(column)(pos).isWhitespace | RecognizeLexeme.otherKnownSymbol(arr(column)(pos)))){
      val locEnd:Location = Location(column, pos+1)
      //print("Error in lexVectorType: ("+column + " , " + row + ")" + " :: Pos is " + pos)
      (Right(ErrorMessage.UnknownType(substring, Span(fileReader,Range(locStart, locEnd)))),pos+1)
    }else{
      val locEnd:Location = Location(column, pos)
      val span =  Span(fileReader,Range(locStart, locEnd))

      val scalarType = getConcreteScalarType(substring,span) match{
        case Left(concreteType)=> concreteType
        case Right(error) => return (Right(error), pos)
      }
      //print("VectorType("+len+" , " + scalarType + " , " + span + ")")
       (Left(VectorType(len, scalarType, span)),pos)
    }
  }

  private def lexKind(column:Int, row:Int,  arr:Array[String] = fileReader.sourceLines):
  (Either[Token,PreAndErrorToken],Int) = {
    val (pos, substring, locStart) = lexName(column, row, arr)
    if(pos < arr(column).length && !(arr(column)(pos).isWhitespace | RecognizeLexeme.otherKnownSymbol(arr(column)(pos)))){
      val locEnd:Location = Location(column, pos+1)
      (Right(UnknownKind(substring, Span(fileReader,Range(locStart, locEnd)))),pos+1)
    }else{
      val locEnd:Location = Location(column, pos)
      val span =  Span(fileReader,Range(locStart, locEnd))
      //different Types in RISE //Todo: not completed yet
      substring match {
        case "Data" => (Left(Kind(Data(), span)), pos)
        case "AddrSpace" => (Left(Kind(AddrSpace(), span)), pos)
        case "Nat" => (Left(Kind(Nat(), span)), pos)

        case a => (Right(ErrorMessage.UnknownKind(substring, span)),pos)
      }
    }
  }

  private def lexName(column:Int, row:Int,arr:Array[String]):(Int, String, Location) = {
    var r: Int = row + 1
    var substring: String = arr(column).substring(row, r)
    while (r-1 < arr(column).length && arr(column).substring(row, r).matches("[a-zA-Z][a-zA-Z0-9_]*")) {
      substring= arr(column).substring(row, r)
      r = r + 1
    }
    val locStart:Location = Location(column, row)
    val pos:Int = r-1
    (pos, substring, locStart)
  }
  /*
    for example "split", "go", "def", "while"

   */
  private def lexIdentifier( column:Int, row:Int, arr:Array[String] = fileReader.sourceLines):
  (Either[Token,PreAndErrorToken],Int) = {
    val (pos, substring, locStart) = lexName(column, row, arr)
    if(pos < arr(column).length && !(arr(column)(pos).isWhitespace | RecognizeLexeme.otherKnownSymbol(arr(column)(pos)))){
      val locEnd:Location = Location(column, pos+1)
      (Right(IdentifierWithNotAllowedSymbol(arr(column)(pos), arr(column).substring(row, pos+1),
        Span(fileReader,Range(locStart, locEnd)))), pos+1)
    }else{
      val locEnd:Location = Location(column, pos)
      val span = Span(fileReader,Range(locStart, locEnd))
    substring match {
      case "Local" => (Left(AddrSpaceType(substring, span)),pos)
      case "Global" => (Left(AddrSpaceType(substring, span)),pos)
      case "Private" => (Left(AddrSpaceType(substring, span)),pos)
      case "Constant" => (Left(AddrSpaceType(substring, span)),pos)
        //keywords are handled in the parser (matchPrimitiveOrIdentifier)
      case _ => {
        if (substring.matches("[a-z][a-zA-Z0-9_]*")){
          (Left(Identifier(substring, span )), pos)
        }else{
          (Left(TypeIdentifier(substring, span)), pos)
        }
      }
    }
    }
  }

  /*
  span.end.row - span.begin.row is the number of steps
 */
  private def lexNumber(column:Int, row:Int,  arr:Array[String] = fileReader.sourceLines):
  (Either[Token,PreAndErrorToken],Int) = {
    var r: Int = row + 1
    var substring: String = arr(column).substring(row, r)
    while (r-1 < arr(column).length && arr(column).substring(row, r).matches(Number.regex)) {
      substring= arr(column).substring(row, r)
      r = r + 1
    }
    val locStart:Location = Location(column, row)
    val pos:Int = r-1
    if(substring.matches("[0-9]+[.]")){ //"5." is the beginning of an array, only "5.2" is an Float
      val locEnd:Location = Location(column, pos-1)
      (Left(NatNumber(arr(column).substring(row, pos-1).toInt, Span(fileReader,Range(locStart, locEnd)))),pos-1)
    }else if(pos < arr(column).length && !(arr(column)(pos).isWhitespace | RecognizeLexeme.otherKnownSymbol(arr(column)(pos)))) {
      lexNumberComplexMatch(column, row, arr, substring, locStart, pos)
    } else if(substring.matches("[0-9]+")){
      val locEnd:Location = Location(column, pos)
      //(Left(I32(substring.toInt, Span(fileReader,Range(locStart, locEnd)))),pos)
      (Left(NatNumber(substring.toInt, Span(fileReader,Range(locStart, locEnd)))),pos)
    }else{
      val locEnd:Location = Location(column, pos)
      (Left(F32(substring.toFloat, Span(fileReader,Range(locStart, locEnd)))),pos)
    }
  }

  private def lexNatNumber(column:Int, row:Int,  arr:Array[String] = fileReader.sourceLines):(NatNumber,Int) = {
    var r: Int = row + 1
    var substring: String = arr(column).substring(row, r)
    while (r-1 < arr(column).length && arr(column).substring(row, r).matches("[0-9]+")) {
      substring= arr(column).substring(row, r)
      r = r + 1
    }
    val loc = Location(column, r)
    (NatNumber(substring.toInt, Span(fileReader, Range(loc, Location(loc.column, loc.row+1)))),r-1)
  }

  /*
  requirement: substring has the form: [0-9]+.?[0-9]*
  arr(column)(pos).isWhitespace | otherKnownSymbol(arr(column)(pos))
   */
private def lexNumberComplexMatch(column: Int, row: Int,  arr: Array[String], substring:String,
                                  locStart:Location, pos:Int):(Either[Token,PreAndErrorToken],Int) =
  arr(column)(pos) match {
  case 'I' => {
    if (substring.matches("[0-9]+")) {
      if (arr(column).substring(pos, pos + 2) == "I8") {
        val locEnd: Location = Location(column, pos + 2)
        (Left(I16(substring.toInt.toShort, Span(fileReader, Range(locStart, locEnd)))),pos+2)
      } else if (arr(column).substring(pos, pos + 3) == "I32") {
        val locEnd: Location = Location(column, pos + 3)
        (Left(I32(substring.toInt, Span(fileReader, Range(locStart, locEnd)))),pos + 3)
      } else {
        val a = createIdentifierBeginsWithDigits(column, row, pos, locStart)
        (Right(a._1),a._2)
      }
    } else { //it has an '.' in it and because of that it is not an accepted Integer-Type
      if (arr(column).substring(pos, pos + 2) == "I8") {
        val locEnd: Location = Location(column, pos + 2)
        (Right(F32DeclaredAsI8(substring.toFloat, Span(fileReader, Range(locStart, locEnd)))),pos + 2)
      } else if (arr(column).substring(pos, pos + 3) == "I32") {
        val locEnd: Location = Location(column, pos + 3)
        (Right(F32DeclaredAsI32(substring.toFloat, Span(fileReader, Range(locStart, locEnd)))),pos + 3)
      } else {
        val a= createIdentifierBeginsWithAF32Number(column, row, pos, locStart)
        (Right(a._1),a._2)
      }
    }
  }
  case 'F' => {
    //Todo: should there be an extra warning for if (substring.matches("[0-9]+")) {
    if (arr(column).substring(pos, pos + 3) == "F32") {
      val locEnd: Location = Location(column, pos + 3)
      (Left(F32(substring.toFloat, Span(fileReader, Range(locStart, locEnd)))),pos + 3)
    }else if (arr(column).substring(pos, pos + 3) == "F64") {
      val locEnd: Location = Location(column, pos + 3)
      (Left(F64(substring.toDouble, Span(fileReader, Range(locStart, locEnd)))),pos + 3)
    } else{
      val a = createIdentifierBeginsWithAF32Number(column, row, pos, locStart)
      (Right(a._1),a._2)
    }
  }
  case 'N' => {
    val loc = Location(column, pos+1)
    (Left(NatNumber(substring.toInt, Span(fileReader, Range(loc, Location(loc.column, loc.row+1))))),pos+1)
  }
  case a => {
    if(a.isLetter){
      val a= createIdentifierBeginsWithAF32Number(column, row, pos, locStart)
      (Right(a._1),a._2)
    }else if(a == '_'){
      if (substring.matches("[0-9]+")) {//it has not an '.' in it and because of that it is I32
        val a= createIdentifierBeginsWithDigits(column, row, pos, locStart)
        (Right(a._1),a._2)
      }else{//it has an '.' in it and because of that it is F32
        val a = createIdentifierBeginsWithAF32Number(column, row, pos, locStart)
        (Right(a._1),a._2)
      }
    }else{ //it is not an whitespace or an other known symbol!
      val locEnd: Location = Location(column, pos + 1)
      (Right(NumberWithUnknownSymbol(a, arr(column).substring(row, pos + 1),
        Span(fileReader, Range(locStart, locEnd)))),pos + 1)
    }
  }
}

/*
requirements:   arr(column).substring(row, pos) has the from [0-9]+
 */
private def createIdentifierBeginsWithDigits(column:Int,row:Int,   pos:Int, locStart:Location,
                                             arr:Array[String] = fileReader.sourceLines):(PreAndErrorToken,Int) ={
  val locEnd:Location = Location(column, pos+1)
  (IdentifierBeginsWithDigits(arr(column).substring(row, pos+1), Span(fileReader, Range(locStart, locEnd))),pos+1)
}

/*
requirements:   arr(column).substring(row, pos) has the from [0-9]+.?[0-9]*
 */
private def createIdentifierBeginsWithAF32Number(column:Int,row:Int,  pos:Int, locStart:Location,
                                                 arr:Array[String] = fileReader.sourceLines):(PreAndErrorToken,Int) ={
  val locEnd:Location = Location(column, pos+1)
  (IdentifierBeginsWithAF32Number(arr(column).substring(row, pos+1), Span(fileReader, Range(locStart, locEnd))),
    pos+1)
}


  /*
we expect to see an Arrow
requirements:  no whitespace at arr(column)(row)

two steps
*/
//  private def lexArrow(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):Either[Token,PreAndErrorToken]= {
//    lexDeporNormalArrow(column,row,arr, "->") match {
//      case Left(Arrow(span)) => {
//        Left(Arrow(span))
//      }
//      case Left(DepArrow(span)) => {
//        Right(NotExpectedToken("->", "=>", span))
//      }
//      case Left(a) => {
//        Right(NotExpectedToken("->", a.toString, a.s))
//      }
//      case Right(e) => Right(e)
//    }
//  }

  private def lexDepArrow(column:Int, row: Int, arr: Array[String]= fileReader.sourceLines):
  Either[Token,PreAndErrorToken]= {
    lexDeporNormalArrow(column,row,arr, "=>") match {
      case Left(DepArrow(span)) => {
        Left(DepArrow(span))
      }
      case Left(Arrow(span)) => {
        return Right(ErrorMessage.NotExpectedToken("=>", "->", span))
      }
      case Left(a) => {
        Right(ErrorMessage.NotExpectedToken("=>", a.toString, a.s))
      }
      case Right(e) => Right(e)
    }
    }

}