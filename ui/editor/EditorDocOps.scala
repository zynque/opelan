package opelan.ui.editor

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.ui.fp.Update
import EditorOutput._
import EditorUpdate.{applyEdit, status}

// Structural and whole-document operations: insert/indent/outdent plus
// new, load-sample, and compact commands.
object EditorDocOps {

  // Structural inserts don't create the node immediately: a phantom row
  // opens in edit mode and the node materializes — one history entry, one
  // sync change — only when its initial text is committed (see
  // EditorSession.commitPending). Nothing reaches the document, history,
  // or peers before that.
  def insertSibling(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.doc.parentOf(m.targetId) match {
      case Some(parentId) =>
        val index = m.doc.childrenOf(parentId).indexOf(m.targetId) + 1
        beginInsert(
          m, PendingInsert(m.flatIds.indexOf(parentId), index, m.targetId))
      case None =>
        insertChild(m) // the root has no siblings; insert a child instead
    }

  def insertChild(m: EditorModel): Update[EditorModel, EditorOutput] =
    if (m.doc.getNode(m.targetId).isEmpty) status(m, "No node selected")
    else
      beginInsert(m, PendingInsert(
        m.flatIds.indexOf(m.targetId),
        m.doc.childrenOf(m.targetId).length,
        m.targetId))

  private def beginInsert(
      m: EditorModel, p: PendingInsert): Update[EditorModel, EditorOutput] =
    if (m.editingId.isDefined) Update(m)
    else Update(
      m.copy(
        pendingInsert = Some(p),
        editingId = Some(EditorModel.PendingId),
        selectedId = Some(EditorModel.PendingId),
        status = "New node"),
      Vector(Status("New node")))

  def indent(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.selectedId match {
      case Some(id) if id != m.doc.rootId =>
        m.doc.parentOf(id) match {
          case Some(parentId) =>
            val siblings = m.doc.childrenOf(parentId)
            val index = siblings.indexOf(id)
            if (index <= 0) status(m, "Cannot indent the first child")
            else {
              val newParent = siblings(index - 1)
              applyEdit(
                m,
                Edit.moveNode(id, newParent, m.doc.childrenOf(newParent).length, m.doc),
                "Indented",
                Some(id))
            }
          case None => status(m, "Node has no parent")
        }
      case _ => status(m, "Select a non-root node first")
    }

  def outdent(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.selectedId match {
      case Some(id) if id != m.doc.rootId =>
        m.doc.parentOf(id).flatMap(m.doc.parentOf) match {
          case Some(grandparentId) =>
            val index = m.doc.childrenOf(grandparentId).indexOf(m.doc.parentOf(id).get)
            applyEdit(
              m,
              Edit.moveNode(id, grandparentId, index + 1, m.doc),
              "Outdented",
              Some(id))
          case None => status(m, "Cannot outdent a top-level node")
        }
      case _ => status(m, "Select a non-root node first")
    }

  // Follow the selected node's reference. Internal refs are followed in
  // place by selecting the target; external refs resolve through the
  // document store, which only the component's owner holds — so they are
  // emitted as an output.
  def followRef(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.doc.getNode(m.targetId).map(_.data) match {
      case Some(NodeData.InternalNodeRef(id)) if m.doc.getNode(id).isDefined =>
        Update(m.copy(selectedId = Some(id)), Vector(Status(s"Followed ref to #$id")))
      case Some(NodeData.InternalNodeRef(id)) =>
        status(m, s"Internal ref target #$id does not exist")
      case Some(NodeData.ExternalNodeRef(ref)) =>
        Update(m, Vector(FollowRef(ref)))
      case _ => status(m, "Selected node is not a reference")
    }

  def compact(m: EditorModel): Update[EditorModel, EditorOutput] = {
    val before = m.doc.nodes.length
    val (compacted, remap) = Edit.compact(m.doc)
    // Remap internal refs in node data; refs to removed nodes stay dangling.
    val d = compacted.mapData(dd =>
      NodeData.remapInternalRefs(dd, id => remap.getOrElse(id, id)))
    val msg = s"Compacted: $before -> ${d.nodes.length} nodes"
    val nm = EditorHistory.record(m, d, msg).copy(
      selectedId = m.selectedId.flatMap(remap.get).filter(id => d.getNode(id).isDefined),
      detachedNodeId = None,
      status = msg)
    Update(nm, Vector(DocChanged(d), Status(msg)))
  }
}
