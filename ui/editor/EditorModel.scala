package opelan.ui.editor

import opelan.foundation.document._
import opelan.foundation.version._

// Where a pending (not yet committed) insert will land. The phantom node
// doesn't exist in `doc` — it materializes, with its initial text, as a
// single history entry when the edit box commits.
//
// `parentPos` is the parent's index in the pre-order traversal (flatIds),
// not its node id: a synced reparse reassigns ids, but a node at the same
// outline position is overwhelmingly the same node, so the phantom row
// lands correctly across pushes.
case class PendingInsert(
    parentPos: Int, index: Int, anchorId: Int, draft: String = "")

// The complete editor state — a pure value, so the update function is
// testable and history operates on immutable documents.
//
// History is a version DAG (see EditorHistory): `history` holds every
// document the session has produced and `versionId` marks the version the
// live `doc` belongs to. Undo branches rather than truncates.
case class EditorModel(
    doc: Document[NodeData],
    selectedId: Option[Int] = None,
    editingId: Option[Int] = None,
    history: Document[Version[EditorSnapshot]],
    versionId: Int,
    clipboard: Option[DetachedNode[NodeData]] = None,
    detachedNodeId: Option[Int] = None,
    // The head version under a synced (shared-change-graph) history.
    // Some(headId) marks synced mode; `versionId` differing from it means
    // the user is browsing a checked-out version. None for unsynced docs.
    syncedHead: Option[Int] = None,
    // An open insert box awaiting its initial text. The phantom row
    // carries the sentinel PendingId, never a real node id.
    pendingInsert: Option[PendingInsert] = None,
    status: String = "Ready") {

  // The node an operation applies to when nothing is explicitly selected.
  def targetId: Int = selectedId.getOrElse(doc.rootId)

  // Pre-order outline traversal as (nodeId, depth) pairs.
  def rowsFrom(id: Int, depth: Int = 0): List[(Int, Int)] =
    (id, depth) :: doc.childrenOf(id).flatMap(cid => rowsFrom(cid, depth + 1))

  def flatIds: List[Int] = rowsFrom(doc.rootId).map(_._1)

  // Outline rows for the view: the document's pre-order traversal plus a
  // phantom row for a pending insert spliced at its target position.
  def outlineRows: List[(Int, Int)] = {
    val base = rowsFrom(doc.rootId)
    pendingInsert match {
      case Some(p) if p.parentPos < base.length =>
        var pos = p.parentPos + 1
        doc.childrenOf(base(p.parentPos)._1).take(p.index).foreach { k =>
          pos += rowsFrom(k).length
        }
        base.patch(
          pos, List((EditorModel.PendingId, base(p.parentPos)._2 + 1)), 0)
      case _ => base
    }
  }
}

object EditorModel {
  // Id of a pending insert's phantom row. `nodes.lift(-1)` is always
  // empty, so no document can ever contain it.
  val PendingId = -1

  // Pre-order ids of a document — like rowsFrom/flatIds on the model but
  // for a bare document (e.g. a pushed one pending resolution).
  def flatIdsOf(d: Document[NodeData]): List[Int] = {
    def go(id: Int): List[Int] = id :: d.childrenOf(id).flatMap(go)
    if (d.getNode(d.rootId).isDefined) go(d.rootId) else Nil
  }

  // Fresh editor state over a loaded document: history starts at a single
  // root version holding it.
  def forDocument(
      d: Document[NodeData],
      selectedId: Option[Int] = None,
      status: String = "Ready"): EditorModel = {
    val h = EditorHistory.initial(d)
    EditorModel(d, selectedId, None, h, h.rootId, status = status)
  }
}

// Everything that can happen to the editor — view events and inputs pushed
// by a parent — arrives through this one channel.
enum EditorInput {
  // navigation
  case Move(delta: Int)
  case Select(id: Int)
  case Deselect
  // insertion & structural movement
  case InsertSibling
  case InsertChild
  case Indent
  case Outdent
  // inline editing
  case StartEdit(id: Int)
  case EditSelected
  case CommitEdit(text: String)
  case CancelEdit
  // Keystrokes in a pending insert's box — tracked so the phantom row can
  // restore its text if the DOM input is rebuilt by a re-render.
  case DraftEdit(text: String)
  // clipboard
  case Remove
  case Cut
  case Copy
  case Paste
  // history
  case Undo
  case Redo
  // Jump directly to a version node in the history DAG.
  case GoToVersion(versionId: Int)
  // document-level commands
  case NewDocument
  case LoadDocument(doc: Document[NodeData])
  // Load without echoing DocChanged/Status back to the parent — for
  // parents that push the document down themselves and already know it.
  // `select` selects a node after the load (e.g. a followed ref's target).
  // A `mergeLabel` means the open document itself changed elsewhere (a
  // text-cell edit): the pushed doc is recorded as a new version under
  // that label so the update sits in history and undoes like a local
  // edit. A `history` push is the authoritative shared change graph from
  // a synced session: the version DAG is rebuilt from it wholesale —
  // identical on every peer — superseding mergeLabel. With neither, the
  // push is a different document being opened and history restarts.
  case SyncDocument(
      doc: Document[NodeData],
      select: Option[Int] = None,
      mergeLabel: Option[String] = None,
      history: Option[SyncedHistory] = None)
  // Follow the selected node's reference: internal refs select their
  // target in place; external refs are reported via EditorOutput.FollowRef
  // for the owner (which holds the document store) to resolve.
  case FollowRef
  case LoadSample
  case Compact
}

// What the editor reports outward. Effects (persistence, sync, status
// display) are interpreted by whoever mounts the component.
enum EditorOutput {
  case DocChanged(doc: Document[NodeData])
  // The view moved to another version without producing a change — the
  // owner should update its mirrors but must not feed this to a session.
  case DocViewed(doc: Document[NodeData])
  case Status(message: String)
  // The user asked to follow an external ref; the editor can't resolve
  // cross-document addresses itself.
  case FollowRef(ref: ExternalNodeReference)
  // At a synced head: ask the session to revert this actor's last change
  // / re-apply the last reverted one.
  case UndoRequested
  case RedoRequested
  // An edit was made while checked out on an old version under sync —
  // the owner should fork a branch draft rather than let it revert the
  // shared frontier under collaborators' feet.
  case BranchRequested(doc: Document[NodeData])
}
