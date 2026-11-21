package opelan.foundation.langdef.syntax

import munit.FunSuite
import opelan.foundation.document.NodeData
import opelan.foundation.langdef.LeafKind._
import opelan.foundation.langdef._
import opelan.foundation.means._
import opelan.foundation.means.text.readRuleOrGap

// vocabularyFrom derives a def's declared terms: leaf terms the
// `where` clauses bind, plus constructs declared by
// construct-application patterns (`square(a)`). Meanings only
// reference — they declare nothing.
class DeriveVocabularySuite extends FunSuite {

  private def rules(lines: String*): RuleSet =
    RuleSet(lines.map(readRuleOrGap).toList)

  private def names(terms: List[NodeTerm]): List[(String, NodeShape)] =
    terms.map(t => (t.name, t.shape))

  test("operator patterns declare their literal; meanings reference") {
    val set = rules(
      "(a) means a",
      "a * b means mul(a, b)",
      "a + b means add(a, b)")
    assertEquals(names(vocabularyFrom(set)), List(
      "*" -> NodeShape.Fixed(2),
      "+" -> NodeShape.Fixed(2)))
  }

  test("a construct-application pattern declares its construct") {
    val set = rules(
      "square(a) means mul(a, a)",
      "pair(a, b) means wrap(a, b)")
    assertEquals(names(vocabularyFrom(set)), List(
      "square" -> NodeShape.Fixed(1),
      "pair"   -> NodeShape.Fixed(2)))
  }

  test("a repeated construct declaration keeps the first") {
    val set = rules(
      "pair(a, b) means wrap(a, b)",
      "pair(a, b) means bundle(a, b)")
    assertEquals(vocabularyFrom(set).length, 1)
  }

  test("a gap rule declares nothing and breaks nothing") {
    val set = rules(
      "square(a) means mul(a, a)",
      "means only")
    assertEquals(names(vocabularyFrom(set)),
      List("square" -> NodeShape.Fixed(1)))
  }

  test("postfix patterns declare; phrase patterns declare nothing") {
    val set = rules(
      "(a) means a",
      "a % means a / 100",
      "if c then a else b means c",
      "a means source(a)")
    assertEquals(vocabularyFrom(set).map(t => (t.name, t.shape)).toSet,
      Set("%" -> NodeShape.Fixed(1)))
  }

  test("where-bound terms join the vocabulary as leaf terms") {
    val set = rules(
      "-n means literal(neg(n)) where n: number",
      "a + b means add(a, b)",
      "ref:c,r means cell(c, r) where c, r: whole number")
    assertEquals(vocabularyFrom(set).map(t => (t.name, t.shape)).toSet,
      Set(
        "number"       -> NodeShape.Leaf,
        "whole number" -> NodeShape.Leaf,
        "-"            -> NodeShape.Fixed(1),
        "+"            -> NodeShape.Fixed(2)))
  }

  test("every impls leaf names a lexical term, bound or not") {
    // Operand status is no longer derived here — which leaves stand
    // alone is authored by lone-placeholder operand rules — so an
    // impls-declared leaf joins the standard vocabulary for `where`
    // to bind (number/ref are standard terms now; `plain` is the
    // impls-only case markdown still wires).
    val impls = Impls(leaves = Map(
      "plain" -> LeafDef(List("text" -> Text))))
    val terms = lexicalTerms(impls)
    assertEquals(terms.keySet, Set("gap", "value", "number",
      "whole number", "int", "float", "literal", "ref", "plain")
      .map(lexicalRefs))
    assert(terms(lexicalRefs("plain")).check(NodeData.StringData("hi")))
    assert(terms(lexicalRefs("number")).check(NodeData.IntData(3)))
    assert(terms(lexicalRefs("number")).check(NodeData.FloatData(1.5)))
    assert(!terms(lexicalRefs("number"))
      .check(NodeData.InternalNodeRef(1)))
  }
}
