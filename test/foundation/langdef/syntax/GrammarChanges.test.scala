package opelan.foundation.langdef.syntax

import munit.FunSuite
import opelan.foundation.document.NodeData
import opelan.foundation.grammar._
import opelan.foundation.langdef._
import opelan.foundation.means._
import opelan.foundation.means.engine._
import opelan.foundation.means.text._

// Language-change diffing over rule sets: memo names are rule
// identities — the `rule` node's id once rules live in `lang:`
// documents — so a rename invalidates nothing and only real changes
// drop parse results (DESIGN §4, *Incremental parsing*).
class GrammarChangesSuite extends FunSuite {

  private def syntax(set: RuleSet): SyntaxDef =
    SyntaxDef("expr", GrammarDef.Hole, Some(set))
  private def rule(text: String, id: Int): Rule =
    readRule(text).fold(e => fail(e), identity).copy(id = Some(id))
  private def codeRule(text: String): Rule =
    readRule(text).fold(e => fail(e), identity)

  test("identical sets report nothing") {
    val set = RuleSet(List(
      rule("(a) means a", 3), rule("a + b means add(a, b)", 5)))
    assertEquals(syntaxChanges(syntax(set), syntax(set)), Set.empty[String])
  }

  test("a placeholder rename is no change — node ids keep the memo key") {
    val before = RuleSet(List(
      rule("(a) means a", 3),
      rule("a + b means add(a, b)", 5),
      rule("a - b means sub(a, b)", 7)))
    val after = RuleSet(List(
      rule("(x) means x", 3),
      rule("x + y means add(x, y)", 5),
      rule("x - y means sub(x, y)", 7)))
    assertEquals(
      syntaxChanges(syntax(before), syntax(after)), Set.empty[String])
  }

  test("a consistent rename — rule and binding both — changes nothing") {
    val before = RuleSet(
      rules = List(rule("a + b means add(a, b)", 5)),
      tiers = List(Tier(List(readPattern("a + b")))))
    val after = RuleSet(
      rules = List(rule("x + y means add(x, y)", 5)),
      tiers = List(Tier(List(readPattern("x + y")))))
    assertEquals(
      syntaxChanges(syntax(before), syntax(after)), Set.empty[String])
  }

  test("a rename the binding list doesn't follow is a real change") {
    // The rule renamed; the tier text still says `a + b` — the rule
    // resolves to nothing and falls loosest: the parser changed.
    val before = RuleSet(
      rules = List(rule("a + b means add(a, b)", 5)),
      tiers = List(Tier(List(readPattern("a + b")))))
    val after = RuleSet(
      rules = List(rule("x + y means add(x, y)", 5)),
      tiers = List(Tier(List(readPattern("a + b")))))
    assertEquals(
      syntaxChanges(syntax(before), syntax(after)), Set("expr"))
  }

  test("a changed sequence rule reports its node-id memo name") {
    val before = RuleSet(List(rule("(a) means a", 3)))
    val after = RuleSet(List(rule("[a] means a", 3)))
    assertEquals(
      syntaxChanges(syntax(before), syntax(after)),
      Set("expr", "means:#3"))
  }

  test("a swapped use binding is a real change") {
    val before = RuleSet(List(rule("pick a and b means pair(a, b)", 3)))
    val after = RuleSet(List(rule("pick a and b means pair(b, a)", 3)))
    assertEquals(
      syntaxChanges(syntax(before), syntax(after)),
      Set("expr", "means:#3"))
  }

  test("added and removed rules name themselves") {
    val before = RuleSet(List(rule("(a) means a", 3)))
    val both = RuleSet(List(rule("(a) means a", 3), rule("|a| means a", 7)))
    assertEquals(
      syntaxChanges(syntax(before), syntax(both)),
      Set("expr", "means:#7"))
    assertEquals(
      syntaxChanges(syntax(both), syntax(before)),
      Set("expr", "means:#7"))
  }

  test("a re-created rule — new node, identical content — reports both names") {
    // Entries keyed under the old name are dead weight and their
    // callers must drop, so the name change itself is reported.
    val before = RuleSet(List(rule("(a) means a", 3)))
    val after = RuleSet(List(rule("(a) means a", 9)))
    assertEquals(
      syntaxChanges(syntax(before), syntax(after)),
      Set("expr", "means:#3", "means:#9"))
  }

  test("code-written rules name by pattern key") {
    val before = RuleSet(List(codeRule("(a) means a")))
    val after = RuleSet(List(codeRule("[a] means a")))
    assertEquals(
      syntaxChanges(syntax(before), syntax(after)),
      Set("expr", "means:'( a ')", "means:'[ a ']"))
  }

  test("a code-side placeholder rename can't pair — both spellings report") {
    val before = RuleSet(List(codeRule("(a) means a")))
    val after = RuleSet(List(codeRule("(x) means x")))
    assertEquals(
      syntaxChanges(syntax(before), syntax(after)),
      Set("expr", "means:'( a ')", "means:'( x ')"))
  }

  test("a doc rule memoizes under its node-id name") {
    val lexicon = Lexicon(List(
      "int"  -> runOf(1)(_.isDigit),
      "lpar" -> exactly("("),
      "rpar" -> exactly(")")))
    val set = RuleSet(List(rule("(a) means a", 3)))
    val s = ParseSession.start(
      compileRules(set, holeHere, NodeData.StringData(_), "e"),
      lexicon, "(5)")
    assert(s.consulted.contains("means:#3"), s.consulted.toString)
  }
}
