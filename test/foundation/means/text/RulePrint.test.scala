package opelan.foundation.means.text

import munit.FunSuite
import opelan.foundation.means._
import opelan.foundation.view.Verbosity

class RulePrintSuite extends FunSuite {

  private def read(t: String): Rule = readRule(t).fold(e => fail(e), identity)

  private val battery = List(
    "a + b means add(a, b)",
    "-a means neg(a)",
    "(a) means a",
    "|a| means abs(a)",
    "a % means a / 100",
    "if c then a else b means choose(c, a, b)",
    "ref:c,r means cell(c, r) where c, r: whole number",
    "n kg means quantity(n, kilogram) where n: number",
    "a .. b means range(a, b) where a: cell; b: cell",
    "a 'x' b means cross(a, b)",
    "pi means constant()")

  test("verbose printing round-trips text exactly") {
    battery.foreach(t => assertEquals(printRule(read(t)), t))
  }

  test("verbose printing round-trips the model") {
    battery.foreach { t =>
      val r = read(t)
      assertEquals(read(printRule(r)), r)
    }
  }

  test("terse spells => and hides where behind a marker") {
    val r = read("a + b means add(a, b) where a, b: number")
    assertEquals(printRule(r, Verbosity.Terse), "a + b => add(a, b) …")
    assertEquals(printRule(read("-a means neg(a)"), Verbosity.Terse),
      "-a => neg(a)")
  }

  test("spans tag roles for highlighting") {
    val spans = ruleSpans(read("a + b means add(a, b)"), Verbosity.Verbose)
      .filterNot(_.role == Role.Space)
    assertEquals(spans.map(s => (s.text, s.role)), List(
      "a" -> Role.Placeholder, "+" -> Role.Literal, "b" -> Role.Placeholder,
      "means" -> Role.Keyword, "add" -> Role.Tag, "(" -> Role.Punctuation,
      "a" -> Role.Placeholder, "," -> Role.Punctuation, "b" -> Role.Placeholder,
      ")" -> Role.Punctuation))
  }

  test("an explicit use term prints as the bare placeholder") {
    assertEquals(printRule(read("a means use(a)")), "a means a")
    assertEquals(printRule(read("a means source(a)")), "a means source(a)")
  }

  test("literals that would misread are quoted") {
    val r = Rule(
      Pattern(List(Piece.Placeholder("a"), Piece.Literal("where"), Piece.Placeholder("b"))),
      Meaning.use("a"))
    assertEquals(printRule(r), "a 'where' b means a")
  }
}
