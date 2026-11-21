package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.document.Detached._
import opelan.ui.editor.{DocPush, EditorInput, EditorOutput}

class TypedDocTextSuite extends munit.FunSuite {

  test("TextEdited parses into the content subtree and pushes the doc") {
    val m = init
    val u = typedDocUpdate(m, TypedDocInput.TextEdited("1 + 2"))
    assertEquals(u.state.text, "1 + 2")
    assertEquals(u.state.textEpoch, m.textEpoch)
    val content = contentId(u.state.doc).get
    assertEquals(shapeOf(u.state.doc, content), exprBin("+", il(1), il(2)))
  }

  test("TextEdited leaves gaps where text does not fit") {
    val u = typedDocUpdate(init, TypedDocInput.TextEdited("1 + abc"))
    val content = contentId(u.state.doc).get
    assertEquals(
      shapeOf(u.state.doc, content),
      exprBin("+", il(1), exprGap("abc")))
    assert(u.state.status.contains("gap"))
  }

  test("editor edits update the mirrored doc but are not pushed back") {
    val m = init
    val edited: Document[NodeData] = beginDocument(NodeData.IntData(1))
    val u = typedDocUpdate(m, TypedDocInput.FromEditor(EditorOutput.DocChanged(edited)))
    assertEquals(u.state.doc, edited)
    assertEquals(u.state.pushedDoc, m.pushedDoc)
    assertEquals(editorInput(u.state), EditorInput.SyncDocument(m.pushedDoc))
    assert(u.out.contains(TypedDocOutput.DocChanged(edited)))
  }

  test("editor edits re-derive the text and bump the epoch") {
    val m = init
    val edited: Document[NodeData] = beginDocument(NodeData.IntData(1))
    val u = typedDocUpdate(m, TypedDocInput.FromEditor(EditorOutput.DocChanged(edited)))
    assertEquals(u.state.textEpoch, m.textEpoch + 1)
  }

  test("a text edit pushes the document as a merge") {
    val u = typedDocUpdate(init, TypedDocInput.TextEdited("1 + 2"))
    assertEquals(u.state.pushMode, DocPush.Merge("Text edit"))
  }

  test("an echo of the pushed doc does not re-derive the text") {
    val m = typedDocUpdate(init, TypedDocInput.TextEdited("5")).state
    val u = typedDocUpdate(m, TypedDocInput.FromEditor(EditorOutput.DocChanged(m.doc)))
    assertEquals(u.state.textEpoch, m.textEpoch)
    assertEquals(u.state.text, "5")
  }
}
