package opelan.foundation.grammar

import opelan.foundation.document._
import munit.FunSuite

// Direct tests of the combinator layer: parsing produces PTree nodes
// whose data is checked structurally (IntData / StringData tag /
// GapData), plus totality and span coverage.
class CombSuite extends FunSuite {

  // "1 + 2 * 3" etc. over int/op/lpar/rpar tokens.
  private def in(input: String, specs: (String, String)*): In = {
    // specs are (kind, text); positions come from laying the texts down
    // back to back — token contents matter, not the gaps between them.
    var at = 0
    val toks = specs.map { case (k, s) =>
      val t = GTok(k, s, at, at + s.length); at += s.length; t
    }.toVector
    In(input, toks)
  }

  private val int = tok("int")(t => NodeData.IntData(t.text.toInt))

  private def tag(s: String) = NodeData.StringData(s)

  test("tok applies on its kind and fails elsewhere without consuming") {
    val i = in("1+", ("int", "1"), ("op", "+"))
    assertEquals(int(i, 0).tree.data, NodeData.IntData(1))
    val miss = int(i, 1)
    assertEquals(miss.ok, false)
    assertEquals(miss.pos, 1) // nothing consumed
    assertEquals(miss.tree.data, NodeData.GapData(""))
  }

  test("chainLeft folds left-associatively") {
    // "1 - 2 - 3" at one level => sub(sub(1, 2), 3)
    val p = chainLeft(Map("-" -> NodeData.StringData("sub")))(int)
    val i = in("1-2-3",
      ("int", "1"), ("op", "-"), ("int", "2"), ("op", "-"), ("int", "3"))
    val r = p(i, 0)
    assertEquals(r.pos, 5)
    r.tree.data match {
      case NodeData.StringData("sub") =>
        assertEquals(r.tree.kids(0).data, NodeData.StringData("sub"))
        assertEquals(r.tree.kids(1).data, NodeData.IntData(3))
      case other => fail(s"expected sub root, got $other")
    }
  }

  test("precedenceTable binds the last level loosest") {
    // tightest-first levels: "1 + 2 * 3" => add(1, mul(2, 3))
    val p = precedenceTable(int, List(
      Map("*" -> NodeData.StringData("mul")),
      Map("+" -> NodeData.StringData("add"))))
    val i = in("1+2*3",
      ("int", "1"), ("op", "+"), ("int", "2"), ("op", "*"), ("int", "3"))
    val r = p(i, 0)
    assertEquals(r.pos, 5)
    assertEquals(r.tree.data, tag("add"))
    assertEquals(r.tree.kids(1).data, tag("mul"))
  }

  test("parens stops at ')' and a missing close is tolerated") {
    val inner = int
    val p = parens(inner)
    // "(1)" — closes cleanly
    val closed = p(in("(1)", ("lpar", "("), ("int", "1"), ("rpar", ")")), 0)
    assertEquals(closed.pos, 3)
    assertEquals(closed.tree.data, NodeData.IntData(1))
    assertEquals(closed.tree.text, "(1)")
    // "(1" — EOF stands in for the close
    val open = p(in("(1", ("lpar", "("), ("int", "1")), 0)
    assertEquals(open.pos, 2)
    assertEquals(open.tree.data, NodeData.IntData(1))
    assertEquals(open.tree.text, "(1")
  }

  test("phrase merges unplaced leftovers into one hole") {
    // "1 x" — the junk token can't continue the level; the whole thing
    // becomes a hole covering both tokens.
    val p = phrase(int, Set.empty)
    val r = p(in("1x", ("int", "1"), ("junk", "x")), 0)
    assertEquals(r.tree.data, NodeData.GapData("1x"))
    assertEquals(r.pos, 2)
  }

  test("spansOf maps every node path to its source range") {
    val p = chainLeft(Map("-" -> NodeData.StringData("sub")))(int)
    val i = in("1-23",
      ("int", "1"), ("op", "-"), ("int", "23"))
    val r = p(i, 0)
    val spans = spansOf(r.tree)
    assertEquals(spans(Nil), Span(0, 4))
    assertEquals(spans(List(0)), Span(0, 1))
    assertEquals(spans(List(1)), Span(2, 4))
    assertEquals(toDetached(r.tree).children.length, 2)
  }
}
