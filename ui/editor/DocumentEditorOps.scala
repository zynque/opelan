package opelan.ui.editor

import opelan.foundation.document._
import opelan.foundation.document.Detached._

// Structural edit operations on the document: inserting nodes and moving
// them (indent/outdent). Clipboard operations live in DocumentEditorClipboard.
trait DocumentEditorOps extends DocumentEditorState { self: DocumentEditor =>

  private[editor] def insertSibling(): Unit =
    doc.parentOf(targetId) match {
      case Some(parentId) =>
        val index = doc.childrenOf(parentId).indexOf(targetId) + 1
        val newId = doc.nodes.length
        if (applyEdit(Edit.insertNode(DetachedNode.leaf(s("")), parentId, index, doc), "Inserted sibling", Some(newId)))
          self.startEditingAt(newId)
      case None =>
        insertChild() // the root has no siblings; insert a child instead
    }

  private[editor] def insertChild(): Unit = {
    val newId = doc.nodes.length
    if (applyEdit(Edit.insertNode(DetachedNode.leaf(s("")), targetId, doc.childrenOf(targetId).length, doc), "Inserted child", Some(newId)))
      self.startEditingAt(newId)
  }

  private[editor] def indent(): Unit = selectedId match {
    case Some(id) if id != doc.rootId =>
      doc.parentOf(id).foreach { parentId =>
        val siblings = doc.childrenOf(parentId)
        val index = siblings.indexOf(id)
        if (index <= 0) {
          statusMessage = "Cannot indent the first child"
          render()
        } else {
          val newParent = siblings(index - 1)
          applyEdit(Edit.moveNode(id, newParent, doc.childrenOf(newParent).length, doc), "Indented", Some(id))
        }
      }
    case _ =>
      statusMessage = "Select a non-root node first"
      render()
  }

  private[editor] def outdent(): Unit = selectedId match {
    case Some(id) if id != doc.rootId =>
      doc.parentOf(id).flatMap(doc.parentOf) match {
        case Some(grandparentId) =>
          val index = doc.childrenOf(grandparentId).indexOf(doc.parentOf(id).get)
          applyEdit(Edit.moveNode(id, grandparentId, index + 1, doc), "Outdented", Some(id))
        case None =>
          statusMessage = "Cannot outdent a top-level node"
          render()
      }
    case _ =>
      statusMessage = "Select a non-root node first"
      render()
  }
}
