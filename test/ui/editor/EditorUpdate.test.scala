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

  test("commit edit parses text into node data and records a version") {
    val m = init
    val editing = m.copy(editingId = Some(m.doc.rootId))
    val u = EditorUpdate(editing, EditorInput.CommitEdit("42"))
    assertEquals(
      u.state.doc.getNode(m.doc.rootId).map(_.data),
      Some(NodeData.IntData(42)))
    assertEquals(u.state.history.nodes.length, m.history.nodes.length + 1)
    assertEquals(u.state.editingId, None)
  }

  test("undo restores the previous document") {
    val m0 = init
    val m1 = EditorUpdate(m0, EditorInput.InsertChild).state
    val u = EditorUpdate(m1.copy(editingId = None), EditorInput.Undo)
    assert(u.state.doc == m0.doc)
  }

  test("redo descends to the most recent version") {
    val m0 = init
    val m1 = EditorUpdate(m0, EditorInput.InsertChild).state
    val m2 = EditorUpdate(m1.copy(editingId = None), EditorInput.Undo).state
    val u = EditorUpdate(m2, EditorInput.Redo)
    assert(u.state.doc == m1.doc)
    assertEquals(u.state.versionId, m1.versionId)
  }

  test("editing after undo branches instead of truncating history") {
    val m0 = init
    val m1 = EditorUpdate(m0, EditorInput.InsertChild).state
    val m2 = EditorUpdate(m1.copy(editingId = None), EditorInput.Undo).state
    val m3 = EditorUpdate(m2.copy(editingId = None, selectedId = None), EditorInput.InsertChild).state
    val siblings = m3.history.childrenOf(m0.versionId)
    assertEquals(siblings.length, 2)
    // the old branch survives and is reachable via GoToVersion
    val back = EditorUpdate(m3, EditorInput.GoToVersion(m1.versionId))
    assert(back.state.doc == m1.doc)
  }

  test("go to version jumps to an arbitrary version") {
    val m0 = init
    val m1 = EditorUpdate(m0, EditorInput.InsertChild).state
    val u = EditorUpdate(m1.copy(editingId = None), EditorInput.GoToVersion(m0.versionId))
    assert(u.state.doc == m0.doc)
    assertEquals(u.state.versionId, m0.versionId)
  }

  test("follow ref selects an internal ref's target") {
    val m = init
    val target = m.doc.childrenOf(m.doc.rootId).head
    val withRef = EditorUpdate(
      m.copy(editingId = Some(m.doc.rootId)),
      EditorInput.CommitEdit(s"&$target")).state
    val u = EditorUpdate(withRef.copy(selectedId = Some(m.doc.rootId)), EditorInput.FollowRef)
    assertEquals(u.state.selectedId, Some(target))
  }

  test("follow ref emits an output for an external ref") {
    val m = init
    val ref = ExternalNodeReference("opelan:docs/x", 0, 1)
    val withRef = m.copy(
      doc = Build.buildDocument(DetachedNode(NodeData.ExternalNodeRef(ref))),
      selectedId = Some(0))
    val u = EditorUpdate(withRef, EditorInput.FollowRef)
    assertEquals(u.out, Vector(EditorOutput.FollowRef(ref)))
  }

  test("follow ref reports a non-reference node as status") {
    val u = EditorUpdate(init, EditorInput.FollowRef)
    assert(u.out.forall(o => !o.isInstanceOf[EditorOutput.FollowRef]))
    assert(u.state.status.contains("not a reference"))
  }

  test("sync document applies an optional selection") {
    val d = Build.buildDocument(
      DetachedNode(NodeData.StringData("r"), List(DetachedNode.leaf(NodeData.IntData(1)))))
    val target = d.childrenOf(d.rootId).head
    val u = EditorUpdate(init, EditorInput.SyncDocument(d, Some(target)))
    assertEquals(u.state.doc, d)
    assertEquals(u.state.selectedId, Some(target))
  }

  test("a merged sync records a version instead of resetting history") {
    val m1 = EditorUpdate(init, EditorInput.InsertChild).state
    val d = Build.buildDocument(
      DetachedNode(NodeData.StringData("r"), List(DetachedNode.leaf(NodeData.IntData(7)))))
    val m2 = EditorUpdate(
      m1.copy(editingId = None),
      EditorInput.SyncDocument(d, mergeLabel = Some("Remote edit"))).state
    assertEquals(m2.doc, d)
    assertEquals(m2.history.nodes.length, m1.history.nodes.length + 1)
    // the remote edit undoes like a local one
    val u = EditorUpdate(m2, EditorInput.Undo)
    assert(u.state.doc == m1.doc)
    // and redoes forward again
    val r = EditorUpdate(u.state, EditorInput.Redo)
    assert(r.state.doc == d)
  }

  test("a merged sync of an unchanged document records nothing") {
    val m = init
    val u = EditorUpdate(m, EditorInput.SyncDocument(m.doc, mergeLabel = Some("x")))
    assertEquals(u.state.history.nodes.length, m.history.nodes.length)
    assertEquals(u.state.versionId, m.versionId)
  }

  test("a non-merge sync still restarts history") {
    val m1 = EditorUpdate(init, EditorInput.InsertChild).state
    val d: Document[NodeData] =
      Build.buildDocument(DetachedNode.leaf(NodeData.IntData(3)))
    val u = EditorUpdate(m1.copy(editingId = None), EditorInput.SyncDocument(d))
    assertEquals(u.state.doc, d)
    assertEquals(u.state.history.nodes.length, 1)
  }

  test("a synced history push rebuilds a shared, author-tagged DAG") {
    val d0: Document[NodeData] = Build.beginDocument(NodeData.StringData("a"))
    val d1: Document[NodeData] = Build.beginDocument(NodeData.StringData("b"))
    val d2: Document[NodeData] = Build.beginDocument(NodeData.StringData("c"))
    val h = SyncedHistory(Vector(
      SyncedEntry("h1", Vector(), "init", d0),
      SyncedEntry("h2", Vector("h1"), "amber-fox", d1),
      SyncedEntry("h3", Vector("h2"), "calm-otter", d2)),
      Vector("h3"))
    val m = EditorUpdate(init, EditorInput.SyncDocument(d2, history = Some(h))).state
    assertEquals(m.doc, d2)
    assertEquals(m.history.nodes.length, 3)
    assertEquals(m.versionId, 2)
    assertEquals(
      m.history.getNode(1).map(_.data.data.label), Some("amber-fox"))
    // undo restores the previous version — the session turns the emitted
    // DocChanged into a revert change shared with peers
    val back = EditorUpdate(m, EditorInput.Undo)
    assert(back.state.doc == d1)
  }

  test("a synced history push records two-dep changes as merge versions") {
    val d: Document[NodeData] = Build.beginDocument(NodeData.StringData("x"))
    val h = SyncedHistory(Vector(
      SyncedEntry("h1", Vector(), "init", d),
      SyncedEntry("h2", Vector("h1"), "amber-fox", d),
      SyncedEntry("h3", Vector("h1"), "calm-otter", d),
      SyncedEntry("h4", Vector("h2", "h3"), "amber-fox", d)),
      Vector("h4"))
    val m = EditorUpdate(init, EditorInput.SyncDocument(d, history = Some(h))).state
    assertEquals(m.history.nodes.length, 4)
    assertEquals(m.history.getNode(3).map(_.data.mergedFromNodeId), Some(Some(2)))
  }

  test("a synced history push drops selection when the node is gone") {
    val m = init
    val gone = m.doc.childrenOf(m.doc.rootId).head
    val d: Document[NodeData] = Build.beginDocument(NodeData.StringData("new"))
    val h = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "amber-fox", d)), Vector("h1"))
    val u = EditorUpdate(
      m.copy(selectedId = Some(gone)),
      EditorInput.SyncDocument(d, history = Some(h)))
    assertEquals(u.state.selectedId, None)
  }

  test("a merged sync drops selection when the node is gone") {
    val m = init
    val gone = m.doc.childrenOf(m.doc.rootId).head
    val d: Document[NodeData] =
      Build.buildDocument(DetachedNode.leaf(NodeData.StringData("new")))
    val u = EditorUpdate(
      m.copy(selectedId = Some(gone)),
      EditorInput.SyncDocument(d, mergeLabel = Some("Remote edit")))
    assertEquals(u.state.selectedId, None)
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
