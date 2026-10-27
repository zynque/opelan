package opelan.foundation.grammar

import scala.util.Random
import opelan.foundation.document._
import munit.FunSuite

// Incremental parsing: whatever the edits and rule changes, a session's
// result equals a full parse — and it gets there by reusing what the
// change could not have affected, keeping those subtrees' identities.
class ParseSessionSuite extends FunSuite {

  private val lexicon = Lexicon(List(
    "int"  -> runOf(1)(_.isDigit),
    "word" -> runOf(1)(_.isLetter),
    "op"   -> oneOf("+*"),
    "semi" -> exactly(";"),
    "lpar" -> exactly("("),
    "rpar" -> exactly(")")))

  private def tag(s: String) = NodeData.StringData(s)
  private val int = tok("int")(t => NodeData.IntData(t.text.toInt))
  private val word = tok("word")(t => NodeData.StringData(t.text))
  private val semi = tok("semi")(_ => tag(";"))

  // program := statement*;  statement := (sum | words) ";"?
  private def grammar(wordsBody: => Parser): Parser = {
    lazy val sum: Parser = rule("sum")(chainLeft(Map("+" -> tag("add")))(term))
    lazy val term: Parser = rule("term")(chainLeft(Map("*" -> tag("mul")))(
      choice(int, parens(sum))))
    val words = rule("words")(wordsBody)
    val statement = rule("statement")(
      sequence(tag("statement"))(choice(sum, words), optional(semi)))
    many(tag("program"))(statement)
  }

  private val plainWords = many(tag("words"))(word)
  private val g1 = grammar(plainWords)
  // A changed `words` rule: words may now be separated by "+".
  private val g2 = grammar(chainLeft(Map("+" -> tag("and")))(word))

  private def normal(r: POut) = (r.pos, r.ok, r.examined, r.tree)
  // A marking table so the oracle's trees carry the same ParseMarks a
  // session's do — marks are deterministic bookkeeping, not content.
  private def full(g: Parser, text: String) =
    normal(g(In(text, lexicon, MemoTable(marking = true)), 0))

  // Two statement nodes hold the same subtree: their kid lists are the
  // same object (memoized results share their graphs when reused).
  private def sameSubtree(a: PTree, b: PTree): Boolean =
    a.kids.nonEmpty && (a.kids eq b.kids)

  test("a fresh session equals a plain parse") {
    val s = ParseSession.start(g1, lexicon, "1 + 2; ab cd; (3 * 4)")
    assertEquals(normal(s.result), full(g1, s.text))
  }

  test("property: after any edits, incremental equals full reparse") {
    val random = new Random(7)
    val alphabet = "0123456789+*();ab "
    var s = ParseSession.start(g1, lexicon, "1 + 2 * 3; ab; (4 + 5) * 6; cd ef")
    (1 to 400).foreach { i =>
      val from = random.nextInt(s.text.length + 1)
      val to = from + random.nextInt((s.text.length - from).min(3) + 1)
      val inserted = List.fill(random.nextInt(4))(alphabet(random.nextInt(alphabet.length))).mkString
      s = s.edit(Edit(from, to, inserted))
      assertEquals(normal(s.result), full(g1, s.text), s"edit $i on: ${s.text}")
    }
  }

  test("an edit reruns only what it touched; reused subtrees are the same objects") {
    val text = (1 to 20).map(i => s"$i + $i;").mkString(" ")
    val s0 = ParseSession.start(g1, lexicon, text)
    val at = text.indexOf("15 + 15")
    val s1 = s0.edit(Edit(at, at + 2, "99"))
    assertEquals(normal(s1.result), full(g1, s1.text))
    assert(s1.evaluations * 4 < s0.evaluations,
      s"${s1.evaluations} evaluations after the edit vs ${s0.evaluations} initially")
    val (before, after) = (s0.tree.kids, s1.tree.kids)
    assert(sameSubtree(after(0), before(0)))     // before the edit: reused
    assert(sameSubtree(after(19), before(19)))   // after the edit: reused, unmoved
    assert(!sameSubtree(after(14), before(14)))  // the edited statement: new
    // A same-length edit: statement 19 keeps its absolute span.
    assertEquals(spansOf(s1.tree)(List(19)), spansOf(s0.tree)(List(19)))
  }

  test("a length-changing edit moves nothing: subtrees after it are reused as-is") {
    val text = (1 to 8).map(i => s"$i + $i;").mkString(" ")
    val s0 = ParseSession.start(g1, lexicon, text)
    val at = text.indexOf("3 + 3")
    val s1 = s0.edit(Edit(at, at + 5, "300 + 300"))
    assertEquals(normal(s1.result), full(g1, s1.text))
    // Statements after the edit sit at later absolute positions, but
    // their subtrees are the same objects — nothing was shifted.
    val (before, after) = (s0.tree.kids, s1.tree.kids)
    assert(sameSubtree(after(7), before(7)))
    assertNotEquals(
      spansOf(s1.tree)(List(7)), spansOf(s0.tree)(List(7)))
  }

  test("the memo lives on the tree: marks claim what the side table can't") {
    val s = ParseSession.start(g1, lexicon, "1 + 2; ab cd; (3 * 4)")
    // Result-tree nodes carry marks naming the rules that produced them.
    val marked = liveMarks(s.tree)
    assertEquals(marked.map(_._3.rule).toSet,
      Set("sum", "term", "words", "statement"))
    // What the marks claim, the side table doesn't repeat — it holds
    // only results the tree can't represent (rejected alternatives).
    val claimed = marked.map((pos, _, m) => (m.rule, pos)).toSet
    s.side.zipWithIndex.foreach { case (column, i) =>
      column.keys.foreach(name => assert(!claimed((name, i)))) }
    // `consulted` still sees every applied rule — including `words`,
    // which failed at column 0 and so lives only in the side table.
    assert(s.consulted == Set("sum", "term", "words", "statement"))
  }

  test("a rule change drops only results that depend on the changed rule") {
    val s0 = ParseSession.start(g1, lexicon, "1 + 2; ab cd; 3 * 4; ef")
    val s1 = s0.changeRules(g2, Set("words"))
    assertEquals(normal(s1.result), full(g2, s1.text))
    // The new `words` rule splits "ab cd" into two statements, so find
    // statements by their text: the numeric ones never called `words`.
    def statement(s: ParseSession, text: String) =
      s.tree.kids.find(_.text == text).get
    assert(sameSubtree(statement(s1, "1 + 2;"), statement(s0, "1 + 2;")))
    assert(sameSubtree(statement(s1, "3 * 4;"), statement(s0, "3 * 4;")))
    assert(!sameSubtree(statement(s1, "ef"), statement(s0, "ef")))
    assert(s1.evaluations < s0.evaluations)
  }

  test("property: rule changes interleaved with edits stay equal to full parses") {
    val random = new Random(11)
    var s = ParseSession.start(g1, lexicon, "ab + cd; 1 + 2; ef; (3)")
    var current = g1
    (1 to 100).foreach { i =>
      if (i % 10 == 0) {
        current = if (current eq g1) g2 else g1
        s = s.changeRules(current, Set("words"))
      } else {
        val from = random.nextInt(s.text.length + 1)
        s = s.edit(Edit(from, from, List("a", "+", "1", " ", ";")(random.nextInt(5))))
      }
      assertEquals(normal(s.result), full(current, s.text), s"step $i on: ${s.text}")
    }
  }
}
