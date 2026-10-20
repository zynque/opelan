package opelan.examples

import scala.util.Random
import opelan.foundation.grammar._
import opelan.foundation.langdef._
import munit.FunSuite

// The interpreted expression language under incremental parsing: random
// edits, and a language change to its grammar, always agree with a full
// parse — recovery (holes, junk runs) included.
class IncrementalParseSuite extends FunSuite {

  private val language = new DefLanguage(exprDef, implsFor(exprDef))
  private val grammarDef = exprDef.syntax.get.grammar
  private val grammar = interpret(grammarDef, language.kindTag)

  private def normal(r: POut) = (r.pos, r.ok, r.examined, r.tree)
  private def full(g: Parser, text: String) = normal(g(In(text, exprLexicon), 0))

  test("property: random edits to expressions match full reparses") {
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
}
