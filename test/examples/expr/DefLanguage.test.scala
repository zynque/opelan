package opelan.examples.expr

import opelan.examples.{exprSampleDoc, implsFor, languageFromDef}
import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc._
import opelan.foundation.means.text.readRuleOrGap
import munit.FunSuite

// A directly-constructed DefLanguage over the seed's decode behaves
// like the resolved interpretation — DefLanguage(decodedExprDef)
// against languageFromDef(decodedExprDef) across the recovery
// contract.
class DefLanguageSuite extends FunSuite {

  private val lang: LanguageDef = decodedExprDef
  private val defed = new DefLanguage(lang, implsFor(lang))
  private val hand = languageFromDef(decodedExprDef)

  // Valid, gappy, and boundary inputs — the whole recovery contract.
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
    makeTyped(hand.typeRef, NodeData.ExternalNodeRef(docTitleRef),
      NodeData.ExternalNodeRef(docImportsRef),
      NodeData.StringData("t"), content)

  test("print and eval views agree on parsed content") {
    inputs.foreach { t =>
      val doc = typed(hand.parse(t).get)
      assertEquals(defed.render("print", doc), hand.render("print", doc), s"print: $t")
      assertEquals(defed.render("eval", doc), hand.render("eval", doc), s"eval: $t")
    }
  }

  test("sample doc renders identically") {
    val doc = exprSampleDoc
    assertEquals(defed.render("print", doc), hand.render("print", doc))
    assertEquals(defed.render("eval", doc), hand.render("eval", doc))
  }

  test("edge cases: unknown view, no content, no input grammar") {
    val empty = makeTyped(hand.typeRef, NodeData.ExternalNodeRef(docTitleRef),
      NodeData.ExternalNodeRef(docImportsRef),
      NodeData.StringData("t"), DetachedNode.leaf(NodeData.IntData(0)))
    assertEquals(defed.render("nope", empty), hand.render("nope", empty))
    assert(defed.parseInput("1").isEmpty)
    assert(defed.parseInput("x").isEmpty)
  }

  test("a def claiming a foundational type ref inherits its impls") {
    // implsFor(decodedExprDef) is empty: a doc-decoded def for the
    // expression type needs no code-side impls — its vocabulary is
    // foundation's.
    val edited = decodedExprDef.copy(title = "edited")
    val l = new DefLanguage(edited, implsFor(edited))
    assert(l.parse("1 + 2").isDefined)
    assert(l.render("print", exprSampleDoc).isRight)
  }

  test("a stored def's new literal tokenizes — `a // b` needs no lexicon entry") {
    // The `a // b` rough edge: the literal needs no pre-configured
    // token table — its rule line in the def document is enough
    // (LiteralLexicon). The decode path proves it end to end.
    val edited = decodedExprDef.copy(syntax = decodedExprDef.syntax.map(s =>
      s.copy(rules = s.rules.map(r => r.copy(rules =
        r.rules :+ readRuleOrGap("a // b means fdiv(a, b)"))))))
    val doc = langDefDoc(edited)
    val l = new DefLanguage(
      langDefFromDocument("opelan:languages/expr", doc),
      implsFor(edited))
    val parsed = l.parse("1 // 2")
    assert(parsed.isDefined)
    def hasGap(t: DetachedNode[NodeData]): Boolean =
      t.data.isInstanceOf[NodeData.GapData] || t.children.exists(hasGap)
    assert(!hasGap(parsed.get))
    assertEquals(parsed.get.children.length, 2)
  }

  test("the standard leaf vocabulary needs no impls") {
    // The expression def binds no impls: the "standard" lexicon and
    // the `number`/`ref` leaves resolve foundation-side — even a def
    // claiming an unknown type ref parses the shared vocabulary.
    val foreign = decodedExprDef.copy(
      typeRef = ExternalNodeReference("opelan:languages/unknown", 0, 0))
    assert(new DefLanguage(foreign, implsFor(foreign))
      .parse("1 + 2").isDefined)
  }

  test("a def naming an unknown lexicon binds no grammar") {
    val foreign = decodedExprDef.copy(
      typeRef = ExternalNodeReference("opelan:languages/unknown", 0, 0),
      syntax = decodedExprDef.syntax.map(_.copy(lexicon = "foreign")))
    val l = new DefLanguage(foreign, implsFor(foreign))
    assertEquals(l.parse("1 + 2"), None)          // "foreign" lexicon unbound
    // The derived views read the def itself, not impls — `print` and
    // `eval` still answer, though no lexicon means no compiled
    // grammar and so no `edit`.
    assertEquals(l.views, List("print", "eval"))
    assert(l.render("print", exprSampleDoc).isRight)
  }
}
