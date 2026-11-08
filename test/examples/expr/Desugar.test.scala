package opelan.examples.expr

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.langdef.syntax.lexicalTerms
import opelan.foundation.means._
import opelan.foundation.means.engine._
import opelan.foundation.means.text._
import Detached._

// The phrase desugar (foundation/means/engine/Desugar.scala,
// foundation/means/DESIGN.md, *Phrases desugar at entry*): a phrase
// meaning parses through the
// language's own rules when it is read, becoming term trees —
// `use` placeholders, construct terms by ref, `quote`d concrete
// content — and evaluation only ever sees terms. A phrase the set
// can't yet read stays a quoted gap until `rephraseRuleSet` heals it.
class DesugarSuite extends munit.FunSuite {

  private val percent = readRuleOrGap("a % means a / 100")
  private val atom =
    unrecognized(exprLexicon.gapStart, exprLexicon.gapRun)
  private val lexicals = lexicalTerms(exprImpls)

  private def desugared(set: RuleSet): RuleSet =
    rephraseRuleSet(set, atom, exprTag, "expr", exprLexicon, lexicals)

  private def grammarFor(set: RuleSet): Parser =
    phrase(compileRules(set, atom, exprTag, "expr",
      lexicals), Set.empty)

  private def parse(g: Parser, input: String): ParseTree =
    g(ParseInput(input, exprLexicon), 0).tree

  private val set = exprRules.copy(rules = exprRules.rules :+ percent)

  test("a context-free read keeps the phrase a quoted gap") {
    // `readRuleOrGap` has no language to desugar through — the
    // phrase keeps its text as a gap for a later re-read to heal.
    assertEquals(percent.meaning.tag, "quote")
    assertEquals(gapQuoteText(percent.meaning), Some("a / 100"))
  }

  test("a set without gap phrases rephrases to itself") {
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
    assertEquals(tree.children(0).text, "5")
    assertEquals((tree.children(0).from, tree.children(0).length), (0, 1))
    val grown = parse(g, "(2 + 3) %")
    assertEquals(toDetached(grown),
      n(exprTag("div"), n(exprTag("add"), il(2), il(3)), il(100)))
  }

  test("an unbound placeholder lands as a gap inside the terms") {
    // `b` isn't in the pattern — the placeholder atom can't lex it,
    // so it parses as an unrecognized run and quotes as a gap inside
    // the structure (`div(gap, 100)`), not over the whole phrase.
    val unbound = readRuleOrGap("a % means b / 100")
    val meaning = desugared(
      exprRules.copy(rules = exprRules.rules :+ unbound))
      .rules.last.meaning
    assertEquals(meaning.tag, "div")
    assert(hasGap(meaning))
  }

  test("a gap in the phrase quotes the gap") {
    val s = exprRules.copy(rules =
      exprRules.rules :+ readRuleOrGap("a % means a +"))
    assertEquals(desugared(s).rules.last.meaning.tag, "add")
    val tree = parse(grammarFor(desugared(s)), "5 %")
    assertEquals(toDetached(tree),
      n(exprTag("add"), il(5), DetachedNode.leaf(NodeData.GapData(""))))
  }
}
