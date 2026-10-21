package opelan.foundation.grammar

import munit.FunSuite
import opelan.foundation.document._

class OperatorsSuite extends FunSuite {

  private val lex = Lexicon(List(
    "int" -> runOf(1)(_.isDigit),
    "sym" -> runOf(1)(c => "+-*/%^~!".contains(c))))

  private val atom: Parser =
    tok("int")(t => NodeData.IntData(t.text.toInt))

  private def tagged(name: String): (List[PTree], In, Int, Int) => PTree =
    (operands, in, from, to) => PTree(
      NodeData.StringData(name), in.input.substring(from, to),
      from, to - from, operands.map(adopted(_, from)))

  private def infix(
      symbol: String, name: String, assoc: Assoc = Assoc.Left): Operator =
    Operator(name, OperatorShape.Infix, List(symbol), assoc, tagged(name))

  private def parse(p: Parser, text: String): DetachedNode[NodeData] =
    toDetached(p(In(text, lex), 0).tree)

  private def node(name: String, kids: DetachedNode[NodeData]*) =
    DetachedNode(NodeData.StringData(name), kids.toList)
  private def lit(v: Int): DetachedNode[NodeData] =
    DetachedNode(NodeData.IntData(v), Nil)

  test("left-associative infix folds like chainLeft") {
    val p = operatorTable(atom, List(List(infix("-", "sub"))))
    assertEquals(parse(p, "1 - 2 - 3"),
      node("sub", node("sub", lit(1), lit(2)), lit(3)))
  }

  test("right-associative infix nests on the right") {
    val p =
      operatorTable(atom, List(List(infix("^", "pow", Assoc.Right))))
    assertEquals(parse(p, "2 ^ 3 ^ 4"),
      node("pow", lit(2), node("pow", lit(3), lit(4))))
  }

  test("non-associative infix refuses a second operator at its level") {
    val p = operatorTable(atom, List(List(infix("~", "cmp", Assoc.Non))))
    val r = p(In("1 ~ 2 ~ 3", lex), 0)
    assertEquals(toDetached(r.tree), node("cmp", lit(1), lit(2)))
    assertEquals(r.pos, 5) // `~ 3` left for the caller's recovery
  }

  test("the binding list orders tiers, tightest first") {
    val p = operatorTable(atom,
      List(List(infix("*", "mul")), List(infix("+", "add"))))
    assertEquals(parse(p, "1 + 2 * 3"),
      node("add", lit(1), node("mul", lit(2), lit(3))))
  }

  test("prefix operators nest and bind at their tier") {
    val neg =
      Operator("neg", OperatorShape.Prefix, List("-"), build = tagged("neg"))
    val p = operatorTable(atom,
      List(List(neg), List(infix("+", "add"), infix("-", "sub"))))
    assertEquals(parse(p, "-1 + 2"),
      node("add", node("neg", lit(1)), lit(2)))
    assertEquals(parse(p, "- -1"), node("neg", node("neg", lit(1))))
    assertEquals(parse(p, "1 - -2"),
      node("sub", lit(1), node("neg", lit(2))))
  }

  test("a looser prefix takes a wider operand") {
    val not =
      Operator("not", OperatorShape.Prefix, List("!"), build = tagged("not"))
    val p = operatorTable(atom,
      List(List(infix("*", "mul")), List(not)))
    assertEquals(parse(p, "!1 * 2"),
      node("not", node("mul", lit(1), lit(2))))
  }

  test("postfix operators apply after the operand") {
    val pct = Operator("pct", OperatorShape.Postfix, List("%"),
      build = tagged("pct"))
    val p = operatorTable(atom,
      List(List(pct), List(infix("+", "add"))))
    assertEquals(parse(p, "5 % + 1"),
      node("add", node("pct", lit(5)), lit(1)))
  }

  test("a missing infix operand becomes a hole in the built node") {
    val p = operatorTable(atom, List(List(infix("+", "add"))))
    assertEquals(parse(p, "1 +"),
      node("add", lit(1), DetachedNode(NodeData.GapData(""), Nil)))
  }
}
