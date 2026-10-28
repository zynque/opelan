package opelan.foundation.langdef.syntax

import munit.FunSuite
import opelan.foundation.langdef._
import opelan.foundation.means._
import opelan.foundation.means.text._

class DeriveVocabularySuite extends FunSuite {

  private def rules(lines: String*): RuleSet =
    RuleSet(lines.map(readRuleOrHole).toList)

  private def names(terms: List[NodeTerm]): List[(String, NodeShape)] =
    terms.map(t => (t.name, t.shape))

  test("every construct meaning declares a term, in rule order") {
    val set = rules(
      "(a) means a",
      "a * b means mul(a, b)",
      "a + b means add(a, b)")
    assertEquals(names(vocabularyFrom(set)), List(
      "mul" -> NodeShape.Fixed(2),
      "add" -> NodeShape.Fixed(2)))
  }

  test("non-construct meanings declare nothing") {
    val set = rules(
      "(a) means a",
      "a % means a / 100",
      "if c then a else b means c",
      "a means source(a)")
    assertEquals(vocabularyFrom(set), Nil)
  }

  test("a builtin's name operands declare nothing") {
    val set = RuleSet(List(Rule(Pattern(List(Piece.Placeholder("a"))),
      Meaning("builtin", List(Meaning.nameTerm("run"))))))
    assertEquals(vocabularyFrom(set), Nil)
  }

  test("nested constructs declare their own terms") {
    val set = rules("a ~ b means wrap(pair(a, b))")
    assertEquals(names(vocabularyFrom(set)), List(
      "wrap" -> NodeShape.Fixed(1),
      "pair" -> NodeShape.Fixed(2)))
  }

  test("a repeated tag keeps its first declaration") {
    val set = rules(
      "a , b means pair(a, b)",
      "a .. b means pair(a, b)")
    assertEquals(vocabularyFrom(set).length, 1)
  }

  test("a hole rule declares nothing and breaks nothing") {
    val set = rules(
      "a + b means add(a, b)",
      "means only")
    assertEquals(names(vocabularyFrom(set)),
      List("add" -> NodeShape.Fixed(2)))
  }
}
