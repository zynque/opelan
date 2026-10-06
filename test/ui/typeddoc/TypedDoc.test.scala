package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.language.{Expr, ExprLanguage}
import opelan.ui.editor.{EditorInput, EditorOutput, SyncedEntry, SyncedHistory}

class TypedDocSuite extends munit.FunSuite {

  private def init: TypedDocModel = TypedDoc.init

  private def editorInput(m: TypedDocModel): EditorInput =
    TypedDoc.children(m).head.input.asInstanceOf[EditorInput]

  test("init loads a typed expression sample") {
    val m = init
    assertEquals(Typed.typeRefOf(m.doc), Some(ExprLanguage.typeRef))
    assertEquals(m.view, DocView.Editor)
  }

  test("children mounts the document editor with the pushed document") {
    val m = init
    assertEquals(editorInput(m), EditorInput.SyncDocument(m.pushedDoc))
  }

  test("SwitchView selects the text surface") {
    val u = TypedDocUpdate(init, TypedDocInput.SwitchView(DocView.Text))
    assertEquals(u.state.view, DocView.Text)
  }

  test("editor edits update the mirrored doc but are not pushed back") {
    val m = init
    val edited: Document[NodeData] = Build.beginDocument(NodeData.IntData(1))
    val u = TypedDocUpdate(m, TypedDocInput.FromEditor(EditorOutput.DocChanged(edited)))
    assertEquals(u.state.doc, edited)
    assertEquals(u.state.pushedDoc, m.pushedDoc)
    assertEquals(editorInput(u.state), EditorInput.SyncDocument(m.pushedDoc))
    assert(u.out.contains(TypedDocOutput.DocChanged(edited)))
  }

  test("Load pushes the new document into the editor") {
    val m = init
    val d: Document[NodeData] = Build.beginDocument(NodeData.StringData("other"))
    val u = TypedDocUpdate(m, TypedDocInput.Load(d))
    assertEquals(u.state.doc, d)
    assertEquals(u.state.pushedDoc, d)
    assertEquals(editorInput(u.state), EditorInput.SyncDocument(d))
  }

  test("TextEdited parses into the content subtree and pushes the doc") {
    val m = init
    val u = TypedDocUpdate(m, TypedDocInput.TextEdited("1 + 2"))
    assertEquals(u.state.text, "1 + 2")
    assertEquals(u.state.textEpoch, m.textEpoch)
    val content = Typed.contentId(u.state.doc).get
    assertEquals(Expr.fromDocument(u.state.doc, content), Right(Expr.Add(Expr.Lit(1), Expr.Lit(2))))
  }

  test("TextEdited leaves holes where text does not fit") {
    val u = TypedDocUpdate(init, TypedDocInput.TextEdited("1 + abc"))
    val content = Typed.contentId(u.state.doc).get
    assertEquals(
      Expr.fromDocument(u.state.doc, content),
      Right(Expr.Add(Expr.Lit(1), Expr.Hole("abc"))))
    assert(u.state.status.contains("hole"))
  }

  test("editor edits re-derive the text and bump the epoch") {
    val m = init
    val edited: Document[NodeData] = Build.beginDocument(NodeData.IntData(1))
    val u = TypedDocUpdate(m, TypedDocInput.FromEditor(EditorOutput.DocChanged(edited)))
    assertEquals(u.state.textEpoch, m.textEpoch + 1)
  }

  test("SyncHistory pushes the document and shared history into the editor") {
    val d: Document[NodeData] = Build.beginDocument(NodeData.StringData("remote"))
    val h = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "amber-fox", d)), Vector("h1"))
    val u = TypedDocUpdate(init, TypedDocInput.SyncHistory(d, h))
    assertEquals(u.state.doc, d)
    assertEquals(u.state.pushedHistory, Some(h))
    assertEquals(
      editorInput(u.state),
      EditorInput.SyncDocument(d, None, None, Some(h)))
    // no DocChanged echo — the push came from the session owner
    assert(u.out.forall(o => !o.isInstanceOf[TypedDocOutput.DocChanged]))
  }

  test("a text edit pushes the document as a merge") {
    val u = TypedDocUpdate(init, TypedDocInput.TextEdited("1 + 2"))
    assert(u.state.pushedMergeLabel.isDefined)
    assertEquals(u.state.pushedHistory, None)
  }

  test("Load after a synced push resets to a fresh document") {
    val d1: Document[NodeData] = Build.beginDocument(NodeData.StringData("remote"))
    val h = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "amber-fox", d1)), Vector("h1"))
    val m = TypedDocUpdate(init, TypedDocInput.SyncHistory(d1, h)).state
    val d2: Document[NodeData] = Build.beginDocument(NodeData.StringData("other"))
    val u = TypedDocUpdate(m, TypedDocInput.Load(d2))
    assertEquals(u.state.pushedMergeLabel, None)
    assertEquals(u.state.pushedHistory, None)
    assertEquals(editorInput(u.state), EditorInput.SyncDocument(d2))
  }

  test("Load with a selection pushes it through to the editor") {
    val m = init
    val d: Document[NodeData] =
      Build.buildDocument(n(s("r"), sl("a")))
    val target = d.childrenOf(d.rootId).head
    val u = TypedDocUpdate(m, TypedDocInput.Load(d, Some(target)))
    assertEquals(u.state.pushedSelect, Some(target))
    assertEquals(editorInput(u.state), EditorInput.SyncDocument(d, Some(target)))
  }

  test("RequestDoc re-emits the current document") {
    val m = TypedDocUpdate(init, TypedDocInput.TextEdited("7")).state
    val u = TypedDocUpdate(m, TypedDocInput.RequestDoc)
    assertEquals(u.out, Vector(TypedDocOutput.DocChanged(m.doc)))
    assertEquals(u.state, m)
  }

  test("editor FollowRef outputs are passed through") {
    val ref = ExternalNodeReference("opelan:docs/x", 0, 1)
    val u = TypedDocUpdate(init, TypedDocInput.FromEditor(EditorOutput.FollowRef(ref)))
    assertEquals(u.out, Vector(TypedDocOutput.FollowRef(ref)))
  }

  test("an echo of the pushed doc does not re-derive the text") {
    val m = TypedDocUpdate(init, TypedDocInput.TextEdited("5")).state
    val u = TypedDocUpdate(m, TypedDocInput.FromEditor(EditorOutput.DocChanged(m.doc)))
    assertEquals(u.state.textEpoch, m.textEpoch)
    assertEquals(u.state.text, "5")
  }
}
