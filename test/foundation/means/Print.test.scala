package opelan.foundation.means

import munit.FunSuite
import opelan.foundation.document._

// The derived printer: construct-meaning rules are templates, the binding list
// supplies minimal parentheses — mirroring the contexts operatorTable
// parses operands under.
class PrintSuite extends FunSuite {

  private def aRule(text: String): Rule =
    readRule(text).fold(e => fail(e), identity)

  private val set = RuleSet(
    rules = List(
      "(a) means group(a)",
      "-a means neg(a)",
      "a % means pct(a)",
      "a * b means mul(a, b)",
      "a / b means div(a, b)",
      "a + b means add(a, b)",
      "a - b means sub(a, b)").map(aRule),
    tiers = List(
      Tier(List(readPattern("-a"), readPattern("a %"))),
      Tier(List(readPattern("a * b"), readPattern("a / b"))),
      Tier(List(readPattern("a + b"), readPattern("a - b")))))

  private val print = printWith(set, NodeData.StringData(_), {
    case NodeData.IntData(v)   => v.toString
    case NodeData.StringData(s) => s
    case NodeData.GapData(t)   => if (t.isEmpty) "?" else t
    case _                     => "?"
  })

  private def node(name: String, kids: DetachedNode[NodeData]*) =
    DetachedNode(NodeData.StringData(name), kids.toList)
  private def lit(v: Int): DetachedNode[NodeData] =
    DetachedNode(NodeData.IntData(v), Nil)
  private def add(l: DetachedNode[NodeData], r: DetachedNode[NodeData]) = node("add", l, r)
  private def sub(l: DetachedNode[NodeData], r: DetachedNode[NodeData]) = node("sub", l, r)
  private def mul(l: DetachedNode[NodeData], r: DetachedNode[NodeData]) = node("mul", l, r)
  private def div(l: DetachedNode[NodeData], r: DetachedNode[NodeData]) = node("div", l, r)
  private def neg(x: DetachedNode[NodeData]) = node("neg", x)
  private def pct(x: DetachedNode[NodeData]) = node("pct", x)
  private def group(x: DetachedNode[NodeData]) = node("group", x)

  test("infix prints without redundant parens") {
    assertEquals(print(add(lit(1), mul(lit(2), lit(3)))), "1 + 2 * 3")
    assertEquals(print(mul(add(lit(1), lit(2)), lit(3))), "(1 + 2) * 3")
    assertEquals(print(add(mul(lit(1), lit(2)), lit(3))), "1 * 2 + 3")
    assertEquals(print(mul(lit(1), add(lit(2), lit(3)))), "1 * (2 + 3)")
  }

  test("left-associative infix regroups the same way it parses") {
    // `1 - 2 - 3` is (1 - 2) - 3; a compound right operand keeps parens.
    assertEquals(print(sub(sub(lit(1), lit(2)), lit(3))), "1 - 2 - 3")
    assertEquals(print(sub(lit(1), sub(lit(2), lit(3)))), "1 - (2 - 3)")
    assertEquals(print(add(sub(lit(1), lit(2)), lit(3))), "1 - 2 + 3")
    assertEquals(print(add(lit(1), div(lit(2), lit(3)))), "1 + 2 / 3")
  }

  test("a prefix operand takes parens one level tighter") {
    // `-` binds tightest here, so a compound operand always parenthesizes.
    assertEquals(print(neg(lit(1))), "-1")
    assertEquals(print(neg(add(lit(1), lit(2)))), "-(1 + 2)")
    assertEquals(print(neg(mul(lit(1), lit(2)))), "-(1 * 2)")
    // A same-tier prefix nests bare — `-a` is glued, so `--1`.
    assertEquals(print(neg(neg(lit(1)))), "--1")
  }

  test("a postfix applies to the result of its level") {
    // `%` binds tightest here, so `1 + 2 %` reads as 1 + (2 %);
    // pct(1 + 2) must print with parens.
    assertEquals(print(pct(add(lit(1), lit(2)))), "(1 + 2) %")
    assertEquals(print(pct(mul(lit(1), lit(2)))), "(1 * 2) %")
    assertEquals(print(pct(neg(lit(1)))), "-1 %")
    assertEquals(print(pct(pct(lit(1)))), "1 % %")
    assertEquals(print(add(pct(lit(1)), lit(2))), "1 % + 2")
  }

  test("a closed rule prints through its pattern, content bare") {
    assertEquals(print(group(add(lit(1), lit(2)))), "(1 + 2)")
    assertEquals(print(mul(group(add(lit(1), lit(2))), lit(3))),
      "(1 + 2) * 3")
  }

  test("a keyword-led construct prints placeholders bare") {
    val ifSet = set.copy(rules =
      aRule("if c then a else b means choose(c, a, b)") +: set.rules)
    val choose = printWith(ifSet, NodeData.StringData(_), {
      case NodeData.IntData(v) => v.toString
      case _                   => "?"
    })
    assertEquals(choose(node("choose", lit(1), add(lit(2), lit(3)), lit(4))),
      "if 1 then 2 + 3 else 4")
  }

  test("a placeholder the meaning doesn't name prints as a hole") {
    val dropSet = set.copy(rules =
      aRule("a + b means keep(a)") +: set.rules)
    val keep = printWith(dropSet, NodeData.StringData(_), {
      case NodeData.IntData(v) => v.toString
      case _                   => "?"
    })
    assertEquals(keep(node("keep", lit(1))), "1 + ?")
  }

  test("constructs no rule claims and loose data degrade to holes") {
    assertEquals(print(node("wat", lit(1))), "?")
    assertEquals(print(DetachedNode.leaf(NodeData.GapData("x"))), "x")
    assertEquals(print(DetachedNode.leaf(NodeData.GapData(""))), "?")
  }
}
