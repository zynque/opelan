package opelan.examples

import opelan.foundation.document._
import opelan.foundation.language.{HoleRetry, TextSyntax}
import opelan.foundation.langdef.rememberCellHoles
import munit.FunSuite

// Holes retried on a language change: only an attempt that consulted a
// changed rule reparses; the rest are reused as they failed.
class HoleRetrySuite extends FunSuite {

  private val staged = languageFromDef(demoExprDef).textSyntax.get
  private val withDiv = languageFromDef(exprDef).textSyntax.get

  test("a hole that consulted the changed rule heals") {
    val (c0, r0) = HoleRetry().parse(staged, "ref:3 / ref:4")
    assert(c0.children.isEmpty && c0.data.isInstanceOf[NodeData.GapData], c0.toString)
    assert(r0.attempts("ref:3 / ref:4").calls.contains("expr"))
    val (c1, _) = r0.parse(withDiv, "ref:3 / ref:4")
    assertEquals(Some(c1), languageFromDef(exprDef).parse("ref:3 / ref:4"))
  }

  test("same syntax reuses the attempt") {
    val (_, r0) = HoleRetry().parse(staged, "1 +")
    val (_, r1) = r0.parse(languageFromDef(demoExprDef).textSyntax.get, "1 +")
    assert(r1 eq r0)
  }

  test("a hole that consulted no changed rule is reused, not reparsed") {
    val (_, r0) = HoleRetry().parse(staged, "ref:3 / ref:4")
    // withDiv's parser would heal it; the change claims to touch only an
    // unconsulted rule, so the failed attempt stands.
    val unrelated = TextSyntax(withDiv.parser, withDiv.lexicon, "other",
      changedSince = _ => Some(Set("unconsulted")))
    val (c1, r1) = r0.parse(unrelated, "ref:3 / ref:4")
    assert(c1.data.isInstanceOf[NodeData.GapData], c1.toString)
    assertEquals(r1.attempts("ref:3 / ref:4").syntax.key, "other")
  }

  test("an undescribable change reparses") {
    val (_, r0) = HoleRetry().parse(staged, "ref:3 / ref:4")
    val opaque = TextSyntax(withDiv.parser, withDiv.lexicon, "opaque")
    val (c1, _) = r0.parse(opaque, "ref:3 / ref:4")
    assert(!c1.data.isInstanceOf[NodeData.GapData], c1.toString)
  }

  test("remembered holes follow the document's hole texts") {
    val doc = Document(0, Vector(
      Node(0, NodeData.StringData("s"), List(1, 2), None),
      Node(0, NodeData.GapData("ref:3 / ref:4"), Nil, Some(0)),
      Node(0, NodeData.StringData("x"), Nil, Some(0))))
    val stale = HoleRetry().parse(staged, "1 +")._2
    val r = rememberCellHoles(doc, Some(staged), stale)
    assertEquals(r.attempts.keySet, Set("ref:3 / ref:4"))
  }
}
