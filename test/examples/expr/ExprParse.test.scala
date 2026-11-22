package opelan.examples.expr

import opelan.examples.languageFromDef
import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.means.engine.{leafText, printWith}

// The expression language's parse surface: literals, precedence,
// parentheses, signum, and the whole recovery contract — asserted on
// the document trees the def'd language produces (DefLanguage over
// decodedExprDef; the seeded demo document drops `/`, see demoExprDoc).
class ExprParseSuite extends munit.FunSuite {

  private val lang = languageFromDef(decodedExprDef)

  private def parse(input: String): DetachedNode[NodeData] =
    lang.parse(input).getOrElse(fail(s"no parse: $input"))

  // Expected trees spelled the encoding's own way — a construct
  // displays as its term's ref, a leaf holds its data.
  private def lit(v: Int) = il(v)
  private def flit(f: Double) = fl(f)
  private def ref(id: Int): DetachedNode[NodeData] =
    DetachedNode.leaf(NodeData.InternalNodeRef(id))
  private def gapExpr(t: String): DetachedNode[NodeData] =
    DetachedNode.leaf(NodeData.GapData(t))
  private def bin(tag: String, l: DetachedNode[NodeData],
                  r: DetachedNode[NodeData]) = n(exprTag(tag), l, r)

  // The derived printer over the parsed tree — printWith reads the
  // rule set backwards, the tiers supplying minimal parentheses.
  private def printTree(t: DetachedNode[NodeData]): String =
    printWith(exprReading.rules, exprTag, leafText)(t)

  test("parses literals and left-associative operator chains") {
    assertEquals(parse("42"), lit(42))
    assertEquals(parse("1 + 2 - 3"),
      bin("-", bin("+", lit(1), lit(2)), lit(3)))
    assertEquals(parse("1+2"), bin("+", lit(1), lit(2)))
  }

  test("parses parentheses") {
    assertEquals(parse("1 + (2 - 3)"),
      bin("+", lit(1), bin("-", lit(2), lit(3))))
    assertEquals(parse("(1 + 2)"), bin("+", lit(1), lit(2)))
  }

  test("parses negative literals") {
    assertEquals(parse("-7"), lit(-7))
    assertEquals(parse("1 - -2"), bin("-", lit(1), lit(-2)))
    assertEquals(parse("-1.5"), flit(-1.5))
    assertEquals(parse("+3"), lit(3))
  }

  test("parses floats and refs") {
    assertEquals(parse("1.5"), flit(1.5))
    assertEquals(parse("0.25"), flit(0.25))
    assertEquals(parse("ref:7"), ref(7))
    assertEquals(parse("12."), gapExpr("12."))  // a dangling dot is unrecognized
  }

  test("* and / bind tighter than + and -") {
    assertEquals(parse("2 + 3 * 4"),
      bin("+", lit(2), bin("*", lit(3), lit(4))))
    assertEquals(parse("2 * 3 + 4"),
      bin("+", bin("*", lit(2), lit(3)), lit(4)))
    assertEquals(parse("8 / 2 - 1"),
      bin("-", bin("/", lit(8), lit(2)), lit(1)))
    assertEquals(parse("1 + 2 * 3 - 4 / 2"),
      bin("-", bin("+", lit(1), bin("*", lit(2), lit(3))),
        bin("/", lit(4), lit(2))))
    assertEquals(parse("8 / 4 / 2"),
      bin("/", bin("/", lit(8), lit(4)), lit(2)))
  }

  test("the derived printer round-trips through the full grammar") {
    val e = bin("+", bin("*", flit(1.5), ref(4)), bin("/", lit(8), lit(2)))
    assertEquals(printTree(e), "1.5 * ref:4 + 8 / 2")
    assertEquals(parse(printTree(e)), e)
  }

  test("the derived printer parenthesizes only where binding requires") {
    val e = bin("-", bin("+", lit(1), lit(2)), bin("-", lit(3), lit(4)))
    assertEquals(printTree(e), "1 + 2 - (3 - 4)")
    assertEquals(parse(printTree(e)), e)
  }

  test("missing operand produces an empty gap") {
    assertEquals(parse("1 +"), bin("+", lit(1), gapExpr("")))
    assertEquals(parse("?"), gapExpr("?"))
  }

  test("unrecognized text produces a gap holding the verbatim text") {
    assertEquals(parse("1 + abc"), bin("+", lit(1), gapExpr("abc")))
    assertEquals(parse("hello"), gapExpr("hello"))
  }

  test("empty input produces an empty gap") {
    assertEquals(parse(""), gapExpr(""))
    assertEquals(parse("   "), gapExpr(""))
  }

  test("tokens left after a complete expression merge into a gap") {
    assertEquals(parse("1 + 2 3"), gapExpr("1 + 2 3"))
    assertEquals(parse("1 + 2 )"), gapExpr("1 + 2 )"))
  }

  test("a degenerate paren group keeps its parentheses in the gap text") {
    assertEquals(parse("(1 2)"), gapExpr("(1 2)"))
    assertEquals(parse("(1 2) + 3"), bin("+", gapExpr("(1 2)"), lit(3)))
  }

  test("an unclosed paren closes implicitly") {
    assertEquals(parse("(1 +"), bin("+", lit(1), gapExpr("")))
  }
}
