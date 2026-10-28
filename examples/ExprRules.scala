package opelan.examples

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.langdef.syntax.{leafTermNames, lexicalTerms}
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
    "a - b means sub(a, b)",
    "+n means literal(n) where n: number",
    "-n means literal(neg(n)) where n: number").map(readRuleOrHole),
  tiers = List(
    Tier(List(readPattern("a * b"), readPattern("a / b"))),
    Tier(List(readPattern("a + b"), readPattern("a - b")))),
  examples = List(Example("1 + 2 * 3", "1 + (2 * 3)")))

// The operand fallback the rules don't describe — just the
// recovering unrecognized run; the leaf tokens come from the
// vocabulary's leaf terms bound through exprImpls' leaf productions,
// and signed numbers are the `+n`/`-n` rules. The same fallback
// ExprParse's atom writes, minus `parens`, the leaf toks, and
// `signum`.
lazy val ruleAtom: Parser =
  unrecognized(Set("op", "unrecognized"),
    Set("int", "float", "ref", "unrecognized", "hole"))

// The named leaf vocabulary `where` terms bind through — the
// vocabulary's leaf terms flag which are operands.
lazy val exprLexicals: Map[String, LexicalTerm] =
  lexicalTerms(exprImpls, leafTermNames(exprDef))

// The same wrapper ExprParse gives the hand-written grammar — the
// grammar inside a to-EOF phrase. The `expr` name is the rule the
// parser memoizes under — placeholders recurse through it, so it is
// what a language change names to invalidate the syntax's results.
lazy val exprRuleGrammar: Parser =
  phrase(compileRules(
    desugarRuleSet(exprRules, ruleAtom, exprTag, "expr", exprLexicon,
      exprLexicals),
    ruleAtom, exprTag, "expr", exprLexicals), Set.empty)
