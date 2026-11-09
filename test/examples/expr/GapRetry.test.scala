package opelan.examples.expr

import opelan.examples.languageFromDef
import opelan.foundation.document._
import opelan.foundation.grammar.RuleName
import opelan.foundation.language.{GapRetry, SyntaxChange, TextSyntax}
import opelan.foundation.langdef.rememberInputGaps
import munit.FunSuite

// Gaps retried on a language change: only an attempt that consulted a
// changed rule reparses; the rest are reused as they failed.
class GapRetrySuite extends FunSuite {

  private val staged = languageFromDef(demoExprDef).textSyntax.get
  private val withDiv = languageFromDef(exprDef).textSyntax.get

  test("a gap that consulted the changed rule heals") {
    val (c0, r0) = GapRetry().parse(staged, "ref:3 / ref:4")
    assert(c0.children.isEmpty && c0.data.isInstanceOf[NodeData.GapData], c0.toString)
    assert(r0.attempts("ref:3 / ref:4").calls.contains("expr"))
    val (c1, _) = r0.parse(withDiv, "ref:3 / ref:4")
    assertEquals(Some(c1), languageFromDef(exprDef).parse("ref:3 / ref:4"))
  }

  test("same syntax reuses the attempt") {
    val (_, r0) = GapRetry().parse(staged, "1 +")
    val (_, r1) = r0.parse(languageFromDef(demoExprDef).textSyntax.get, "1 +")
    assert(r1 eq r0)
  }

  test("a gap that consulted no changed rule is reused, not reparsed") {
    val (_, r0) = GapRetry().parse(staged, "ref:3 / ref:4")
    // withDiv's parser would heal it; the change claims to touch only an
    // unconsulted rule, so the failed attempt stands.
    val unrelated = TextSyntax(withDiv.parser, withDiv.lexicon, "other",
      changedSince = _ => Some(SyntaxChange(Set("unconsulted"))))
    val (c1, r1) = r0.parse(unrelated, "ref:3 / ref:4")
    assert(c1.data.isInstanceOf[NodeData.GapData], c1.toString)
    assertEquals(r1.attempts("ref:3 / ref:4").syntax.key, "other")
  }

  test("an undescribable change reparses") {
    val (_, r0) = GapRetry().parse(staged, "ref:3 / ref:4")
    val opaque = TextSyntax(withDiv.parser, withDiv.lexicon, "opaque")
    val (c1, _) = r0.parse(opaque, "ref:3 / ref:4")
    assert(!c1.data.isInstanceOf[NodeData.GapData], c1.toString)
  }

  test("a reused attempt migrates the content the change moved") {
    val (_, r0) = GapRetry().parse(staged, "ref:3 / ref:4")
    val migrate: NodeData => NodeData = {
      case NodeData.GapData(t) => NodeData.GapData(s"$t!")
      case d                   => d
    }
    val bumped = TextSyntax(withDiv.parser, withDiv.lexicon, "bumped",
      changedSince = _ => Some(SyntaxChange(Set.empty, migrate)))
    val (c1, r1) = r0.parse(bumped, "ref:3 / ref:4")
    assertEquals(c1.data, NodeData.GapData("ref:3 / ref:4!"))
    assertEquals(r1.attempts("ref:3 / ref:4").content, c1)
  }

  test("a def version bump re-pins term tags without touching rules") {
    import opelan.foundation.langdef.doc.{langDefDoc, langDefFromDocument}
    import opelan.foundation.langdef.{NodeShape, NodeTerm}
    import opelan.foundation.means.text.readRuleOrGap
    // `pair` is a locally-declared construct — the arithmetic terms are
    // foundational references that never re-pin — so the bump's re-pin
    // shows on the local term.
    val localRules = exprRules.copy(rules =
      exprRules.rules :+ readRuleOrGap("pair(a, b) means pair(a, b)"))
    val local = exprDef.copy(
      vocabulary = exprDef.vocabulary :+
        NodeTerm("pair", NodeShape.Fixed(2)),
      syntax = exprDef.syntax.map(s => s.copy(rules = Some(localRules))))
    val doc = langDefDoc(local)
    val url = exprDef.typeRef.documentUrl
    val s0 = languageFromDef(langDefFromDocument(url, doc, 0)).textSyntax.get
    val s1 = languageFromDef(langDefFromDocument(url, doc, 1)).textSyntax.get
    val ch = s1.changedSince(s0).get
    assertEquals(ch.rules, Set.empty[RuleName])
    // Content parsed under v0 re-pins its construct refs on reuse.
    val (c0, r0) = GapRetry().parse(s0, "pair(1, 2)")
    val (c1, _) = r0.parse(s1, "pair(1, 2)")
    assertNotEquals(c1, c0)
    assertEquals(Some(c1),
      languageFromDef(langDefFromDocument(url, doc, 1)).parse("pair(1, 2)"))
  }

  test("remembered gaps follow the document's gap texts") {
    val doc = Document(0, Vector(
      Node(0, NodeData.StringData("s"), List(1, 2), None),
      Node(0, NodeData.GapData("ref:3 / ref:4"), Nil, Some(0)),
      Node(0, NodeData.StringData("x"), Nil, Some(0))))
    val stale = GapRetry().parse(staged, "1 +")._2
    val r = rememberInputGaps(doc, Some(staged), stale)
    assertEquals(r.attempts.keySet, Set("ref:3 / ref:4"))
  }
}
