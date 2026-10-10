package opelan.foundation.language

import Expr._

class ExprParseSuite extends munit.FunSuite {

  private def parse(input: String): Expr = parseExpr(input).expr

  test("parses literals and left-associative operator chains") {
    assertEquals(parse("42"), Lit(42))
    assertEquals(parse("1 + 2 - 3"), Sub(Add(Lit(1), Lit(2)), Lit(3)))
    assertEquals(parse("1+2"), Add(Lit(1), Lit(2)))
  }

  test("parses parentheses") {
    assertEquals(parse("1 + (2 - 3)"), Add(Lit(1), Sub(Lit(2), Lit(3))))
    assertEquals(parse("(1 + 2)"), Add(Lit(1), Lit(2)))
  }

  test("parses negative literals") {
    assertEquals(parse("-7"), Lit(-7))
    assertEquals(parse("1 - -2"), Sub(Lit(1), Lit(-2)))
    assertEquals(parse("-1.5"), FLit(-1.5))
    assertEquals(parse("+3"), Lit(3))
  }

  test("parses floats and refs") {
    assertEquals(parse("1.5"), FLit(1.5))
    assertEquals(parse("0.25"), FLit(0.25))
    assertEquals(parse("ref:7"), Ref(7))
    assertEquals(parse("12."), Hole("12."))  // a dangling dot is junk
  }

  test("* and / bind tighter than + and -") {
    assertEquals(parse("2 + 3 * 4"), Add(Lit(2), Mul(Lit(3), Lit(4))))
    assertEquals(parse("2 * 3 + 4"), Add(Mul(Lit(2), Lit(3)), Lit(4)))
    assertEquals(
      parse("8 / 2 - 1"), Sub(Div(Lit(8), Lit(2)), Lit(1)))
    assertEquals(
      parse("1 + 2 * 3 - 4 / 2"),
      Sub(Add(Lit(1), Mul(Lit(2), Lit(3))), Div(Lit(4), Lit(2))))
    assertEquals(
      parse("8 / 4 / 2"), Div(Div(Lit(8), Lit(4)), Lit(2)))
  }

  test("round-trips Expr.print with the full grammar") {
    val e = Add(Mul(FLit(1.5), Ref(4)), Div(Lit(8), Lit(2)))
    assertEquals(parse(print(e)), e)
  }

  test("round-trips Expr.print") {
    val e = Sub(Add(Lit(1), Lit(2)), Sub(Lit(3), Lit(4)))
    assertEquals(parse(print(e)), e)
  }

  test("missing operand produces an empty hole") {
    assertEquals(parse("1 +"), Add(Lit(1), Hole("")))
    assertEquals(parse("?"), Hole(""))
  }

  test("junk text produces a hole holding the verbatim text") {
    assertEquals(parse("1 + abc"), Add(Lit(1), Hole("abc")))
    assertEquals(parse("hello"), Hole("hello"))
  }

  test("empty input produces an empty hole") {
    assertEquals(parse(""), Hole(""))
    assertEquals(parse("   "), Hole(""))
  }

  test("tokens left after a complete expression merge into a hole") {
    assertEquals(parse("1 + 2 3"), Hole("1 + 2 3"))
    assertEquals(parse("1 + 2 )"), Hole("1 + 2 )"))
  }

  test("a degenerate paren group keeps its parens in the hole text") {
    assertEquals(parse("(1 2)"), Hole("(1 2)"))
    assertEquals(parse("(1 2) + 3"), Add(Hole("(1 2)"), Lit(3)))
  }

  test("an unclosed paren closes implicitly") {
    assertEquals(parse("(1 +"), Add(Lit(1), Hole("")))
  }

  test("span map records every node's source range") {
    val r = parseExpr("1 + 23")
    assertEquals(r.spans(Nil), ExprSpan(0, 6))
    assertEquals(r.spans(List(0)), ExprSpan(0, 1))
    assertEquals(r.spans(List(1)), ExprSpan(4, 6))
  }
}
