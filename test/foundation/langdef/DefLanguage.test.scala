package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.language.ExprLanguage
import munit.FunSuite

// The regression anchor for the bootstrap: a language described as data
// and interpreted by DefLanguage must be behaviorally identical to the
// hand-written implementation it replaces.
class DefLanguageSuite extends FunSuite {

  private val lang: LanguageDef = exprDef
  private val defed = new DefLanguage(lang)
  private val hand = ExprLanguage

  // Valid, holey, and boundary inputs — the whole recovery contract.
  private val inputs = List(
    "1", "1 + 2", "1 + 2 * 3", "(1 + 2) * 3", "1.5 * 2",
    "ref:3 + 1", "-4 + 5", "- 4.5", "1 - 2 - 3",
    "", "  ", "?", "?oops", "1 +", "1 + )", "1 x", "x 1",
    "1 2", "(1 2)", "(1 +", "(", ")", "5 /", "ref:", "ref:9",
    "a * b", "+ 4", "1 + + 2")

  test("identity: typeRef, name, views, langDoc match") {
    assertEquals(defed.typeRef, hand.typeRef)
    assertEquals(defed.name, hand.name)
    assertEquals(defed.views, hand.views)
    assertEquals(render(defed.langDoc), render(hand.langDoc))
  }

  test("parse agrees on every input") {
    inputs.foreach { t =>
      assertEquals(defed.parse(t), hand.parse(t), s"input: ${"\"" + t + "\""}")
    }
  }

  private def typed(content: DetachedNode[NodeData]) =
    makeTyped(hand.typeRef, NodeData.StringData("t"), content)

  test("print and eval views agree on parsed content") {
    inputs.foreach { t =>
      val doc = typed(hand.parse(t).get)
      assertEquals(defed.render("print", doc), hand.render("print", doc), s"print: $t")
      assertEquals(defed.render("eval", doc), hand.render("eval", doc), s"eval: $t")
    }
  }

  test("sample doc renders identically") {
    val doc = hand.sampleDoc
    assertEquals(defed.render("print", doc), hand.render("print", doc))
    assertEquals(defed.render("eval", doc), hand.render("eval", doc))
  }

  test("edge cases: unknown view, no content, no cell grammar") {
    val empty = makeTyped(hand.typeRef, NodeData.StringData("t"), DetachedNode.leaf(NodeData.IntData(0)))
    assertEquals(defed.render("nope", empty), hand.render("nope", empty))
    assert(defed.parseCell("1").isEmpty)
    assert(defed.parseCell("x").isEmpty)
  }
}
