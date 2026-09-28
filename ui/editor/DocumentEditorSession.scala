package opelan.ui.editor

import opelan.foundation.document._

// Editing-session transitions: moving the selection, entering/leaving inline
// edit mode, and undo/redo over the persistent document stack.
trait DocumentEditorSession extends DocumentEditorState {

  private[editor] def moveSelection(delta: Int): Unit = {
    val ids = flatIds()
    if (ids.nonEmpty) {
      val current = selectedId.map(ids.indexOf).getOrElse(-1)
      val next =
        if (current < 0) (if (delta > 0) 0 else ids.length - 1)
        else math.max(0, math.min(ids.length - 1, current + delta))
      selectedId = Some(ids(next))
      render()
    }
  }

  private[editor] def startEditing(): Unit = selectedId.foreach(startEditingAt)

  private[editor] def startEditingAt(id: Int): Unit =
    if (doc.getNode(id).isDefined) {
      editingId = Some(id)
      render()
    }

  private[editor] def commitEditing(text: String): Unit =
    editingId.foreach { id =>
      editingId = None
      applyEdit(Edit.updateNodeData(id, DocumentEditor.parseNodeData(text), doc), "Updated", Some(id))
    }

  private[editor] def cancelEdit(): Unit = {
    editingId = None
    render()
  }

  private[editor] def undo(): Unit = undoStack match {
    case prev :: rest =>
      redoStack = doc :: redoStack
      doc = prev
      undoStack = rest
      editingId = None
      selectedId = selectedId.filter(id => doc.getNode(id).isDefined)
      statusMessage = "Undo"
      render()
    case Nil =>
      statusMessage = "Nothing to undo"
      render()
  }

  private[editor] def redo(): Unit = redoStack match {
    case next :: rest =>
      undoStack = doc :: undoStack
      doc = next
      redoStack = rest
      editingId = None
      selectedId = selectedId.filter(id => doc.getNode(id).isDefined)
      statusMessage = "Redo"
      render()
    case Nil =>
      statusMessage = "Nothing to redo"
      render()
  }
}
