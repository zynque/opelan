package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.foundation.language.ExprLanguage
import opelan.ui.editor.{EditorInput, EditorOutput}

class TypedDocSuite extends munit.FunSuite {

  private def init: TypedDocModel = TypedDoc.init

  private def editorInput(m: TypedDocModel): EditorInput =
    TypedDoc.structure(m).head.input.asInstanceOf[EditorInput]

  test("init loads a typed expression sample") {
    val m = init
    assertEquals(Typed.typeRefOf(m.doc), Some(ExprLanguage.typeRef))
    assertEquals(m.view, DocView.Editor)
  }

  test("structure mounts the document editor with the pushed document") {
    val m = init
    assertEquals(editorInput(m), EditorInput.LoadDocument(m.pushedDoc))
  }

  test("SwitchView selects a derived view") {
    val u = TypedDocUpdate(init, TypedDocInput.SwitchView(DocView.Derived("print")))
    assertEquals(u.state.view, DocView.Derived("print"))
  }

  test("editor edits update the mirrored doc but are not pushed back") {
    val m = init
    val edited: Document[NodeData] = Build.beginDocument(NodeData.IntData(1))
    val u = TypedDocUpdate(m, TypedDocInput.FromEditor(EditorOutput.DocChanged(edited)))
    assertEquals(u.state.doc, edited)
    assertEquals(u.state.pushedDoc, m.pushedDoc)
    assertEquals(editorInput(u.state), EditorInput.LoadDocument(m.pushedDoc))
    assert(u.out.contains(TypedDocOutput.DocChanged(edited)))
  }

  test("Load pushes the new document into the editor") {
    val m = init
    val d: Document[NodeData] = Build.beginDocument(NodeData.StringData("other"))
    val u = TypedDocUpdate(m, TypedDocInput.Load(d))
    assertEquals(u.state.doc, d)
    assertEquals(u.state.pushedDoc, d)
    assertEquals(editorInput(u.state), EditorInput.LoadDocument(d))
  }
}
