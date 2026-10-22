package opelan.foundation.means

import munit.FunSuite
import opelan.foundation.document._
import opelan.foundation.grammar._

class CompileSuite extends FunSuite {

  private val lex = Lexicon(List(
    "int"  -> runOf(1)(_.isDigit),
    "word" -> runOf(1)(_.isLetter),
    "lpar" -> exactly("("),
    "rpar" -> exactly(")"),
    "sym"  -> runOf(1)(c => "+-*/%!".contains(c))))

  private val atom: Parser = choice(
    tok("int")(t => NodeData.IntData(t.text.toInt)),
    junk(Set("sym", "word"), Set("int", "word", "sym")))

  private def aRule(text: String): Rule =
    readRule(text).fold(e => fail(e), identity)

  private val arithmetic = RuleSet(
    rules = List(
      "(a) means a",
      "-a means neg(a)",
      "a * b means mul(a, b)",
      "a / b means div(a, b)",
      "a + b means add(a, b)",
      "a - b means sub(a, b)").map(aRule),
    tiers = List(
      Tier(List(readPattern("-a"))),
      Tier(List(readPattern("a * b"), readPattern("a / b"))),
      Tier(List(readPattern("a + b"), readPattern("a - b")))))

  private def parse(set: RuleSet, text: String): DetachedNode[NodeData] =
    toDetached(
      compileRules(set, atom, NodeData.StringData(_), "e")(In(text, lex), 0).tree)

  private def node(name: String, kids: DetachedNode[NodeData]*) =
    DetachedNode(NodeData.StringData(name), kids.toList)
  private def lit(v: Int): DetachedNode[NodeData] =
    DetachedNode(NodeData.IntData(v), Nil)
  private def gap(t: String): DetachedNode[NodeData] =
    DetachedNode(NodeData.GapData(t), Nil)

  test("infix rules respect the binding list") {
    assertEquals(parse(arithmetic, "1 + 2 * 3"),
      node("add", lit(1), node("mul", lit(2), lit(3))))
    assertEquals(parse(arithmetic, "8 / 4 / 2"),
      node("div", node("div", lit(8), lit(4)), lit(2)))
  }

  test("prefix rules nest and bind at their tier") {
    assertEquals(parse(arithmetic, "-1 + 2"),
      node("add", node("neg", lit(1)), lit(2)))
    assertEquals(parse(arithmetic, "- -1"),
      node("neg", node("neg", lit(1))))
    assertEquals(parse(arithmetic, "1 - -2"),
      node("sub", lit(1), node("neg", lit(2))))
  }

  test("closed rules serve as operands") {
    assertEquals(parse(arithmetic, "(1 + 2) * 3"),
      node("mul", node("add", lit(1), lit(2)), lit(3)))
    assertEquals(parse(arithmetic, "((4))"), lit(4))
  }

  test("an unclosed bracket closes implicitly, meaning intact") {
    assertEquals(parse(arithmetic, "(1 +"),
      node("add", lit(1), gap("")))
  }

  test("a degenerate bracket keeps its brackets in the hole") {
    assertEquals(parse(arithmetic, "(1 2)"), gap("(1 2)"))
    assertEquals(parse(arithmetic, "(1 2) + 3"),
      node("add", gap("(1 2)"), lit(3)))
  }

  test("a phrase meaning reparses with the language's own rules") {
    val percent = arithmetic.copy(
      rules = arithmetic.rules :+ aRule("a % means a / 100"),
      tiers = arithmetic.tiers.updated(0,
        Tier(readPattern("-a") :: readPattern("a %") :: Nil)))
    assertEquals(parse(percent, "5 %"), node("div", lit(5), lit(100)))
    assertEquals(parse(percent, "5 % + 1"),
      node("add", node("div", lit(5), lit(100)), lit(1)))
  }

  test("a keyword-led rule compiles to a sequence, no binding needed") {
    val ifSet = RuleSet(
      rules = List(aRule("if c then a else b means choose(c, a, b)")))
    assertEquals(ruleSetIssues(ifSet), Nil)
    assertEquals(parse(ifSet, "if 1 then 2 else 3"),
      node("choose", lit(1), lit(2), lit(3)))
    assertEquals(parse(ifSet, "if 1 then 2 else 3 + 4"),
      node("choose", lit(1), lit(2), lit(3)))
  }

  test("operators the binding list forgot compile loosest") {
    val flat = arithmetic.copy(tiers = Nil)
    assertEquals(parse(flat, "1 + 2 * 3"),
      node("mul", node("add", lit(1), lit(2)), lit(3)))
  }

  test("a constructor meaning nests") {
    val set = RuleSet(rules = List(
      aRule("-n means neg(neg(n))"), aRule("(a) means a")),
      tiers = List(Tier(List(readPattern("-n")))))
    assertEquals(parse(set, "-5"),
      node("neg", node("neg", lit(5))))
  }

  test("a bare placeholder meaning passes the bound tree through") {
    assertEquals(parse(arithmetic, "(1)"), lit(1))
  }
}
