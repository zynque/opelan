package opelan.foundation.means

import munit.FunSuite
import Piece._
import Meaning._

class RuleReadSuite extends FunSuite {

  private def read(t: String): Rule = readRule(t).fold(e => fail(e), identity)

  test("infix rule with a constructor meaning") {
    val r = read("a + b means add(a, b)")
    assertEquals(r.pattern.pieces, List(Placeholder("a"), Literal("+"), Placeholder("b")))
    assertEquals(r.meaning, construct("add", List(use("a"), use("b"))))
    assertEquals(r.where, Nil)
  }

  test("=> is the terse spelling of means") {
    assertEquals(read("a * b => mul(a, b)"), read("a * b means mul(a, b)"))
  }

  test("glued pieces record spacing but not identity") {
    val tight = read("-a means neg(a)").pattern
    assertEquals(tight.pieces, List(Literal("-"), Placeholder("a", glued = true)))
    assertEquals(tight.key, read("- a means neg(a)").pattern.key)
  }

  test("a bare placeholder meaning") {
    assertEquals(read("(a) means a").meaning, use("a"))
  }

  test("builtins spelled like constructs read as builtin terms") {
    assertEquals(read("a means use(a)").meaning, use("a"))
    assertEquals(read("a means source(a)").meaning, source("a"))
  }

  test("a phrase meaning in the same language") {
    assertEquals(read("a % means a / 100").meaning,
      reparse(List(Placeholder("a"), Literal("/"), Literal("100"))))
  }

  test("nested constructor arguments") {
    assertEquals(read("-n means literal(neg(n))").meaning,
      construct("literal", List(construct("neg", List(use("n"))))))
  }

  test("where clause with several groups and multi-word kinds") {
    val r = read("ref:c,r means cell(c, r) where c, r: whole number; x: text")
    assertEquals(r.where, List(
      Constraint(List("c", "r"), "whole number"),
      Constraint(List("x"), "text")))
    assertEquals(r.pattern.placeholders, List("c", "r"))
  }

  test("placeholder convention: lone lowercase letters, optionally numbered") {
    val p = read("if c then a1 else b means choose(c, a1, b)").pattern
    assertEquals(p.placeholders, List("c", "a1", "b"))
    assert(!isPlaceholderWord("kg") && !isPlaceholderWord("A") && isPlaceholderWord("x2"))
  }

  test("quotes force a literal") {
    assertEquals(read("a 'x' b means cross(a, b)").pattern.pieces,
      List(Placeholder("a"), Literal("x"), Placeholder("b")))
  }

  test("only the first means splits; where is a keyword after it") {
    assertEquals(read("a 'means' b means eq(a, b)").pattern.pieces,
      List(Placeholder("a"), Literal("means"), Placeholder("b")))
  }

  test("malformed rules say what's wrong") {
    assertEquals(readRule("a + b"), Left("missing 'means'"))
    assertEquals(readRule("means add(a, b)"), Left("empty pattern"))
    assertEquals(readRule("a + b means"), Left("empty meaning"))
    assert(readRule("a + b means add(a, b) where a b").isLeft)
  }

  test("a malformed line reads as a hole rule that never applies") {
    val r = readRuleOrHole("a + b") // missing 'means'
    assertEquals(r.pattern.pieces, Nil)
    assertEquals(r.meaning, reparse(Nil))
    assert(ruleIssues(r).nonEmpty) // still reported as malformed
  }

  test("a call that doesn't span the whole meaning is a phrase") {
    assertEquals(read("a means abs(a) + 1").meaning,
      reparse(List(Literal("abs"), Literal("(", true), Placeholder("a", true),
        Literal(")", true), Literal("+"), Literal("1"))))
  }
}
