package opelan.examples.expr

import opelan.foundation.langdef.syntax.lexicalTerms
import opelan.foundation.means.text.ruleSetIssues

// The expression rule set the def'd language compiles
// (examples/ExprRules) must be well formed — the issues the engine
// reports stay empty.
class ExprRulesSuite extends munit.FunSuite {

  test("the expression rule set is well formed") {
    assertEquals(ruleSetIssues(exprRules, lexicalTerms(exprImpls).keySet), Nil)
  }
}
