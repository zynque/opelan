package opelan.ui.typeddoc

import opelan.examples.expr.ExprLanguage
import opelan.examples.sheet.{SheetLanguage, sheetDef}
import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.document.Detached._
import opelan.foundation.langdef.doc.langDefDoc
import opelan.ui.editor.{
  DocPush, EditorInput, SelectTarget, SyncedEntry, SyncedHistory}

class TypedDocSuite extends munit.FunSuite {

  test("init loads a typed expression sample") {
    val m = init
    assertEquals(typeRefOf(m.doc), Some(ExprLanguage.typeRef))
    assertEquals(m.view, DocView.Editor)
  }

  test("children mounts the document editor with the pushed document") {
    val m = init
    assertEquals(editorInput(m), EditorInput.SyncDocument(m.pushedDoc))
  }

  test("SwitchView selects a derived view") {
    val u = typedDocUpdate(
      init, TypedDocInput.SwitchView(DocView.Derived("print")))
    assertEquals(u.state.view, DocView.Derived("print"))
  }

  test("Load falls back to the new language's default view") {
    // On a derived view, jump to a doc whose language lacks it — the
    // go-to-definition path: "sheet" doesn't exist on DocLanguage, so
    // the pane lands on its default "edit" view instead of an error.
    val m = typedDocUpdate(
      init, TypedDocInput.SwitchView(DocView.Derived("sheet"))).state
    val u = typedDocUpdate(m, TypedDocInput.Load(langDefDoc(sheetDef)))
    assertEquals(u.state.view, DocView.Derived("edit"))
  }

  test("Load onto an untyped doc leaves a derived view for the editor") {
    val m = typedDocUpdate(
      init, TypedDocInput.SwitchView(DocView.Derived("print"))).state
    val u = typedDocUpdate(
      m, TypedDocInput.Load(beginDocument(NodeData.StringData("raw"))))
    assertEquals(u.state.view, DocView.Editor)
  }

  test("Load keeps a derived view the new language also offers") {
    val m = typedDocUpdate(
      init, TypedDocInput.SwitchView(DocView.Derived("sheet"))).state
    val u = typedDocUpdate(m, TypedDocInput.Load(SheetLanguage.sampleDoc))
    assertEquals(u.state.view, DocView.Derived("sheet"))
  }

  test("Load pushes the new document into the editor") {
    val m = init
    val d: Document[NodeData] = beginDocument(NodeData.StringData("other"))
    val u = typedDocUpdate(m, TypedDocInput.Load(d))
    assertEquals(u.state.doc, d)
    assertEquals(u.state.pushedDoc, d)
    assertEquals(editorInput(u.state), EditorInput.SyncDocument(d))
  }

  test("Load after a synced push resets to a fresh document") {
    val d1: Document[NodeData] = beginDocument(NodeData.StringData("remote"))
    val h = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "amber-fox", d1)), Vector("h1"))
    val m = typedDocUpdate(init, TypedDocInput.SyncHistory(d1, h)).state
    val d2: Document[NodeData] = beginDocument(NodeData.StringData("other"))
    val u = typedDocUpdate(m, TypedDocInput.Load(d2))
    assertEquals(u.state.pushMode, DocPush.Open)
    assertEquals(editorInput(u.state), EditorInput.SyncDocument(d2))
  }

  test("Load with a selection pushes it through to the editor") {
    val m = init
    val d: Document[NodeData] =
      buildDocument(n(s("r"), sl("a")))
    val target = d.childrenOf(d.rootId).head
    val u = typedDocUpdate(m, TypedDocInput.Load(d, Some(target)))
    assertEquals(u.state.pushedSelect, Some(SelectTarget(target)))
    assertEquals(
      editorInput(u.state),
      EditorInput.SyncDocument(d, Some(SelectTarget(target))))
  }
}
