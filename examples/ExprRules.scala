package opelan.examples

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means._

// The expression language as `means` rules — the same grammar
// ExprParse writes as combinators and ExampleDefs writes as
// GrammarDef data, compiled by compileRules (foundation/means).
// Behavioral parity with both is the regression anchor for the
// rule engine (STATUS.md, `means` Phase B); once parity is proven
// the rules become the definition and the other forms fall away.

// `readRuleOrHole` keeps a mistyped rule from breaking the set —
// it reads as a rule that never applies, and `ruleSetIssues`
// still reports it (`ExprRulesSuite` asserts the set is clean).
val exprRules = RuleSet(
  rules = List(
    "(a) means a",
    "a * b means mul(a, b)",
    "a / b means div(a, b)",
    "a + b means add(a, b)",
    "a - b means sub(a, b)").map(readRuleOrHole),
  tiers = List(
    Tier(List(readPattern("a * b"), readPattern("a / b"))),
    Tier(List(readPattern("a + b"), readPattern("a - b")))),
  examples = List(Example("1 + 2 * 3", "1 + (2 * 3)")))

// The operand syntax rules don't yet describe — leaf tokens, the
// signum, and the recovering junk fallback — until `where`
// constraints bind placeholders to a kind vocabulary. The same
// choice ExprParse writes, minus `parens`: `(a) means a` supplies
// them now.
lazy val ruleAtom: Parser = choice(
  tok("int")(t => t.text.toIntOption.map(NodeData.IntData(_))
    .getOrElse(NodeData.GapData(t.text))),
  tok("float")(t => t.text.toDoubleOption.map(NodeData.FloatData(_))
    .getOrElse(NodeData.GapData(t.text))),
  tok("ref")(t => t.text.drop(4).toIntOption
    .map(NodeData.InternalNodeRef(_)).getOrElse(NodeData.GapData(t.text))),
  tok("hole")(_ => NodeData.GapData("")),
  signum,
  junk(Set("op", "junk"), Set("int", "float", "ref", "junk", "hole")))

// The same wrapper ExprParse gives the hand-written grammar — the
// grammar inside a to-EOF phrase. The `expr` name is the rule the
// parser memoizes under — placeholders recurse through it, so it is
// what a language change names to invalidate the syntax's results.
lazy val exprRuleGrammar: Parser =
  phrase(compileRules(
    desugarRuleSet(exprRules, ruleAtom, exprTag, "expr", exprLexicon),
    ruleAtom, exprTag, "expr"), Set.empty)
