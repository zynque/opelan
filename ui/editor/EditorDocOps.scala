package opelan.ui.editor

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.ui.fp.Update
import EditorOutput._
import EditorUpdate.{applyEdit, status, maxUndo}

// Structural and whole-document operations: insert/indent/outdent plus
// new, load-sample, and compact commands.
object EditorDocOps {

  def insertSibling(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.doc.parentOf(m.targetId) match {
      case Some(parentId) =>
        val index = m.doc.childrenOf(parentId).indexOf(m.targetId) + 1
        val newId = m.doc.nodes.length
        applyEdit(
          m,
          Edit.insertNode(DetachedNode.leaf(s("")), parentId, index, m.doc),
          "Inserted sibling",
          Some(newId),
          _.copy(editingId = Some(newId)))
      case None =>
        insertChild(m) // the root has no siblings; insert a child instead
    }

  def insertChild(m: EditorModel): Update[EditorModel, EditorOutput] = {
    val newId = m.doc.nodes.length
    applyEdit(
      m,
      Edit.insertNode(
        DetachedNode.leaf(s("")), m.targetId, m.doc.childrenOf(m.targetId).length, m.doc),
      "Inserted child",
      Some(newId),
      _.copy(editingId = Some(newId)))
  }

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

  def compact(m: EditorModel): Update[EditorModel, EditorOutput] = {
    val before = m.doc.nodes.length
    val (compacted, remap) = Edit.compact(m.doc)
    // Remap internal refs in node data; refs to removed nodes stay dangling.
    val d = compacted.mapData(dd =>
      NodeData.remapInternalRefs(dd, id => remap.getOrElse(id, id)))
    val msg = s"Compacted: $before -> ${d.nodes.length} nodes"
    val nm = m.copy(
      doc = d,
      undoStack = (m.doc :: m.undoStack).take(maxUndo),
      redoStack = Nil,
      selectedId = m.selectedId.flatMap(remap.get).filter(id => d.getNode(id).isDefined),
      detachedNodeId = None,
      status = msg)
    Update(nm, Vector(DocChanged(d), Status(msg)))
  }
}
