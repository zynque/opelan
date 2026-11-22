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
  private def full(g: Grammar, text: String) =
    normal(g(text, MemoTable(marking = true)))

  test("property: random edits to expressions match full reparses") {
    val grammar = languageFromDef(decodedExprDef).textSyntax.get.grammar
    val random = new Random(3)
    val alphabet = "0123456789+-*/(). ?xref:"
    var s = ParseSession.start(grammar, "(1 + 2) * 3 - ref:4 / 5")
    (1 to 500).foreach { i =>
      val from = random.nextInt(s.text.length + 1)
      val to = from + random.nextInt((s.text.length - from).min(4) + 1)
      val inserted = List.fill(random.nextInt(4))(alphabet(random.nextInt(alphabet.length))).mkString
      s = s.edit(Edit(from, to, inserted))
      assertEquals(normal(s.result), full(grammar, s.text), s"edit $i on: '${s.text}'")
    }
  }

  test("a rules change carries the session — a means entry drops with its caller") {
    // Under the staged rules, `(7 + 7)` doesn't fit: the number
    // operand rule is missing, so `(a)`'s sequence result is memoized
    // against its failed `standard` call. Restoring the rule changes
    // only `standard` — the derived literal set is untouched — and
    // `(a)`'s entry falls with it: no stale result survives.
    import opelan.foundation.langdef.SyntaxDef
    import opelan.foundation.means.text.readPattern
    val noNumber = decodedExprDef.copy(syntax = Some(SyntaxDef("standard", Some(
      exprReading.rules.copy(rules = exprReading.rules.rules.filterNot(
        _.pattern == readPattern("n")))))))
    val staged = languageFromDef(noNumber).textSyntax.get
    val fullSyntax = languageFromDef(decodedExprDef).textSyntax.get
    val names = fullSyntax.changedSince(staged).map(_.rules).get
    assertEquals(names, Set(RuleName("standard")))
    val s0 = ParseSession.start(staged.grammar, "(7 + 7)")
    val s1 = s0.changeRules(fullSyntax.grammar, names)
    assertEquals(normal(s1.result), full(fullSyntax.grammar, s1.text))
  }
}
