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
    Tier(List(readPattern("a + b"), readPattern("a - b")))))

// The operand syntax rules don't yet describe — leaf tokens, the
// signum, and the recovering junk fallback — until `where`
// constraints bind placeholders to a kind vocabulary. The same
// choice ExprParse writes, minus `parens`: `(a) means a` supplies
// them now.
private lazy val ruleAtom: Parser = choice(
  tok("int")(t => NodeData.IntData(t.text.toInt)),
  tok("float")(t => NodeData.FloatData(t.text.toDouble)),
  tok("ref")(t => NodeData.InternalNodeRef(t.text.drop(4).toInt)),
  tok("hole")(_ => NodeData.GapData("")),
  signum,
  junk(Set("op", "junk"), Set("int", "float", "ref", "junk", "hole")))

// The same wrapper ExprParse gives the hand-written grammar — the
// named rule inside a to-EOF phrase.
lazy val exprRuleGrammar: Parser =
  phrase(rule("expr")(compileRules(exprRules, ruleAtom, exprTag)),
    Set.empty)
