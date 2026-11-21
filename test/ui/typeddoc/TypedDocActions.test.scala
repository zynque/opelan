package opelan.ui.typeddoc

import opelan.examples.expr.ExprLanguage
import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.document.Detached._
import opelan.ui.editor.{DocPush, EditorInput, EditorOutput, SelectTarget}

class TypedDocActionsSuite extends munit.FunSuite {

  test("SelectSource pushes a focus-less selection into the editor") {
    val m = init
    val target = contentId(m.doc).get
    val u = typedDocUpdate(m, TypedDocInput.SelectSource(target))
    assertEquals(
      u.state.pushedSelect, Some(SelectTarget(target, focus = false)))
    assertEquals(
      editorInput(u.state),
      EditorInput.SyncDocument(
        m.doc, Some(SelectTarget(target, focus = false)),
        DocPush.Merge("Select")))
    assertEquals(u.state.view, DocView.Editor)
  }

  test("SelectSource on a missing node changes nothing") {
    val u = typedDocUpdate(init, TypedDocInput.SelectSource(999))
    assertEquals(u.state.pushedSelect, None)
  }

  test("SelectSource inside a derived view stays on the app surface") {
    val m = typedDocUpdate(
      init, TypedDocInput.SwitchView(DocView.Derived("print"))).state
    val target = contentId(m.doc).get
    val u = typedDocUpdate(m, TypedDocInput.SelectSource(target))
    assertEquals(u.state.view, DocView.Derived("print"))
    assertEquals(
      u.state.pushedSelect, Some(SelectTarget(target, focus = false)))
  }

  test("GoToDefinition follows the document's type ref") {
    val u = typedDocUpdate(init, TypedDocInput.GoToDefinition)
    assert(u.out.contains(TypedDocOutput.FollowRef(ExprLanguage.typeRef)))
  }

  test("GoToDefinition on an untyped document reports it") {
    val raw =
      typedDocUpdate(init, TypedDocInput.Load(buildDocument(n(s("r"))))).state
    val u = typedDocUpdate(raw, TypedDocInput.GoToDefinition)
    assert(u.out.forall(o => !o.isInstanceOf[TypedDocOutput.FollowRef]))
    assertEquals(u.state.status, "Untyped document")
  }

  test("RequestDoc re-emits the current document") {
    val m = typedDocUpdate(init, TypedDocInput.TextEdited("7")).state
    val u = typedDocUpdate(m, TypedDocInput.RequestDoc)
    assertEquals(u.out, Vector(TypedDocOutput.DocChanged(m.doc)))
    assertEquals(u.state, m)
  }

  test("a Pane push applies per serial; a pull re-emits the live doc") {
    val d: Document[NodeData] = beginDocument(NodeData.StringData("remote"))
    val p = TypedDocInput.Pane(Some(TypedDocInput.Load(d)), 1, 0)
    val u1 = typedDocUpdate(init, p)
    assertEquals(u1.state.doc, d)
    // The owner edited locally — the same props re-delivered do not reload.
    val other: Document[NodeData] = beginDocument(NodeData.IntData(9))
    val edited = typedDocUpdate(
      u1.state, TypedDocInput.FromEditor(EditorOutput.DocChanged(other))).state
    val u2 = typedDocUpdate(edited, p)
    assertEquals(u2.state.doc, other)
    // A bumped push serial re-applies even the identical push.
    val u3 = typedDocUpdate(
      u2.state, TypedDocInput.Pane(Some(TypedDocInput.Load(d)), 2, 0))
    assertEquals(u3.state.doc, d)
    // A bumped pull serial re-emits DocChanged.
    val u4 = typedDocUpdate(
      u3.state, TypedDocInput.Pane(Some(TypedDocInput.Load(d)), 2, 1))
    assertEquals(u4.out, Vector(TypedDocOutput.DocChanged(d)))
  }

  test("editor FollowRef outputs are passed through") {
    val ref = ExternalNodeReference("opelan:docs/x", 0, 1)
    val u = typedDocUpdate(init, TypedDocInput.FromEditor(EditorOutput.FollowRef(ref)))
    assertEquals(u.out, Vector(TypedDocOutput.FollowRef(ref)))
  }
}
