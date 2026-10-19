package opelan.foundation.grammar

import opelan.foundation.document._
import munit.FunSuite

// Direct tests of the scannerless combinator layer: parsing produces
// PTree nodes whose data is checked structurally (IntData / StringData
// tag / GapData), plus totality, spans, and examined extents.
class CombSuite extends FunSuite {

  private val lexicon = Lexicon(List(
    "int"  -> runOf(1)(_.isDigit),
    "op"   -> oneOf("+-*"),
    "lpar" -> exactly("("),
    "rpar" -> exactly(")"),
    "junk" -> runOf(1)(c => c.isLetter)))

  private def in(text: String) = In(text, lexicon)

  private val int = tok("int")(t => NodeData.IntData(t.text.toInt))

  private def tag(s: String) = NodeData.StringData(s)

  test("tok applies on its kind and fails elsewhere without consuming") {
    val i = in("1+")
    assertEquals(int(i, 0).tree.data, NodeData.IntData(1))
    val miss = int(i, 1)
    assertEquals(miss.ok, false)
    assertEquals(miss.pos, 1) // nothing consumed
    assertEquals(miss.tree.data, NodeData.GapData(""))
  }

  test("tokens skip leading whitespace; positions are characters") {
    val r = int(in("  42 "), 0)
    assertEquals((r.tree.from, r.tree.to, r.pos), (2, 4, 4))
  }

  test("an unlexable character is still a token, so no text is lost") {
    val r = phrase(int, Set.empty)(in("1 #"), 0)
    assertEquals(r.tree.data, NodeData.GapData("1 #"))
  }

  test("chainLeft folds left-associatively") {
    val r = chainLeft(Map("-" -> tag("sub")))(int)(in("1 - 2 - 3"), 0)
    assertEquals(r.pos, 9)
    assertEquals(r.tree.data, tag("sub"))
    assertEquals(r.tree.kids(0).data, tag("sub"))
    assertEquals(r.tree.kids(1).data, NodeData.IntData(3))
  }

  test("precedenceTable binds the last level loosest") {
    val p = precedenceTable(int, List(
      Map("*" -> tag("mul")),
      Map("+" -> tag("add"))))
    val r = p(in("1+2*3"), 0)
    assertEquals(r.pos, 5)
    assertEquals(r.tree.data, tag("add"))
    assertEquals(r.tree.kids(1).data, tag("mul"))
  }

  test("parens stops at ')' and a missing close is tolerated") {
    val p = parens(int)
    val closed = p(in("(1)"), 0)
    assertEquals(closed.pos, 3)
    assertEquals(closed.tree.data, NodeData.IntData(1))
    assertEquals(closed.tree.text, "(1)")
    val open = p(in("(1"), 0)
    assertEquals(open.pos, 2)
    assertEquals(open.tree.data, NodeData.IntData(1))
    assertEquals(open.tree.text, "(1")
  }

  test("phrase merges unplaced leftovers into one hole") {
    val r = phrase(int, Set.empty)(in("1x"), 0)
    assertEquals(r.tree.data, NodeData.GapData("1x"))
    assertEquals(r.pos, 2)
  }

  test("examined covers the lookahead that decided a result") {
    // "12+" — int reads up to '+' to know the digits ended.
    assertEquals(int(in("12+"), 0).examined, 3)
    // At the end of input, the end itself was observed.
    assertEquals(int(in("12"), 0).examined, 3)
    // chainLeft looked past the last operand for another operator: it
    // scanned "x" (junk runs to the end, so the end was observed too) —
    // appending to the text could change this result.
    val p = chainLeft(Map("+" -> tag("add")))(int)
    assertEquals(p(in("1+2 x"), 0).examined, 6)
    assertEquals(p(in("1+2 x"), 0).pos, 3)
  }

  test("spansOf maps every node path to its source range") {
    val r = chainLeft(Map("-" -> tag("sub")))(int)(in("1-23"), 0)
    val spans = spansOf(r.tree)
    assertEquals(spans(Nil), Span(0, 4))
    assertEquals(spans(List(0)), Span(0, 1))
    assertEquals(spans(List(1)), Span(2, 4))
    assertEquals(toDetached(r.tree).children.length, 2)
  }
}
