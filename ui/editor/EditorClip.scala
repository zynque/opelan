package opelan.ui.editor

import opelan.foundation.document._
import opelan.ui.fp.Update
import EditorUpdate.{applyEdit, status}

// Clipboard operations: remove, cut, copy and paste of whole subtrees.
// Cut keeps the node id for re-attach (pasteNode); copy produces a detached
// duplicate which gets fresh ids on insert.
object EditorClip {

  def remove(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.selectedId match {
      case Some(id) if id != m.doc.rootId =>
        applyEdit(m, Edit.cutNode(id, m.doc), "Removed subtree", m.doc.parentOf(id))
      case _ => status(m, "Select a non-root node first")
    }

  def cut(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.selectedId match {
      case Some(id) if id != m.doc.rootId =>
        applyEdit(m, Edit.cutNode(id, m.doc), "Cut subtree", m.doc.parentOf(id),
          _.copy(detachedNodeId = Some(id)))
      case _ => status(m, "Select a non-root node first")
    }

  def copy(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.selectedId match {
      case Some(id) =>
        Edit.extractSubtree(id, m.doc) match {
          case Right(detached) =>
            status(
              m.copy(clipboard = Some(detached)),
              s"Copied subtree (${DocumentEditor.subtreeSize(detached)} nodes)")
          case Left(err) => status(m, err)
        }
      case None => status(m, "Select a node first")
    }

  def paste(m: EditorModel): Update[EditorModel, EditorOutput] = {
    val index = m.doc.childrenOf(m.targetId).length
    m.detachedNodeId match {
      case Some(nodeId) =>
        applyEdit(m, Edit.pasteNode(nodeId, m.targetId, index, m.doc), "Pasted",
          Some(nodeId), _.copy(detachedNodeId = None))
      case None =>
        m.clipboard match {
          case Some(detached) =>
            val newId = m.doc.nodes.length + DocumentEditor.subtreeSize(detached) - 1
            applyEdit(m, Edit.insertNode(detached, m.targetId, index, m.doc),
              "Pasted copy", Some(newId))
          case None => status(m, "Nothing to paste")
        }
    }
  }
}
