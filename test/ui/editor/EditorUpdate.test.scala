package opelan.ui.editor

import opelan.foundation.document._

class EditorUpdateSuite extends munit.FunSuite {

  private def init: EditorModel = DocumentEditor.init

  test("init loads the sample document") {
    val m = init
    assert(m.doc.nodes.length > 1)
    assertEquals(m.selectedId, Some(m.doc.rootId))
  }

  test("insert child adds a node and enters edit mode") {
    val m = init
    val u = EditorUpdate(m, EditorInput.InsertChild)
    assertEquals(u.state.doc.nodes.length, m.doc.nodes.length + 1)
    assertEquals(u.state.editingId, Some(m.doc.nodes.length))
    assert(u.out.exists(_.isInstanceOf[EditorOutput.DocChanged]))
  }

  test("move selection walks the outline in pre-order") {
    val m = init
    val u = EditorUpdate(m, EditorInput.Move(1))
    assertEquals(u.state.selectedId, Some(m.flatIds(1)))
  }

  test("indenting the first child fails without changing the document") {
    val m = init
    val firstChild = m.doc.childrenOf(m.doc.rootId).head
    val sel = m.copy(selectedId = Some(firstChild))
    val u = EditorUpdate(sel, EditorInput.Indent)
    assert(u.state.doc == sel.doc)
    assert(u.state.status.contains("indent"))
  }

  test("commit edit parses text into node data and pushes undo") {
    val m = init
    val editing = m.copy(editingId = Some(m.doc.rootId))
    val u = EditorUpdate(editing, EditorInput.CommitEdit("42"))
    assertEquals(
      u.state.doc.getNode(m.doc.rootId).map(_.data),
      Some(NodeData.IntData(42)))
    assertEquals(u.state.undoStack.length, 1)
    assertEquals(u.state.editingId, None)
  }

  test("undo restores the previous document") {
    val m0 = init
    val m1 = EditorUpdate(m0, EditorInput.InsertChild).state
    val u = EditorUpdate(m1.copy(editingId = None), EditorInput.Undo)
    assert(u.state.doc == m0.doc)
  }

  test("cut then paste reattaches the subtree") {
    val m0 = init
    val childId = m0.doc.childrenOf(m0.doc.rootId).head
    val m1 = EditorUpdate(m0.copy(selectedId = Some(childId)), EditorInput.Cut).state
    assertEquals(m1.detachedNodeId, Some(childId))
    val m2 = EditorUpdate(m1.copy(selectedId = Some(m0.doc.rootId)), EditorInput.Paste).state
    assertEquals(m2.detachedNodeId, None)
    assert(m2.doc.childrenOf(m0.doc.rootId).contains(childId))
  }
}
