package opelan.ui.typeddoc

import opelan.examples.expr.exprDef
import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.langdef.doc.{langDefDoc, metaConsOf}

class TypedDocTextSessionSuite extends munit.FunSuite {

  test("TextEdited reparses through a carried text session") {
    val u1 = typedDocUpdate(init, TypedDocInput.TextEdited("1 + 2"))
    assert(u1.state.textSession.isDefined)
    val u2 = typedDocUpdate(u1.state, TypedDocInput.TextEdited("1 + 2 * 3"))
    assertEquals(u2.state.textSession.map(_.session.text), Some("1 + 2 * 3"))
    // the same content a whole-text parse commits (ids differ — the
    // session's span hints keep more of them)
    val whole = opelan.foundation.language.applyDocText(
      u1.state.doc, langFor(u1.state), "1 + 2 * 3")
      .toOption.get
    assertEquals(
      shapeOf(u2.state.doc, contentId(u2.state.doc).get),
      shapeOf(whole, contentId(whole).get))
  }

  test("TextEdited hints the commit through the session's spans") {
    val u1 = typedDocUpdate(init, TypedDocInput.TextEdited("1 + 2"))
    val cid = contentId(u1.state.doc).get
    val List(one, two) = u1.state.doc.childrenOf(cid)
    val u2 = typedDocUpdate(u1.state, TypedDocInput.TextEdited("1 + 2 * 3"))
    // `2` moved from `add`'s second operand to `mul`'s first — its
    // characters' provenance keeps its node anyway.
    val cid2 = contentId(u2.state.doc).get
    val mul = u2.state.doc.childrenOf(cid2)(1)
    assertEquals(u2.state.doc.childrenOf(cid2).head, one)
    assertEquals(u2.state.doc.childrenOf(mul).head, two)
    assert(u2.state.provenance.isDefined)
  }

  // The session-path counterpart of ResolveSuite's tracked edit: a
  // rule typed in front of `a * b` never steals its definition node —
  // the merged line splits back out with its identity intact.
  test("a means line typed in front keeps the old rule through the session") {
    def definitionIds(doc: Document[NodeData]): Set[Int] =
      contentId(doc).toList.flatMap(cid =>
        doc.subtreeIds(cid).filter(id =>
          metaConsOf(doc, id).contains("definition"))).toSet
    val m = typedDocUpdate(init, TypedDocInput.Load(langDefDoc(exprDef))).state
    val before = definitionIds(m.doc)
    val line = m.text.linesIterator.find(_.contains("a * b means")).get
    val indent = line.takeWhile(_ == ' ')
    val at = m.text.indexOf(line) + indent.length
    val rule = "a // b means div(a, b)\n" + indent
    val end = (1 to rule.length).foldLeft(m) { (state, k) =>
      val t = m.text.substring(0, at) + rule.take(k) +
        m.text.substring(at)
      typedDocUpdate(state, TypedDocInput.TextEdited(t)).state
    }
    val after = definitionIds(end.doc)
    assertEquals(after.size, before.size + 1)
    assert(before.subsetOf(after), s"lost ids: ${before -- after}")
  }

  test("a text edit keeps the ids of unchanged content") {
    val m = init
    // TextEdited goes through applyDocText: recommitting the current
    // text keeps the content node's id (and the whole subtree's).
    val cid = contentId(m.doc).get
    val u = typedDocUpdate(m, TypedDocInput.TextEdited(m.text))
    assertEquals(u.state.doc.childrenOf(u.state.doc.rootId), m.doc.childrenOf(m.doc.rootId))
    assertEquals(u.state.doc.childrenOf(cid), m.doc.childrenOf(cid))
  }
}
