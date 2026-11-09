package opelan.examples.expr

import opelan.examples.languageFromDef
import scala.util.Random
import opelan.foundation.grammar._
import munit.FunSuite

// The interpreted expression language under incremental parsing: random
// edits, and a language change to its rules, always agree with a full
// parse — recovery (gaps, unrecognized runs) included.
class IncrementalParseSuite extends FunSuite {

  private def normal(r: ParseResult) = (r.position, r.applied, r.examined, r.tree)
  // A marking table so the oracle's trees carry the same ParseMarks a
  // session's do — marks are deterministic bookkeeping, not content.
  private def full(g: Parser, text: String) =
    normal(g(ParseInput(text, exprLexicon, MemoTable(marking = true)), 0))

  test("property: random edits to expressions match full reparses") {
    val grammar = languageFromDef(exprDef).textSyntax.get.parser
    val random = new Random(3)
    val alphabet = "0123456789+-*/(). ?xref:"
    var s = ParseSession.start(grammar, exprLexicon, "(1 + 2) * 3 - ref:4 / 5")
    (1 to 500).foreach { i =>
      val from = random.nextInt(s.text.length + 1)
      val to = from + random.nextInt((s.text.length - from).min(4) + 1)
      val inserted = List.fill(random.nextInt(4))(alphabet(random.nextInt(alphabet.length))).mkString
      s = s.edit(Edit(from, to, inserted))
      assertEquals(normal(s.result), full(grammar, s.text), s"edit $i on: '${s.text}'")
    }
  }

  test("a rules change carries the session — a means entry drops with its caller") {
    // Under the staged rules, `(7 / 7)` doesn't fit: its `(a)` sequence
    // result is memoized. Adding the `/` rule marks only `expr` changed,
    // but `(a)`'s placeholder recursed through `expr`, so its entry
    // falls with it — no stale result survives.
    val staged = languageFromDef(demoExprDef).textSyntax.get
    val withDiv = languageFromDef(exprDef).textSyntax.get
    val names = withDiv.changedSince(staged).map(_.rules).get
    assertEquals(names, Set(RuleName("expr")))
    val s0 = ParseSession.start(staged.parser, exprLexicon, "(7 / 7)")
    val s1 = s0.changeRules(withDiv.parser, names)
    assertEquals(normal(s1.result), full(withDiv.parser, s1.text))
  }
}
