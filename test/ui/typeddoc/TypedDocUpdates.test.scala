package opelan.ui.typeddoc

import opelan.examples.expr.{exprDoc, exprTypeRef}
import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.typed._
import opelan.foundation.langdef.doc.langDefDoc
import opelan.ui.editor.{DocPush, EditorInput, SyncedEntry, SyncedHistory}

class TypedDocUpdatesSuite extends munit.FunSuite {

  test("SyncHistory pushes the document and shared history into the editor") {
    val d: Document[NodeData] = beginDocument(NodeData.StringData("remote"))
    val h = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "amber-fox", d)), Vector("h1"))
    val u = typedDocUpdate(init, TypedDocInput.SyncHistory(d, h))
    assertEquals(u.state.doc, d)
    assertEquals(u.state.pushMode, DocPush.Synced(h))
    assertEquals(
      editorInput(u.state),
      EditorInput.SyncDocument(d, None, DocPush.Synced(h), u.state.store))
    // no DocChanged echo — the push came from the session owner
    assert(u.out.forall(o => !o.isInstanceOf[TypedDocOutput.DocChanged]))
  }

  // A store holding two versions of the pane's language: the doc's
  // pins all target v0 while v1 is head.
  private def bumpedStore: Store =
    Store.empty
      .put(exprTypeRef.documentUrl, exprDoc)._1
      .put(exprTypeRef.documentUrl,
        langDefDoc(opelan.examples.expr.decodedExprDef.copy(name = "v2")))._1

  test("AcceptUpdates bumps the doc's pinned refs to their heads") {
    val m = init.copy(store = bumpedStore)
    val u = typedDocUpdate(m, TypedDocInput.AcceptUpdates)
    assertEquals(
      typeRefOf(u.state.doc).map(_.documentVersionId), Some(1))
    assert(u.out.exists {
      case TypedDocOutput.DocChanged(d) =>
        typeRefOf(d).map(_.documentVersionId).contains(1)
      case _ => false
    })
    assertEquals(pendingUpdates(u.state.doc, u.state.store), Nil)
  }

  test("AcceptUpdates reports when nothing is pending") {
    val u = typedDocUpdate(init, TypedDocInput.AcceptUpdates)
    assertEquals(u.out, Vector(TypedDocOutput.Status("No pending updates")))
  }

  test("ToggleAutoUpdate writes and removes the flag") {
    val u1 = typedDocUpdate(init, TypedDocInput.ToggleAutoUpdate)
    assert(opelan.foundation.langdef.autoUpdateEnabled(u1.state.doc))
    val u2 = typedDocUpdate(u1.state, TypedDocInput.ToggleAutoUpdate)
    assert(!opelan.foundation.langdef.autoUpdateEnabled(u2.state.doc))
  }

  test("an auto-updating doc accepts pending pins without the click") {
    val flagged =
      opelan.foundation.langdef.setAutoUpdate(init.doc, true).toOption.get
    val m = init.copy(doc = flagged, store = bumpedStore)
    // Any input — a view switch suffices — triggers the recorded accept.
    val u = typedDocUpdate(
      m, TypedDocInput.SwitchView(DocView.Derived("eval")))
    assertEquals(
      typeRefOf(u.state.doc).map(_.documentVersionId), Some(1))
  }
}
