package opelan.foundation.means.text

import munit.FunSuite
import opelan.foundation.means._

class RuleCheckSuite extends FunSuite {

  private def read(t: String): Rule = readRule(t).fold(e => fail(e), identity)

  test("shapes come from the pattern's edges") {
    val shapes = List(
      "n means literal(n)" -> Shape.Leaf,
      "(a) means a" -> Shape.Closed,
      "pi means constant()" -> Shape.Closed,
      "-a means neg(a)" -> Shape.Prefix,
      "if c then a else b means choose(c, a, b)" -> Shape.Prefix,
      "a % means a / 100" -> Shape.Postfix,
      "a + b means add(a, b)" -> Shape.Infix,
      "n x means mul(n, x)" -> Shape.Infix)
    shapes.foreach { case (t, s) => assertEquals(read(t).shape, s, t) }
    assert(Shape.Infix.isOperator && !Shape.Closed.isOperator)
  }

  test("a well-formed rule has no issues") {
    assertEquals(ruleIssues(read("a + b means add(a, b) where a, b: number")), Nil)
  }

  test("unbound, duplicated, and stray placeholders are reported") {
    assertEquals(ruleIssues(read("a + b means add(a, c)")).length, 1)
    assertEquals(ruleIssues(read("a + a means double(a)")).length, 1)
    assertEquals(ruleIssues(read("a + b means add(a, b) where z: number")).length, 1)
  }

  private val arithmetic = RuleSet(
    rules = List(
      "a + b means add(a, b)", "a - b means sub(a, b)",
      "a * b means mul(a, b)", "a / b means div(a, b)",
      "-a means neg(a)", "(a) means a").map(read),
    tiers = List(
      Tier(List(readPattern("-a"))),
      Tier(List(readPattern("a * b"), readPattern("a / b"))),
      Tier(List(readPattern("a + b"), readPattern("a - b")))))

  test("the arithmetic rule set is well formed") {
    assertEquals(ruleSetIssues(arithmetic), Nil)
  }

  test("binding-list mistakes are reported") {
    val bad = arithmetic.copy(tiers = List(
      Tier(List(readPattern("a * b"), readPattern("(a)"))),
      Tier(List(readPattern("a + b"), readPattern("a ^ b"),
        readPattern("a+b")))))
    val issues = ruleSetIssues(bad)
    assert(issues.exists(_.contains("'a ^ b'")), issues)          // no rule
    assert(issues.exists(_.contains("not an operator")), issues)  // (a)
    assert(issues.exists(_.contains("more than once")), issues)   // a+b twice
    assert(issues.exists(_.contains("'-a' is an operator with no binding")), issues)
  }

  test("a pattern defined twice is reported") {
    val twice = RuleSet(List(read("a + b means add(a, b)"),
      read("a + b means concat(a, b)")),
      List(Tier(List(readPattern("a + b")))))
    assertEquals(ruleSetIssues(twice).length, 1)
  }
}
