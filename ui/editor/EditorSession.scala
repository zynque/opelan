package opelan.ui.editor

import opelan.foundation.document._
import opelan.ui.fp.Update
import EditorOutput._
import EditorUpdate.applyEdit

// Editing-session transitions: selection movement, entering/leaving inline
// edit mode, and loading documents. History lives in EditorHistory.
object EditorSession {

  def move(m: EditorModel, delta: Int): Update[EditorModel, EditorOutput] = {
    val ids = m.flatIds
    if (ids.isEmpty) Update(m)
    else {
      val current = m.selectedId.map(ids.indexOf).getOrElse(-1)
      val next =
        if (current < 0) (if (delta > 0) 0 else ids.length - 1)
        else math.max(0, math.min(ids.length - 1, current + delta))
      Update(m.copy(selectedId = Some(ids(next))))
    }
  }

  def startEdit(m: EditorModel, id: Int): Update[EditorModel, EditorOutput] =
    if (m.doc.getNode(id).isDefined) Update(m.copy(editingId = Some(id)))
    else Update(m)

  def editSelected(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.selectedId.map(id => startEdit(m, id)).getOrElse(Update(m))

  def commitEdit(m: EditorModel, text: String): Update[EditorModel, EditorOutput] =
    m.editingId match {
      case Some(EditorModel.PendingId) => commitPending(m, text)
      case Some(id) =>
        val data = DocumentEditor.parseNodeData(text)
        // An unchanged commit (e.g. a blur from a remounted input)
        // exits edit mode without recording a version.
        if (m.doc.getNode(id).exists(_.data == data))
          Update(m.copy(editingId = None))
        else
          applyEdit(
            m.copy(editingId = None),
            Edit.updateNodeData(id, data, m.doc),
            "Updated",
            Some(id))
      case None => Update(m)
    }

  // The phantom becomes a real node only now: the insert carries its
  // initial text, so history (and the shared change graph under sync)
  // records a single creation event. An empty commit abandons the
  // phantom — nothing reaches the document — which also makes a stray
  // blur incapable of materializing an empty node.
  private def commitPending(
      m: EditorModel, text: String): Update[EditorModel, EditorOutput] =
    m.pendingInsert.flatMap(p =>
      m.flatIds.lift(p.parentPos).map(p -> _)) match {
      case Some((p, parentId)) if text.trim.nonEmpty =>
        applyEdit(
          m.copy(
            editingId = None,
            pendingInsert = None,
            selectedId = Some(p.anchorId)),
          Edit.insertNode(
            DetachedNode.leaf(DocumentEditor.parseNodeData(text)),
            parentId, p.index, m.doc),
          "Inserted",
          Some(m.doc.nodes.length))
      case _ =>
        Update(m.copy(
          editingId = None,
          pendingInsert = None,
          selectedId =
            if (m.selectedId.contains(EditorModel.PendingId))
              m.pendingInsert.map(_.anchorId)
            else m.selectedId))
    }

  def loadDocument(d: Document[NodeData]): Update[EditorModel, EditorOutput] =
    Update(
      EditorModel.forDocument(d, status = "Document loaded"),
      Vector(DocChanged(d), Status("Document loaded")))

  // Parent-driven sync: replace the document without reporting it back.
  // `select` optionally picks a node in the new document — ignored if the
  // id doesn't exist there.
  //
  // A plain push opens a different document, so history restarts at a
  // fresh root. A `mergeLabel` push is the same document updated from
  // outside (e.g. a text-cell edit): record it as a new version on the
  // current history so undo walks back over it. A `history` push carries
  // the shared change graph: the version DAG is rebuilt wholesale from
  // it and `syncedHead` tracks the frontier version — undo at the head is
  // deferred to the session (UndoRequested) while browsing stays local.
  //
  // A browsing pin survives rebuilds by hash — node ids shift on every
  // push, so the pin resolves through the snapshot's change hash. Pinned,
  // the pane keeps showing the browsed version (DocViewed updates the
  // parent's mirrors) rather than being bumped forward by remote edits.
  // State bound to node ids (selection, an in-progress edit, a detached
  // cut node) survives only if the node still exists in the shown doc.
  def syncDocument(
      m: EditorModel,
      d: Document[NodeData],
      select: Option[Int],
      mergeLabel: Option[String],
      history: Option[SyncedHistory]): Update[EditorModel, EditorOutput] =
    (mergeLabel, history) match {
      case (_, Some(h)) =>
        EditorSynced.build(h) match {
          case Some((hist, headId)) =>
            val pin =
              if (m.syncedHead.forall(_ == m.versionId)) None
              else for {
                n <- m.history.getNode(m.versionId)
                hash <- n.data.data.hash
                i <- h.entries.indexWhere(_.hash == hash) match {
                  case -1 => None
                  case x  => Some(x)
                }
              } yield i
            val vid = pin.getOrElse(headId)
            val pushed =
              if (pin.isDefined)
                hist.getNode(vid).map(_.data.data.doc).getOrElse(d)
              else d
            // A push carrying the same outline text the editor already
            // has is the echo of our own local edit — keep the live doc:
            // a whole-text reparse reassigns node ids, which would orphan
            // the selection and in-progress edit (id 5 on a reparse isn't
            // the id-5 node we inserted).
            val shown =
              if (Outline.render(pushed) == Outline.render(m.doc)) m.doc
              else pushed
            // A pending insert's phantom row is local-only — it survives
            // a push as long as the pushed outline still has a row at the
            // parent's position (ids shift on reparse; positions don't).
            val pending = m.pendingInsert.filter(p =>
              EditorModel.flatIdsOf(shown).isDefinedAt(p.parentPos))
            val keep = (id: Int) =>
              if (id == EditorModel.PendingId) pending.isDefined
              else shown.getNode(id).isDefined
            val nm = m.copy(
              doc = shown,
              history = hist,
              versionId = vid,
              syncedHead = Some(headId),
              selectedId = select.orElse(m.selectedId).filter(keep),
              editingId = m.editingId.filter(keep),
              pendingInsert = pending,
              detachedNodeId =
                m.detachedNodeId.filter(id => shown.getNode(id).isDefined),
              status = "Synced")
            Update(nm, if (shown != d) Vector(DocViewed(shown)) else Vector.empty)
          case None => Update(m)
        }
      case (None, None) =>
        Update(EditorModel.forDocument(
          d,
          selectedId = select.filter(id => d.getNode(id).isDefined),
          status = "Document synced"))
      case (Some(label), None) =>
        if (d == m.doc) Update(m)
        else {
          val pending = m.pendingInsert.filter(p =>
            EditorModel.flatIdsOf(d).isDefinedAt(p.parentPos))
          val keep = (id: Int) =>
            if (id == EditorModel.PendingId) pending.isDefined
            else d.getNode(id).isDefined
          val nm = EditorHistory.record(m, d, label).copy(
            selectedId = select.orElse(m.selectedId).filter(keep),
            editingId = m.editingId.filter(keep),
            pendingInsert = pending,
            detachedNodeId = m.detachedNodeId.filter(id => d.getNode(id).isDefined),
            status = label)
          Update(nm)
        }
    }

  def newDocument(): Update[EditorModel, EditorOutput] = {
    val d = Build.beginDocument(Detached.s("root"))
    Update(
      EditorModel.forDocument(d, Some(d.rootId), "New document"),
      Vector(DocChanged(d), Status("New document")))
  }

  def sample(): Update[EditorModel, EditorOutput] = {
    val nm = EditorSample.model
    Update(nm, Vector(DocChanged(nm.doc), Status(nm.status)))
  }
}
