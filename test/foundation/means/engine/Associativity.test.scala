package opelan.foundation.means.engine

import munit.FunSuite
import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means._
import opelan.foundation.means.text._

// Worked examples drive operator associativity (Associativity.scala):
// left by default, `a ^ b ^ c means a ^ (b ^ c)` selects right, and
// the derived printer parenthesizes accordingly.
class AssociativitySuite extends FunSuite {

  private val lex = Lexicon(List(
    "int"  -> runOf(1)(_.isDigit),
    "word" -> runOf(1)(_.isLetter),
    "lpar" -> exactly("("),
    "rpar" -> exactly(")"),
    "sym"  -> runOf(1)(c => "+-*/^~".contains(c))))

  private val atom: Parser = choice(
    tok("int")(t => NodeData.IntData(t.text.toInt)),
    unrecognized(Set("sym"), Set("int", "sym")))

  private def aRule(text: String): Rule =
    readRule(text).fold(e => fail(e), identity)

  private def parse(set: RuleSet, text: String): DetachedNode[NodeData] =
    toDetached(
      compileRules(set, atom, NodeData.StringData(_), "e",
        Map.empty)(In(text, lex), 0).tree)

  private def node(name: String, kids: DetachedNode[NodeData]*) =
    DetachedNode(NodeData.StringData(name), kids.toList)
  private def lit(v: Int): DetachedNode[NodeData] =
    DetachedNode(NodeData.IntData(v), Nil)

  private val powers = RuleSet(
    rules = List(aRule("a ^ b means pow(a, b)")),
    tiers = List(Tier(List(readPattern("a ^ b")))))

  test("an infix operator associates left by default") {
    assertEquals(parse(powers, "2 ^ 3 ^ 4"),
      node("pow", node("pow", lit(2), lit(3)), lit(4)))
  }

  test("a right-grouped example selects right associativity") {
    val right = powers.copy(examples =
      List(Example("a ^ b ^ c", "a ^ (b ^ c)")))
    assertEquals(parse(right, "2 ^ 3 ^ 4"),
      node("pow", lit(2), node("pow", lit(3), lit(4))))
  }

  test("a left-grouped example confirms the default") {
    val left = powers.copy(examples =
      List(Example("a ^ b ^ c", "(a ^ b) ^ c")))
    assertEquals(parse(left, "2 ^ 3 ^ 4"),
      node("pow", node("pow", lit(2), lit(3)), lit(4)))
  }

  test("an example that doesn't chain the rule witnesses nothing") {
    val set = powers.copy(examples = List(
      Example("a ^ (b ^ c) ^ d", "(a ^ b) ^ (c ^ d)"),
      Example("1 + 2 * 3", "1 + (2 * 3)")))
    // `a ^ (b ^ c) ^ d` splits `^` three times — not a witness —
    // and `+`/`*` aren't this rule's literal.
    assertEquals(parse(set, "2 ^ 3 ^ 4"),
      node("pow", node("pow", lit(2), lit(3)), lit(4)))
  }

  test("a multi-literal operator chains on its joined literals") {
    val set = RuleSet(
      rules = List(aRule("a is not b means cmp(a, b)")),
      tiers = List(Tier(List(readPattern("a is not b")))),
      examples = List(Example("a is not b is not c", "a is not (b is not c)")))
    assertEquals(parse(set, "1 is not 2 is not 3"),
      node("cmp", lit(1), node("cmp", lit(2), lit(3))))
  }

  test("the derived printer regroups by the same associativity") {
    val right = powers.copy(examples =
      List(Example("a ^ b ^ c", "a ^ (b ^ c)")))
    val print = printWith(right, NodeData.StringData(_), {
      case NodeData.IntData(v) => v.toString
      case other               => other.toString
    })
    // The continued operand prints bare, the regrouping one keeps
    // parens — minimal for right-associative `^`.
    assertEquals(print(node("pow", lit(2), node("pow", lit(3), lit(4)))),
      "2 ^ 3 ^ 4")
    assertEquals(print(node("pow", node("pow", lit(2), lit(3)), lit(4))),
      "(2 ^ 3) ^ 4")
  }
}
