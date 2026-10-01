package opelan.ui.editor

import opelan.foundation.document._

// The complete editor state — a pure value, so the update function is
// testable and history operates on immutable documents.
case class EditorModel(
    doc: Document[NodeData],
    selectedId: Option[Int] = None,
    editingId: Option[Int] = None,
    undoStack: List[Document[NodeData]] = Nil,
    redoStack: List[Document[NodeData]] = Nil,
    clipboard: Option[DetachedNode[NodeData]] = None,
    detachedNodeId: Option[Int] = None,
    status: String = "Ready") {

  // The node an operation applies to when nothing is explicitly selected.
  def targetId: Int = selectedId.getOrElse(doc.rootId)

  // Pre-order outline traversal as (nodeId, depth) pairs.
  def rowsFrom(id: Int, depth: Int = 0): List[(Int, Int)] =
    (id, depth) :: doc.childrenOf(id).flatMap(cid => rowsFrom(cid, depth + 1))

  def flatIds: List[Int] = rowsFrom(doc.rootId).map(_._1)
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
  // clipboard
  case Remove
  case Cut
  case Copy
  case Paste
  // history
  case Undo
  case Redo
  // document-level commands
  case NewDocument
  case LoadDocument(doc: Document[NodeData])
  // Load without echoing DocChanged/Status back to the parent — for
  // parents that push the document down themselves and already know it.
  // `select` selects a node after the load (e.g. a followed ref's target).
  case SyncDocument(doc: Document[NodeData], select: Option[Int] = None)
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
  case Status(message: String)
  // The user asked to follow an external ref; the editor can't resolve
  // cross-document addresses itself.
  case FollowRef(ref: ExternalNodeReference)
}
