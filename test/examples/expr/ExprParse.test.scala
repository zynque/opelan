package opelan.examples.expr

import Expr._

// The expression language's parse surface: literals, precedence,
// parentheses, signum, and the whole recovery contract — asserted through
// the def'd language (DefLanguage over exprDef), the only parser now.
class ExprParseSuite extends munit.FunSuite {

  private def parse(input: String): Expr =
    exprFromDetached(ExprLanguage.parse(input).get)

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
    assertEquals(parse("12."), Gap("12."))  // a dangling dot is unrecognized
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

  test("missing operand produces an empty gap") {
    assertEquals(parse("1 +"), Add(Lit(1), Gap("")))
    assertEquals(parse("?"), Gap("?"))
  }

  test("unrecognized text produces a gap holding the verbatim text") {
    assertEquals(parse("1 + abc"), Add(Lit(1), Gap("abc")))
    assertEquals(parse("hello"), Gap("hello"))
  }

  test("empty input produces an empty gap") {
    assertEquals(parse(""), Gap(""))
    assertEquals(parse("   "), Gap(""))
  }

  test("tokens left after a complete expression merge into a gap") {
    assertEquals(parse("1 + 2 3"), Gap("1 + 2 3"))
    assertEquals(parse("1 + 2 )"), Gap("1 + 2 )"))
  }

  test("a degenerate paren group keeps its parentheses in the gap text") {
    assertEquals(parse("(1 2)"), Gap("(1 2)"))
    assertEquals(parse("(1 2) + 3"), Add(Gap("(1 2)"), Lit(3)))
  }

  test("an unclosed paren closes implicitly") {
    assertEquals(parse("(1 +"), Add(Lit(1), Gap("")))
  }
}
