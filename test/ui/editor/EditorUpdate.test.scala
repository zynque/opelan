package opelan.ui.editor

import opelan.foundation.document._

class EditorUpdateSuite extends munit.FunSuite {

  private def init: EditorModel = DocumentEditor.init

  test("init loads the sample document") {
    val m = init
    assert(m.doc.nodes.length > 1)
    assertEquals(m.selectedId, Some(m.doc.rootId))
  }

  // An InsertChild committed with text — the pending node materializes.
  private def insertCommitted(m: EditorModel, text: String = "x"): EditorModel =
    editorUpdate(
      editorUpdate(m, EditorInput.InsertChild).state,
      EditorInput.CommitEdit(text)).state

  test("insert child opens a phantom edit box without touching the document") {
    val m = init
    val u = editorUpdate(m, EditorInput.InsertChild)
    assertEquals(u.state.doc, m.doc)
    assertEquals(u.state.history, m.history)
    assertEquals(u.state.editingId, Some(EditorModel.PendingId))
    assert(u.state.pendingInsert.isDefined)
    assert(u.state.outlineRows.size == m.flatIds.size + 1)
    assert(u.out.forall(o => !o.isInstanceOf[EditorOutput.DocChanged]))
  }

  test("committing a pending insert creates the named node as one event") {
    val m = init
    val pending = editorUpdate(m, EditorInput.InsertChild).state
    val u = editorUpdate(pending, EditorInput.CommitEdit("intro"))
    val newId = u.state.doc.nodes.length - 1
    assertEquals(u.state.doc.nodes.length, m.doc.nodes.length + 1)
    assertEquals(
      u.state.doc.getNode(newId).map(_.data),
      Some(NodeData.StringData("intro")))
    assertEquals(u.state.editingId, None)
    assertEquals(u.state.pendingInsert, None)
    assertEquals(u.state.selectedId, Some(newId))
    assertEquals(u.state.history.nodes.length, m.history.nodes.length + 1)
    assert(u.out.exists(_.isInstanceOf[EditorOutput.DocChanged]))
  }

  test("an empty commit abandons a pending insert") {
    val m = init
    val pending = editorUpdate(m, EditorInput.InsertChild).state
    val u = editorUpdate(pending, EditorInput.CommitEdit(""))
    assertEquals(u.state.doc, m.doc)
    assertEquals(u.state.editingId, None)
    assertEquals(u.state.pendingInsert, None)
    assertEquals(u.state.history.nodes.length, m.history.nodes.length)
    assert(u.out.forall(o => !o.isInstanceOf[EditorOutput.DocChanged]))
  }

  test("escape cancels a pending insert and restores selection") {
    val m = init
    val pending = editorUpdate(m, EditorInput.InsertChild).state
    val u = editorUpdate(pending, EditorInput.CancelEdit)
    assertEquals(u.state.doc, m.doc)
    assertEquals(u.state.editingId, None)
    assertEquals(u.state.pendingInsert, None)
    assertEquals(u.state.selectedId, Some(m.doc.rootId))
  }

  test("move selection walks the outline in pre-order") {
    val m = init
    val u = editorUpdate(m, EditorInput.Move(1))
    assertEquals(u.state.selectedId, Some(m.flatIds(1)))
  }

  test("indenting the first child fails without changing the document") {
    val m = init
    val firstChild = m.doc.childrenOf(m.doc.rootId).head
    val sel = m.copy(selectedId = Some(firstChild))
    val u = editorUpdate(sel, EditorInput.Indent)
    assert(u.state.doc == sel.doc)
    assert(u.state.status.contains("indent"))
  }

  test("commit edit parses text into node data and records a version") {
    val m = init
    val editing = m.copy(editingId = Some(m.doc.rootId))
    val u = editorUpdate(editing, EditorInput.CommitEdit("42"))
    assertEquals(
      u.state.doc.getNode(m.doc.rootId).map(_.data),
      Some(NodeData.IntData(42)))
    assertEquals(u.state.history.nodes.length, m.history.nodes.length + 1)
    assertEquals(u.state.editingId, None)
  }

  test("undo restores the previous document") {
    val m0 = init
    val m1 = insertCommitted(m0)
    val u = editorUpdate(m1, EditorInput.Undo)
    assert(u.state.doc == m0.doc)
  }

  test("redo descends to the most recent version") {
    val m0 = init
    val m1 = insertCommitted(m0)
    val m2 = editorUpdate(m1, EditorInput.Undo).state
    val u = editorUpdate(m2, EditorInput.Redo)
    assert(u.state.doc == m1.doc)
    assertEquals(u.state.versionId, m1.versionId)
  }

  test("editing after undo branches instead of truncating history") {
    val m0 = init
    val m1 = insertCommitted(m0)
    val m2 = editorUpdate(m1, EditorInput.Undo).state
    val m3 = insertCommitted(m2.copy(selectedId = None))
    val siblings = m3.history.childrenOf(m0.versionId)
    assertEquals(siblings.length, 2)
    // the old branch survives and is reachable via GoToVersion
    val back = editorUpdate(m3, EditorInput.GoToVersion(m1.versionId))
    assert(back.state.doc == m1.doc)
  }

  test("go to version jumps to an arbitrary version") {
    val m0 = init
    val m1 = insertCommitted(m0)
    val u = editorUpdate(m1, EditorInput.GoToVersion(m0.versionId))
    assert(u.state.doc == m0.doc)
    assertEquals(u.state.versionId, m0.versionId)
  }

  test("follow ref selects an internal ref's target") {
    val m = init
    val target = m.doc.childrenOf(m.doc.rootId).head
    val withRef = editorUpdate(
      m.copy(editingId = Some(m.doc.rootId)),
      EditorInput.CommitEdit(s"&$target")).state
    val u = editorUpdate(withRef.copy(selectedId = Some(m.doc.rootId)), EditorInput.FollowRef)
    assertEquals(u.state.selectedId, Some(target))
  }

  test("follow ref emits an output for an external ref") {
    val m = init
    val ref = ExternalNodeReference("opelan:docs/x", 0, 1)
    val withRef = m.copy(
      doc = buildDocument(DetachedNode(NodeData.ExternalNodeRef(ref))),
      selectedId = Some(0))
    val u = editorUpdate(withRef, EditorInput.FollowRef)
    assertEquals(u.out, Vector(EditorOutput.FollowRef(ref)))
  }

  test("follow ref reports a non-reference node as status") {
    val u = editorUpdate(init, EditorInput.FollowRef)
    assert(u.out.forall(o => !o.isInstanceOf[EditorOutput.FollowRef]))
    assert(u.state.status.contains("not a reference"))
  }

  test("sync document applies an optional selection") {
    val d = buildDocument(
      DetachedNode(NodeData.StringData("r"), List(DetachedNode.leaf(NodeData.IntData(1)))))
    val target = d.childrenOf(d.rootId).head
    val u = editorUpdate(init, EditorInput.SyncDocument(d, Some(target)))
    assertEquals(u.state.doc, d)
    assertEquals(u.state.selectedId, Some(target))
  }

  test("a merged sync records a version instead of resetting history") {
    val m1 = insertCommitted(init)
    val d = buildDocument(
      DetachedNode(NodeData.StringData("r"), List(DetachedNode.leaf(NodeData.IntData(7)))))
    val m2 = editorUpdate(
      m1.copy(editingId = None),
      EditorInput.SyncDocument(d, push = DocPush.Merge("Remote edit"))).state
    assertEquals(m2.doc, d)
    assertEquals(m2.history.nodes.length, m1.history.nodes.length + 1)
    // the remote edit undoes like a local one
    val u = editorUpdate(m2, EditorInput.Undo)
    assert(u.state.doc == m1.doc)
    // and redoes forward again
    val r = editorUpdate(u.state, EditorInput.Redo)
    assert(r.state.doc == d)
  }

  test("a merged sync of an unchanged document records nothing") {
    val m = init
    val u = editorUpdate(m, EditorInput.SyncDocument(m.doc, push = DocPush.Merge("x")))
    assertEquals(u.state.history.nodes.length, m.history.nodes.length)
    assertEquals(u.state.versionId, m.versionId)
  }

  test("a non-merge sync still restarts history") {
    val m1 = insertCommitted(init)
    val d: Document[NodeData] =
      buildDocument(DetachedNode.leaf(NodeData.IntData(3)))
    val u = editorUpdate(m1.copy(editingId = None), EditorInput.SyncDocument(d))
    assertEquals(u.state.doc, d)
    assertEquals(u.state.history.nodes.length, 1)
  }

  test("a synced history push rebuilds a shared, author-tagged DAG") {
    val d0: Document[NodeData] = beginDocument(NodeData.StringData("a"))
    val d1: Document[NodeData] = beginDocument(NodeData.StringData("b"))
    val d2: Document[NodeData] = beginDocument(NodeData.StringData("c"))
    val h = SyncedHistory(Vector(
      SyncedEntry("h1", Vector(), "init", d0),
      SyncedEntry("h2", Vector("h1"), "amber-fox", d1),
      SyncedEntry("h3", Vector("h2"), "calm-otter", d2)),
      Vector("h3"))
    val m = editorUpdate(init, EditorInput.SyncDocument(d2, push = DocPush.Synced(h))).state
    assertEquals(m.doc, d2)
    assertEquals(m.history.nodes.length, 3)
    assertEquals(m.versionId, 2)
    assertEquals(
      m.history.getNode(1).map(_.data.data.label), Some("amber-fox"))
    // at the synced head, undo defers to the session — it reverts this
    // actor's own change rather than navigating the shared graph
    val back = editorUpdate(m, EditorInput.Undo)
    assertEquals(back.out, Vector(EditorOutput.UndoRequested))
    assert(back.state.doc == d2)
  }

  test("a synced history push records two-dep changes as merge versions") {
    val d: Document[NodeData] = beginDocument(NodeData.StringData("x"))
    val h = SyncedHistory(Vector(
      SyncedEntry("h1", Vector(), "init", d),
      SyncedEntry("h2", Vector("h1"), "amber-fox", d),
      SyncedEntry("h3", Vector("h1"), "calm-otter", d),
      SyncedEntry("h4", Vector("h2", "h3"), "amber-fox", d)),
      Vector("h4"))
    val m = editorUpdate(init, EditorInput.SyncDocument(d, push = DocPush.Synced(h))).state
    assertEquals(m.history.nodes.length, 4)
    assertEquals(m.history.getNode(3).map(_.data.mergedFromNodeId), Some(Some(2)))
  }

  test("navigation emits DocViewed, never DocChanged") {
    val m0 = init
    val m1 = insertCommitted(m0)
    val u = editorUpdate(m1, EditorInput.GoToVersion(m0.versionId))
    assert(u.out.exists(_.isInstanceOf[EditorOutput.DocViewed]))
    assert(u.out.forall(o => !o.isInstanceOf[EditorOutput.DocChanged]))
  }

  test("undo while browsing a synced doc still navigates locally") {
    val d0: Document[NodeData] = beginDocument(NodeData.StringData("a"))
    val d1: Document[NodeData] = beginDocument(NodeData.StringData("b"))
    val d2: Document[NodeData] = beginDocument(NodeData.StringData("c"))
    val h = SyncedHistory(Vector(
      SyncedEntry("h1", Vector(), "init", d0),
      SyncedEntry("h2", Vector("h1"), "amber-fox", d1),
      SyncedEntry("h3", Vector("h2"), "calm-otter", d2)),
      Vector("h3"))
    val m = editorUpdate(init, EditorInput.SyncDocument(d2, push = DocPush.Synced(h))).state
    // check out the middle version, then undo = a cursor step, not a
    // session revert
    val checked =
      editorUpdate(m, EditorInput.GoToVersion(1)).state
    val u = editorUpdate(checked, EditorInput.Undo)
    assert(u.state.doc == d0)
    assert(u.out.exists(_.isInstanceOf[EditorOutput.DocViewed]))
    assert(u.out.forall(o => o != EditorOutput.UndoRequested))
  }

  test("editing a checked-out synced version requests a branch") {
    val d0: Document[NodeData] = beginDocument(NodeData.StringData("a"))
    val d1: Document[NodeData] = beginDocument(NodeData.StringData("b"))
    val d2: Document[NodeData] = beginDocument(NodeData.StringData("c"))
    val h = SyncedHistory(Vector(
      SyncedEntry("h1", Vector(), "init", d0),
      SyncedEntry("h2", Vector("h1"), "amber-fox", d1),
      SyncedEntry("h3", Vector("h2"), "calm-otter", d2)),
      Vector("h3"))
    val m = editorUpdate(init, EditorInput.SyncDocument(d2, push = DocPush.Synced(h))).state
    val checked = editorUpdate(m, EditorInput.GoToVersion(1)).state
    // opening the insert box is not yet an edit — the branch request
    // comes when the pending node's text is committed
    val pending = editorUpdate(
      checked.copy(selectedId = None), EditorInput.InsertChild)
    assert(pending.out.forall(o => !o.isInstanceOf[EditorOutput.BranchRequested]))
    assert(pending.out.forall(o => !o.isInstanceOf[EditorOutput.DocChanged]))
    val u = editorUpdate(pending.state, EditorInput.CommitEdit("x"))
    assert(u.out.exists(_.isInstanceOf[EditorOutput.BranchRequested]))
    assert(u.out.forall(o => !o.isInstanceOf[EditorOutput.DocChanged]))
    // while at the head, edits still flow as DocChanged
    val atHead = insertCommitted(m.copy(selectedId = None))
    assert(atHead.doc.nodes.length == m.doc.nodes.length + 1)
  }

  test("a browsing pin survives incoming synced pushes") {
    val d0: Document[NodeData] = beginDocument(NodeData.StringData("a"))
    val d1: Document[NodeData] = beginDocument(NodeData.StringData("b"))
    val d2: Document[NodeData] = beginDocument(NodeData.StringData("c"))
    val d3: Document[NodeData] = beginDocument(NodeData.StringData("d"))
    val h = SyncedHistory(Vector(
      SyncedEntry("h1", Vector(), "init", d0),
      SyncedEntry("h2", Vector("h1"), "amber-fox", d1),
      SyncedEntry("h3", Vector("h2"), "calm-otter", d2)),
      Vector("h3"))
    val m = editorUpdate(init, EditorInput.SyncDocument(d2, push = DocPush.Synced(h))).state
    val checked = editorUpdate(m, EditorInput.GoToVersion(1)).state
    // a remote change arrives while browsing — the pin holds by hash
    val h2 = SyncedHistory(h.entries :+
      SyncedEntry("h4", Vector("h3"), "calm-otter", d3), Vector("h4"))
    val u = editorUpdate(checked, EditorInput.SyncDocument(d3, push = DocPush.Synced(h2)))
    assert(u.state.doc == d1)
    assertEquals(u.state.syncedHead, Some(3))
    assertEquals(u.state.versionId, 1)
    assert(u.out.exists(_.isInstanceOf[EditorOutput.DocViewed]))
  }

  test("a synced echo of a local edit keeps the live doc and selection") {
    val m0 = init
    val m1 = insertCommitted(m0)
    val newId = m1.doc.nodes.length - 1
    // the session round-trips the outline text — reparsing reassigns node
    // ids, so the inserted node no longer sits at `newId` in the pushed doc
    val pushed = parse(render(m1.doc)).get
    assert(pushed != m1.doc)
    val h = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "amber-fox", pushed)),
      Vector("h1"))
    val u = editorUpdate(m1, EditorInput.SyncDocument(pushed, push = DocPush.Synced(h)))
    // the editor keeps its own doc (stable ids), so the new node stays
    // selected instead of the highlight jumping to a wrong node
    assertEquals(u.state.doc, m1.doc)
    assertEquals(u.state.selectedId, Some(newId))
  }

  test("a synced push keeps an open pending insert box") {
    val m0 = init
    val pending = editorUpdate(m0, EditorInput.InsertChild).state
    // a remote change arrives while the phantom box is open
    val d = buildDocument(
      DetachedNode(NodeData.StringData("r"),
        List(DetachedNode.leaf(NodeData.IntData(1)))))
    val h = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "calm-otter", d)), Vector("h1"))
    val u = editorUpdate(pending, EditorInput.SyncDocument(d, push = DocPush.Synced(h)))
    assertEquals(u.state.editingId, Some(EditorModel.PendingId))
    assert(u.state.pendingInsert.isDefined)
    // and the phantom can still commit into the pushed document
    val c = editorUpdate(u.state, EditorInput.CommitEdit("kept"))
    assert(c.state.doc.nodes.length == d.nodes.length + 1)
  }

  test("a synced history push drops selection when the node is gone") {
    val m = init
    val gone = m.doc.childrenOf(m.doc.rootId).head
    val d: Document[NodeData] = beginDocument(NodeData.StringData("new"))
    val h = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "amber-fox", d)), Vector("h1"))
    val u = editorUpdate(
      m.copy(selectedId = Some(gone)),
      EditorInput.SyncDocument(d, push = DocPush.Synced(h)))
    assertEquals(u.state.selectedId, None)
  }

  test("a merged sync drops selection when the node is gone") {
    val m = init
    val gone = m.doc.childrenOf(m.doc.rootId).head
    val d: Document[NodeData] =
      buildDocument(DetachedNode.leaf(NodeData.StringData("new")))
    val u = editorUpdate(
      m.copy(selectedId = Some(gone)),
      EditorInput.SyncDocument(d, push = DocPush.Merge("Remote edit")))
    assertEquals(u.state.selectedId, None)
  }

  // The full local commit → pane → session → peer pipeline, the same
  // hops a browser tab takes: commit emits DocChanged, the pane forwards
  // it, the session writes the render into Automerge — broadcast and
  // persist follow from publish().
  test("a committed pending insert reaches a synced peer and persists") {
    import opelan.collaboration.DocSession
    import opelan.collaboration.backends.{SyncEnvelope, SyncTransport}
    import opelan.ui.typeddoc.{TypedDocInput, TypedDocOutput, typedDocUpdate}

    class Pipe(val peerId: String) extends SyncTransport {
      var handler: SyncEnvelope => Unit = _ => ()
      var others: List[Pipe] = Nil
      def send(e: SyncEnvelope): Unit = others.foreach(_.handler(e))
      def subscribe(h: SyncEnvelope => Unit): Unit = handler = h
      def close(): Unit = ()
    }
    val (ta, tb) = (new Pipe("A"), new Pipe("B"))
    ta.others = List(tb); tb.others = List(ta)
    def actor(c: String): String = c * 64

    val m0 = init
    val h0 = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "init", m0.doc)), Vector("h1"))
    // editor sees the synced push (marks syncedHead)
    var m = editorUpdate(
      m0, EditorInput.SyncDocument(m0.doc, push = DocPush.Synced(h0))).state
    // deferred insert committed with its initial text
    m = editorUpdate(m, EditorInput.InsertChild).state
    val committed = editorUpdate(m, EditorInput.CommitEdit("intro")).out
      .collect { case EditorOutput.DocChanged(d) => d }.headOption
    assert(committed.isDefined)

    // the pane forwards DocChanged (it's not the pushed doc)
    val pane = typedDocUpdate(
      typedDocUpdate(
        opelan.ui.typeddoc.TypedDoc.init,
        TypedDocInput.SyncHistory(m0.doc, h0)).state,
      TypedDocInput.FromEditor(EditorOutput.DocChanged(committed.get)))
    val paneDoc = pane.out
      .collect { case TypedDocOutput.DocChanged(d) => d }.headOption
    assertEquals(paneDoc, Some(committed.get))

    // the session accepts it — it must differ from lastText
    var persisted = false
    var remoteText = ""
    var b: DocSession = null
    val a = new DocSession("u", ta, actor("a"), "amber-fox",
      (_, _) => (), _ => persisted = true)
    b = new DocSession("u", tb, actor("b"), "calm-otter",
      (_, _) => remoteText = b.text, _ => ())
    a.attach(render(m0.doc), None)
    b.attach(render(m0.doc), None)
    a.localText(render(paneDoc.get))
    assert(persisted)
    assertEquals(remoteText, a.text)
    assert(remoteText.contains("intro"))
  }

  test("cut then paste reattaches the subtree") {
    val m0 = init
    val childId = m0.doc.childrenOf(m0.doc.rootId).head
    val m1 = editorUpdate(m0.copy(selectedId = Some(childId)), EditorInput.Cut).state
    assertEquals(m1.detachedNodeId, Some(childId))
    val m2 = editorUpdate(m1.copy(selectedId = Some(m0.doc.rootId)), EditorInput.Paste).state
    assertEquals(m2.detachedNodeId, None)
    assert(m2.doc.childrenOf(m0.doc.rootId).contains(childId))
  }
}
