package opelan.foundation.means.engine

import opelan.foundation.grammar._
import opelan.foundation.means._
import opelan.foundation.means.text.readRuleOrGap
import munit.FunSuite

// The derived token layer (LiteralLexicon.scala): a rule's literal
// displays are the lexicon's literal tokens — `a // b` needs no
// pre-configured marker table — longest-first, reserved over the
// authored leaves, and infix literals carry gap-start eligibility.
class LiteralLexiconSuite extends FunSuite {

  private val set = RuleSet(List(
    readRuleOrGap("(a) means a"),
    readRuleOrGap("a # b means h1(a, b)"),
    readRuleOrGap("a ## b means h2(a, b)"),
    readRuleOrGap("a mod b means mod(a, b)"),
    readRuleOrGap("# t means h1(t)"),
    readRuleOrGap("# t means h1(t)")))

  test("literalsOf — first appearance order, distinct") {
    assertEquals(literalsOf(set),
      List("(", ")", "#", "##", "mod"))
  }

  test("literalRules — longest first; the kind is the literal") {
    val rules = literalRules(literalsOf(set))
    assertEquals(rules.map(_._1), List("mod", "##", "(", ")", "#"))
    assert(rules.forall { case (k, _) => k.nonEmpty })
  }

  test("a word-ending literal holds its right boundary") {
    val rule = literalRule("mod")
    assert(rule("x mod y", 2).end == 5)
    assert(rule("mod", 0).end == 3)
    assertEquals(rule("modulo", 0).end, -1)
    assertEquals(rule("modx", 0).end, -1)
  }

  test("a symbol literal needs no boundary") {
    assert(literalRule("##")("## a", 0).end == 2)
    assert(literalRule("#")("##", 0).end == 1)
  }

  test("withLiterals — derived first; infix literals join gapStart only") {
    val base = Lexicon(List("word" -> runOf(1)(_.isLetter)),
      gapStart = Set("unrecognized"), gapRun = Set("word"))
    val full = withLiterals(set, base)
    // Derived literals precede the authored leaves — reserved.
    assertEquals(full.rules.take(5).map(_._1),
      List("mod", "##", "(", ")", "#"))
    // `#`/`##`/`mod` are infix (`a # b`, `a mod b`) — gap starters;
    // `(`/`)` are edges, not.
    assertEquals(full.gapStart.map(_.id),
      Set("#", "##", "mod", "unrecognized"))
    assertEquals(full.gapRun.map(_.id), Set("word"))
  }

  test("a stored rule's new literal tokenizes — `a // b` needs no lexicon entry") {
    val slashed = RuleSet(List(readRuleOrGap("a // b means idiv(a, b)")))
    val full = withLiterals(slashed, Lexicon(List(
      "int" -> runOf(1)(_.isDigit))))
    val in = ParseInput("1 // 2", full)
    assertEquals(scanAt(in, 1).token.map(t => (t.kind.id, t.text)),
      Some(("//", "//")))
  }
}
