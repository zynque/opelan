package opelan.ui.editor

import opelan.foundation.document._
import opelan.ui.fp.Update
import EditorOutput._

// Editing-session transitions: selection movement, entering/leaving inline
// edit mode, and loading documents. History lives in EditorHistory.

def move(m: EditorModel, delta: Int): Update[EditorModel, EditorOutput, EditorInput] = {
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

def startEdit(m: EditorModel, id: Int): Update[EditorModel, EditorOutput, EditorInput] =
  if (m.doc.getNode(id).isDefined) Update(m.copy(editingId = Some(id)))
  else Update(m)

def editSelected(m: EditorModel): Update[EditorModel, EditorOutput, EditorInput] =
  m.selectedId.map(id => startEdit(m, id)).getOrElse(Update(m))

def commitEdit(m: EditorModel, text: String): Update[EditorModel, EditorOutput, EditorInput] =
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
          updateNodeData(id, data, m.doc),
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
    m: EditorModel, text: String): Update[EditorModel, EditorOutput, EditorInput] =
  m.pendingInsert.flatMap(p =>
    m.flatIds.lift(p.parentPos).map(p -> _)) match {
    case Some((p, parentId)) if text.trim.nonEmpty =>
      applyEdit(
        m.copy(
          editingId = None,
          pendingInsert = None,
          selectedId = Some(p.anchorId)),
        insertNode(
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

// Parent-driven sync: replace the document without reporting it back.
// `select` optionally picks a node in the new document — ignored if the
// id doesn't exist there. `push` says how the doc relates to the one
// being shown (see DocPush).
def syncDocument(
    m: EditorModel,
    d: Document[NodeData],
    select: Option[Int],
    push: DocPush): Update[EditorModel, EditorOutput, EditorInput] =
  push match {
    case DocPush.Synced(h) => syncShared(m, d, select, h)
    case DocPush.Open =>
      Update(EditorModel.forDocument(
        d,
        selectedId = select.filter(id => d.getNode(id).isDefined),
        status = "Document synced"))
    case DocPush.Merge(label) =>
      // The same document updated from outside (a text-cell edit) is
      // recorded as a version so undo walks it; an unchanged push is
      // just the echo of our own doc.
      if (d == m.doc) Update(m)
      else Update(retarget(record(m, d, label), d, select).copy(status = label))
  }

// A shared-history push: rebuild the version DAG from the change graph
// and track its frontier in `syncedHead` — undo at the head defers to
// the session (UndoRequested) while browsing stays local.
//
// A browsing pin survives rebuilds by hash — node ids shift on every
// push, so the pin resolves through the snapshot's change hash. Pinned,
// the pane keeps showing the browsed version (DocViewed updates the
// parent's mirrors) rather than being bumped forward by remote edits.
private def syncShared(
    m: EditorModel,
    d: Document[NodeData],
    select: Option[Int],
    h: SyncedHistory): Update[EditorModel, EditorOutput, EditorInput] =
  buildSyncedHistory(h) match {
    case Some((hist, headId)) =>
      val pin = browsePin(m, h)
      val vid = pin.getOrElse(headId)
      val pushed =
        if (pin.isDefined)
          hist.getNode(vid).map(_.data.data.doc).getOrElse(d)
        else d
      // A push carrying the same outline text the editor already has is
      // the echo of our own local edit — keep the live doc: a whole-text
      // reparse reassigns node ids, which would orphan the selection and
      // in-progress edit (id 5 on a reparse isn't the id-5 node we
      // inserted).
      val shown =
        if (render(pushed) == render(m.doc)) m.doc
        else pushed
      val nm = retarget(m.copy(
        doc = shown,
        history = hist,
        versionId = vid,
        syncedHead = Some(headId),
        status = "Synced"), shown, select)
      Update(nm, if (shown != d) Vector(DocViewed(shown)) else Vector.empty)
    case None => Update(m)
  }

// The index in `h` of the change the user is currently pinned on, if
// browsing an old version — the pin follows its change hash across
// rebuilds (node ids shift every push; hashes don't). None while at the
// head, so a synced push follows the frontier.
private def browsePin(m: EditorModel, h: SyncedHistory): Option[Int] =
  if (m.syncedHead.forall(_ == m.versionId)) None
  else for {
    n <- m.history.getNode(m.versionId)
    hash <- n.data.data.hash
    i = h.entries.indexWhere(_.hash == hash)
    if i >= 0
  } yield i

// Retarget id-bound state onto a pushed document: selection, an
// in-progress edit, and a detached (cut) node survive only if their node
// still exists in `d`; a pending insert's phantom row is local-only and
// survives while the pushed outline still has a row at the parent's
// position (ids shift on reparse; positions don't). `select`, when
// present, overrides the carried selection.
private def retarget(
    m: EditorModel,
    d: Document[NodeData],
    select: Option[Int]): EditorModel = {
  val pending = m.pendingInsert.filter(p =>
    EditorModel.flatIdsOf(d).isDefinedAt(p.parentPos))
  val keep = (id: Int) =>
    if (id == EditorModel.PendingId) pending.isDefined
    else d.getNode(id).isDefined
  m.copy(
    selectedId = select.orElse(m.selectedId).filter(keep),
    editingId = m.editingId.filter(keep),
    pendingInsert = pending,
    detachedNodeId = m.detachedNodeId.filter(id => d.getNode(id).isDefined))
}

def newDocument(): Update[EditorModel, EditorOutput, EditorInput] = {
  val d = beginDocument(Detached.s("root"))
  Update(
    EditorModel.forDocument(d, Some(d.rootId), "New document"),
    Vector(DocChanged(d), Status("New document")))
}

def loadSample(): Update[EditorModel, EditorOutput, EditorInput] = {
  val nm = sampleModel
  Update(nm, Vector(DocChanged(nm.doc), Status(nm.status)))
}
