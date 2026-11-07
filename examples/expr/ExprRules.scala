package opelan.examples.expr

import opelan.foundation.means._
import opelan.foundation.means.text._

// The expression language as `means` rules — the definition ExprDef
// encodes, compiled by compileRules (foundation/means).
//
// `readRuleOrGap` keeps a mistyped rule from breaking the set —
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
    "-n means literal(neg(n)) where n: number",
    // The operand declarations — which builtin leaves stand alone.
    // `n means n` is the `operand ::= number` production: the `where`
    // binds the token and `use` passes the leaf through. The gap
    // token needs no rule: `gap` is a foundation operand term.
    "n means n where n: number",
    "r means r where r: ref").map(readRuleOrGap),
  tiers = List(
    Tier(List(readPattern("a * b"), readPattern("a / b"))),
    Tier(List(readPattern("a + b"), readPattern("a - b")))),
  examples = List(Example("1 + 2 * 3", "1 + (2 * 3)")))
