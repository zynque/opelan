package opelan.foundation.langdef

import munit.FunSuite
import opelan.foundation.means._

class DeriveSchemaSuite extends FunSuite {

  private def rules(lines: String*): RuleSet =
    RuleSet(lines.map(readRuleOrHole).toList)

  private def names(kinds: List[NodeKind]): List[(String, NodeShape)] =
    kinds.map(k => (k.name, k.shape))

  test("every construct meaning declares a kind, in rule order") {
    val set = rules(
      "(a) means a",
      "a * b means mul(a, b)",
      "a + b means add(a, b)")
    assertEquals(names(schemaFrom(set)), List(
      "mul" -> NodeShape.Fixed(2),
      "add" -> NodeShape.Fixed(2)))
  }

  test("non-construct meanings declare nothing") {
    val set = rules(
      "(a) means a",
      "a % means a / 100",
      "if c then a else b means c",
      "a means source(a)")
    assertEquals(schemaFrom(set), Nil)
  }

  test("a builtin's name operands declare nothing") {
    val set = RuleSet(List(Rule(Pattern(List(Piece.Placeholder("a"))),
      Meaning("builtin", List(Meaning.nameTerm("run"))))))
    assertEquals(schemaFrom(set), Nil)
  }

  test("nested constructs declare their own kinds") {
    val set = rules("a ~ b means wrap(pair(a, b))")
    assertEquals(names(schemaFrom(set)), List(
      "wrap" -> NodeShape.Fixed(1),
      "pair" -> NodeShape.Fixed(2)))
  }

  test("a repeated tag keeps its first declaration") {
    val set = rules(
      "a , b means pair(a, b)",
      "a .. b means pair(a, b)")
    assertEquals(schemaFrom(set).length, 1)
  }

  test("a hole rule declares nothing and breaks nothing") {
    val set = rules(
      "a + b means add(a, b)",
      "means only")
    assertEquals(names(schemaFrom(set)),
      List("add" -> NodeShape.Fixed(2)))
  }
}
