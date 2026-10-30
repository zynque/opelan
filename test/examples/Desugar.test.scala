package opelan.examples

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means._
import opelan.foundation.means.engine._
import opelan.foundation.means.text._
import Detached._

// The phrase desugar (foundation/means/Desugar.scala, DESIGN §4
// *Phrases desugar at entry*): a phrase meaning parses through the
// language's own rules when it is read, becoming term trees —
// `use` placeholders, construct terms by ref, `quote`d concrete
// content — and evaluation only ever sees terms. A phrase the set
// can't yet read stays a quoted hole until `rephraseRuleSet` heals it.
class DesugarSuite extends munit.FunSuite {

  private val percent = readRuleOrHole("a % means a / 100")

  private def desugared(set: RuleSet): RuleSet =
    rephraseRuleSet(set, ruleAtom, exprTag, "expr", exprLexicon,
      exprLexicals)

  private def grammarFor(set: RuleSet): Parser =
    phrase(compileRules(set, ruleAtom, exprTag, "expr",
      exprLexicals), Set.empty)

  private def parse(g: Parser, input: String): PTree =
    g(In(input, exprLexicon), 0).tree

  private val set = exprRules.copy(rules = exprRules.rules :+ percent)

  test("a context-free read keeps the phrase a quoted hole") {
    // `readRuleOrHole` has no language to desugar through — the
    // phrase keeps its text as a hole for a later re-read to heal.
    assertEquals(percent.meaning.tag, "quote")
    assertEquals(holeQuoteText(percent.meaning), Some("a / 100"))
  }

  test("a set without hole phrases rephrases to itself") {
    assertEquals(desugared(exprRules), exprRules)
  }

  test("a phrase meaning becomes construct/use/quote terms") {
    val meaning = desugared(set).rules.last.meaning
    assertEquals(meaning.tag, "div")
    assertEquals(meaning.reference, Some(exprTerms("div")))
    assertEquals(meaning.arguments.map(_.tag), List("use", "quote"))
    assertEquals(
      meaning.arguments(0).arguments.flatMap(Meaning.nameOf), List("a"))
    assertEquals(
      meaning.arguments(1).quoted.map(_.data), Some(NodeData.IntData(100)))
  }

  test("a phrase may use a rule the set lists later") {
    val reordered = exprRules.copy(rules = percent :: exprRules.rules)
    assertEquals(desugared(reordered).rules.head.meaning.tag, "div")
  }

  test("the desugared meaning splices the bound tree itself") {
    val g = grammarFor(desugared(set))
    val tree = parse(g, "5 %")
    assertEquals(toDetached(tree),
      n(exprTag("div"), il(5), il(100)))
    // The `5` is the bound node — its real source span, not a
    // reparsed copy's synthetic one.
    assertEquals(tree.kids(0).text, "5")
    assertEquals((tree.kids(0).from, tree.kids(0).length), (0, 1))
    val grown = parse(g, "(2 + 3) %")
    assertEquals(toDetached(grown),
      n(exprTag("div"), n(exprTag("add"), il(2), il(3)), il(100)))
  }

  test("an unbound placeholder lands as a hole inside the terms") {
    // `b` isn't in the pattern — the placeholder atom can't lex it,
    // so it parses as an unrecognized run and quotes as a hole inside
    // the structure (`div(hole, 100)`), not over the whole phrase.
    val unbound = readRuleOrHole("a % means b / 100")
    val meaning = desugared(
      exprRules.copy(rules = exprRules.rules :+ unbound))
      .rules.last.meaning
    assertEquals(meaning.tag, "div")
    assert(hasGap(meaning))
  }

  test("a hole in the phrase quotes the hole") {
    val s = exprRules.copy(rules =
      exprRules.rules :+ readRuleOrHole("a % means a +"))
    assertEquals(desugared(s).rules.last.meaning.tag, "add")
    val tree = parse(grammarFor(desugared(s)), "5 %")
    assertEquals(toDetached(tree),
      n(exprTag("add"), il(5), DetachedNode.leaf(NodeData.GapData(""))))
  }
}
