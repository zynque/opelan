package opelan.examples

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means.printWith

// The `means`-compiled expression grammar (examples/ExprRules) must
// produce exactly the hand-written grammar's parse — trees and spans
// — across the parser battery. This is the parity anchor for the
// rule engine (STATUS.md, `means` Phase B).
class ExprRulesSuite extends munit.FunSuite {

  private val inputs = List(
    "42", "1.5", "0.25", "ref:7", "12.", "?", "hello", "", "   ",
    "1 + 2 - 3", "1+2", "2 + 3 * 4", "2 * 3 + 4", "8 / 2 - 1",
    "1 + 2 * 3 - 4 / 2", "8 / 4 / 2",
    "-7", "1 - -2", "-1.5", "+3", "- 4", "+ 4", "1 - - 2",
    "(1 + 2)", "1 + (2 - 3)", "((1))", "((1 + 2) * 3 - 4) / 5",
    "(1)", "1 + ((2))",
    "1 +", "1 + abc", "1 + 2 3", "1 + 2 )",
    "(1 2)", "(1 2) + 3", "(1 +", "1 + (", "()", "(?)", "(?",
    "(()", "(1))", "(1 + 2 3)", "((x")

  test("the means-compiled grammar matches the hand-written grammar") {
    inputs.foreach { input =>
      val expected = exprGrammar(In(input, exprLexicon), 0)
      val actual = exprRuleGrammar(In(input, exprLexicon), 0)
      assertEquals(toDetached(actual.tree), toDetached(expected.tree),
        s"tree for '$input'")
      assertEquals(spansOf(actual.tree), spansOf(expected.tree),
        s"spans for '$input'")
    }
  }

  test("the expression rule set is well formed") {
    import opelan.foundation.means.ruleSetIssues
    assertEquals(ruleSetIssues(exprRules), Nil)
  }

  // The leaf half of the derived printer — what the atom's tokens
  // print as, mirroring Expr.print's leaf cases.
  private val exprLeaf: NodeData => String = {
    case NodeData.IntData(v)          => v.toString
    case NodeData.FloatData(f)        => f.toString
    case NodeData.InternalNodeRef(id) => s"ref:$id"
    case NodeData.GapData(t)          => if (t.isEmpty) "?" else t
    case other                        => holeText(other)
  }

  test("the rule-derived printer matches Expr.print") {
    val print = printWith(exprRules, exprTag, exprLeaf)
    inputs.foreach { input =>
      val tree = toDetached(
        exprRuleGrammar(In(input, exprLexicon), 0).tree)
      assertEquals(print(tree), Expr.print(exprFromDetached(tree)),
        s"print of '$input'")
    }
  }
}
