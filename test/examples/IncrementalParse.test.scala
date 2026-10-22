package opelan.examples

import scala.util.Random
import opelan.foundation.grammar._
import opelan.foundation.langdef._
import munit.FunSuite

// The interpreted expression language under incremental parsing: random
// edits, and a language change to its grammar, always agree with a full
// parse — recovery (holes, junk runs) included.
class IncrementalParseSuite extends FunSuite {

  private val language = new DefLanguage(exprCombinatorDef, implsFor(exprCombinatorDef))
  private val grammarDef = exprCombinatorDef.syntax.get.grammar
  private val grammar = interpret(grammarDef, language.kindTag)

  private def normal(r: POut) = (r.pos, r.ok, r.examined, r.tree)
  private def full(g: Parser, text: String) = normal(g(In(text, exprLexicon), 0))

  private def editsMatchFull(grammar: Parser): Unit = {
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

  test("property: random edits to expressions match full reparses") {
    editsMatchFull(grammar)
  }

  test("property: random edits against the rules-compiled grammar match") {
    editsMatchFull(exprRuleGrammar)
  }

  test("a grammar change invalidates by rule and matches a full parse") {
    // Drop "/" from the language: the "expr" rule's definition changes.
    val edited = grammarDef match {
      case GrammarDef.Phrase(GrammarDef.Named(n, p: GrammarDef.Precedence), stops) =>
        GrammarDef.Phrase(GrammarDef.Named(n, p.copy(
          levels = p.levels.map(_ - "/"))), stops)
      case other => fail(s"unexpected grammar shape: $other")
    }
    assertEquals(changedRules(grammarDef, edited), Set("expr"))
    assertEquals(changedRules(grammarDef, grammarDef), Set.empty[String])
    val newGrammar = interpret(edited, language.kindTag)
    val s0 = ParseSession.start(grammar, exprLexicon, "8 / 2 + (3 / 1)")
    val s1 = s0.changeRules(newGrammar, changedRules(grammarDef, edited))
    assertEquals(normal(s1.result), full(newGrammar, s1.text))
  }

  test("a rules change carries the session — a means entry drops with its caller") {
    // Under the staged rules, `(7 / 7)` doesn't fit: its `(a)` sequence
    // result is memoized. Adding the `/` rule marks only `expr` changed,
    // but `(a)`'s placeholder recursed through `expr`, so its entry
    // falls with it — no stale result survives.
    val staged = languageFromDef(demoExprDef).textSyntax.get
    val withDiv = languageFromDef(exprDef).textSyntax.get
    val names = withDiv.changedSince(staged).map(_.rules).get
    assertEquals(names, Set("expr"))
    val s0 = ParseSession.start(staged.parser, exprLexicon, "(7 / 7)")
    val s1 = s0.changeRules(withDiv.parser, names)
    assertEquals(normal(s1.result), full(withDiv.parser, s1.text))
  }
}
