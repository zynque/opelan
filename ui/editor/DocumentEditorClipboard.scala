package opelan.ui.editor

import opelan.foundation.document._

// Clipboard operations: remove, cut, copy and paste of whole subtrees.
// Cut keeps the node id for re-attach (pasteNode); copy produces a detached
// duplicate which gets fresh ids on insert.
trait DocumentEditorClipboard extends DocumentEditorState {

  private[editor] def deleteSelected(): Unit = selectedId match {
    case Some(id) if id != doc.rootId =>
      applyEdit(Edit.cutNode(id, doc), "Removed subtree", doc.parentOf(id))
    case _ =>
      statusMessage = "Select a non-root node first"
      render()
  }

  private[editor] def cutSelected(): Unit = selectedId match {
    case Some(id) if id != doc.rootId =>
      if (applyEdit(Edit.cutNode(id, doc), "Cut subtree", doc.parentOf(id)))
        detachedNodeId = Some(id)
    case _ =>
      statusMessage = "Select a non-root node first"
      render()
  }

  private[editor] def copySelected(): Unit = selectedId match {
    case Some(id) =>
      Edit.extractSubtree(id, doc) match {
        case Right(detached) =>
          clipboard = Some(detached)
          statusMessage = s"Copied subtree (${DocumentEditor.subtreeSize(detached)} nodes)"
        case Left(err) => statusMessage = err
      }
      render()
    case None =>
      statusMessage = "Select a node first"
      render()
  }

  private[editor] def pasteIntoSelected(): Unit = {
    val index = doc.childrenOf(targetId).length
    detachedNodeId match {
      case Some(nodeId) =>
        if (applyEdit(Edit.pasteNode(nodeId, targetId, index, doc), "Pasted", Some(nodeId)))
          detachedNodeId = None
      case None =>
        clipboard match {
          case Some(detached) =>
            val newId = doc.nodes.length + DocumentEditor.subtreeSize(detached) - 1
            applyEdit(Edit.insertNode(detached, targetId, index, doc), "Pasted copy", Some(newId))
          case None =>
            statusMessage = "Nothing to paste"
            render()
        }
    }
  }
}
