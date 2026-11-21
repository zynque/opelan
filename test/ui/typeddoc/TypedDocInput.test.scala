package opelan.ui.typeddoc

import opelan.examples.expr.{exprDoc, exprTypeRef}
import opelan.examples.termOf
import opelan.examples.sheet.{RefTag, SheetLanguage, sheetTerms}
import opelan.foundation.document._
import opelan.foundation.document.store._

class TypedDocInputSuite extends munit.FunSuite {

  // Sheet docs need both defs: the sheet language itself, and the
  // expression language its cell input grammar references by ref.
  private val seeded = Store.empty
    .put(exprTypeRef.documentUrl, exprDoc)._1
    .put(SheetLanguage.typeRef.documentUrl, SheetLanguage.langDoc)._1

  test("CommitInputText rewrites the node in place and pushes the doc") {
    val m = typedDocUpdate(
      init.copy(store = seeded),
      TypedDocInput.Load(SheetLanguage.sampleDoc)).state
    val price = cellId(m.doc, 1, 2) // Apples' price
    val u = typedDocUpdate(m, TypedDocInput.CommitInputText(price, "1.50"))
    assertEquals(
      u.state.doc.getNode(price).map(_.data), Some(NodeData.FloatData(1.5)))
    assertEquals(u.state.pushedDoc, u.state.doc)
    assert(u.out.contains(TypedDocOutput.DocChanged(u.state.doc)))
  }

  test("an input commit keeps the ids of unchanged subtrees") {
    val m = typedDocUpdate(
      init.copy(store = seeded),
      TypedDocInput.Load(SheetLanguage.sampleDoc)).state
    val total = cellId(m.doc, 1, 3) // Apples' line total = qty * price
    val refs = m.doc.childrenOf(total)
    val u = typedDocUpdate(
      m, TypedDocInput.CommitInputText(total, "B2 + C2"))
    // mul → add changed the node's data, but the ref subtrees ("B2",
    // "C2" name the same cells) kept their ids and the cell itself was
    // never replaced.
    assertEquals(u.state.doc.childrenOf(total), refs)
    assertEquals(
      refs.map(id => termOf(sheetTerms, u.state.doc, id)),
      List(Some(RefTag), Some(RefTag)))
    // A fresh part gets a fresh id; the rest stay put.
    val u2 = typedDocUpdate(
      m, TypedDocInput.CommitInputText(total, "B2 * 3"))
    val after = u2.state.doc.childrenOf(total)
    assertEquals(after(0), refs(0))
    assertNotEquals(after(1), refs(1))
  }

  test("re-committing an input reparses through its session") {
    // Seeded def docs make the sheet a DefLanguage, whose input grammar
    // reads through lang: expression's incremental syntax.
    val m = typedDocUpdate(
      init.copy(store = seeded), TypedDocInput.Load(SheetLanguage.sampleDoc)).state
    val total = cellId(m.doc, 1, 3)
    val Seq(NodeData.InternalNodeRef(qty), _) =
      m.doc.childrenOf(total).map(id => m.doc.getNode(id).get.data).runtimeChecked
    // Parenthesized terms: each is its own memoized rule application.
    val long = (1 to 10).map(i => s"(ref:$qty * $i)").mkString(" + ")
    val u1 = typedDocUpdate(m, TypedDocInput.CommitInputText(total, long))
    assertEquals(u1.state.inputSessions.sessions.keySet, Set(total))
    val edited = long.replace("* 5", "* 6")
    val u2 = typedDocUpdate(u1.state, TypedDocInput.CommitInputText(total, edited))
    val session = u2.state.inputSessions.sessions(total)
    val fresh = opelan.foundation.language.TextSession.start(
      session.syntax, edited)
    assert(session.evaluations * 4 < fresh.evaluations,
      s"${session.evaluations} vs ${fresh.evaluations}")
    assertEquals(
      shapeOf(u2.state.doc, total),
      shapeOf(
        typedDocUpdate(m, TypedDocInput.CommitInputText(total, edited))
          .state.doc, total))
  }

  test("an unchanged CommitInputText is a no-op") {
    val m = typedDocUpdate(
      init.copy(store = seeded),
      TypedDocInput.Load(SheetLanguage.sampleDoc)).state
    val price = cellId(m.doc, 1, 2)
    val u = typedDocUpdate(m, TypedDocInput.CommitInputText(price, "1.2"))
    assertEquals(u.state, m)
    assert(u.out.isEmpty)
  }

  test("CommitInputText on a doc with no input grammar reports it") {
    val u = typedDocUpdate(init, TypedDocInput.CommitInputText(0, "x"))
    assert(u.state.status.contains("input grammar"))
    assert(u.out.exists(_.isInstanceOf[TypedDocOutput.Status]))
  }
}
