package opelan.ui.editor

import opelan.foundation.document._
import opelan.ui.fp.Update
import EditorOutput._

// Structural and whole-document operations: insert/indent/outdent plus
// new, load-sample, and compact commands.

// Structural inserts don't create the node immediately: a phantom row
// opens in edit mode and the node materializes — one history entry, one
// sync change — only when its initial text is committed (see
// commitPending in EditorSession). Nothing reaches the document, history,
// or peers before that.
def insertSibling(m: EditorModel): Update[EditorModel, EditorOutput, EditorInput] =
  m.doc.parentOf(m.targetId) match {
    case Some(parentId) =>
      val index = m.doc.childrenOf(parentId).indexOf(m.targetId) + 1
      beginInsert(
        m, PendingInsert(m.flatIds.indexOf(parentId), index, m.targetId))
    case None =>
      insertChild(m) // the root has no siblings; insert a child instead
  }

def insertChild(m: EditorModel): Update[EditorModel, EditorOutput, EditorInput] =
  if (m.doc.getNode(m.targetId).isEmpty) status(m, "No node selected")
  else
    beginInsert(m, PendingInsert(
      m.flatIds.indexOf(m.targetId),
      m.doc.childrenOf(m.targetId).length,
      m.targetId))

private def beginInsert(
    m: EditorModel, p: PendingInsert): Update[EditorModel, EditorOutput, EditorInput] =
  if (m.editingId.isDefined) Update(m)
  else Update(
    m.copy(
      pendingInsert = Some(p),
      editingId = Some(EditorModel.PendingId),
      selectedId = Some(EditorModel.PendingId),
      status = "New node"),
    Vector(Status("New node")))

def indent(m: EditorModel): Update[EditorModel, EditorOutput, EditorInput] =
  m.selectedId match {
    case Some(id) if id != m.doc.rootId =>
      val siblings = m.doc.parentOf(id).toList.flatMap(m.doc.childrenOf)
      siblings.lift(siblings.indexOf(id) - 1) match {
        case Some(newParent) =>
          applyEdit(
            m,
            moveNode(id, newParent, m.doc.childrenOf(newParent).length, m.doc),
            "Indented",
            Some(id))
        case None => status(m, "Cannot indent the first child")
      }
    case _ => status(m, "Select a non-root node first")
  }

def outdent(m: EditorModel): Update[EditorModel, EditorOutput, EditorInput] =
  m.selectedId match {
    case Some(id) if id != m.doc.rootId =>
      val target = for {
        parentId <- m.doc.parentOf(id)
        grandparentId <- m.doc.parentOf(parentId)
      } yield (grandparentId, m.doc.childrenOf(grandparentId).indexOf(parentId) + 1)
      target match {
        case Some((grandparentId, index)) =>
          applyEdit(m, moveNode(id, grandparentId, index, m.doc),
            "Outdented", Some(id))
        case None => status(m, "Cannot outdent a top-level node")
      }
    case _ => status(m, "Select a non-root node first")
  }

// Follow the selected node's reference. Internal refs are followed in
// place by selecting the target; external refs resolve through the
// document store, which only the component's owner holds — so they are
// emitted as an output.
def followRef(m: EditorModel): Update[EditorModel, EditorOutput, EditorInput] =
  m.doc.getNode(m.targetId).map(_.data) match {
    case Some(NodeData.InternalNodeRef(id)) if m.doc.getNode(id).isDefined =>
      Update(m.copy(selectedId = Some(id)), Vector(Status(s"Followed ref to #$id")))
    case Some(NodeData.InternalNodeRef(id)) =>
      status(m, s"Internal ref target #$id does not exist")
    case Some(NodeData.ExternalNodeRef(ref)) =>
      Update(m, Vector(FollowRef(ref)))
    case _ => status(m, "Selected node is not a reference")
  }

def compactDoc(m: EditorModel): Update[EditorModel, EditorOutput, EditorInput] = {
  val before = m.doc.nodes.length
  val (compacted, remap) = compact(m.doc)
  // Remap internal refs in node data; refs to removed nodes stay dangling.
  val d = compacted.mapData(dd =>
    NodeData.remapInternalRefs(dd, id => remap.getOrElse(id, id)))
  val msg = s"Compacted: $before -> ${d.nodes.length} nodes"
  val nm = record(m, d, msg).copy(
    selectedId = m.selectedId.flatMap(remap.get).filter(id => d.getNode(id).isDefined),
    detachedNodeId = None,
    status = msg)
  Update(nm, Vector(DocChanged(d), Status(msg)))
}
