package opelan.ui.editor

import opelan.foundation.document._
import opelan.ui.fp.Update
import EditorOutput._
import EditorUpdate.{applyEdit, status}

// Editing-session transitions: selection movement, entering/leaving inline
// edit mode, loading documents, and undo/redo over the persistent-doc stack.
object EditorSession {

  def move(m: EditorModel, delta: Int): Update[EditorModel, EditorOutput] = {
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

  def startEdit(m: EditorModel, id: Int): Update[EditorModel, EditorOutput] =
    if (m.doc.getNode(id).isDefined) Update(m.copy(editingId = Some(id)))
    else Update(m)

  def editSelected(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.selectedId.map(id => startEdit(m, id)).getOrElse(Update(m))

  def commitEdit(m: EditorModel, text: String): Update[EditorModel, EditorOutput] =
    m.editingId match {
      case Some(id) =>
        applyEdit(
          m.copy(editingId = None),
          Edit.updateNodeData(id, DocumentEditor.parseNodeData(text), m.doc),
          "Updated",
          Some(id))
      case None => Update(m)
    }

  def loadDocument(d: Document[NodeData]): Update[EditorModel, EditorOutput] =
    Update(
      EditorModel(doc = d, status = "Document loaded"),
      Vector(DocChanged(d), Status("Document loaded")))

  // Parent-driven sync: replace the document without reporting it back.
  def syncDocument(d: Document[NodeData]): Update[EditorModel, EditorOutput] =
    Update(EditorModel(doc = d, status = "Document synced"))

  def newDocument(): Update[EditorModel, EditorOutput] = {
    val d = Build.beginDocument(Detached.s("root"))
    Update(
      EditorModel(doc = d, selectedId = Some(d.rootId), status = "New document"),
      Vector(DocChanged(d), Status("New document")))
  }

  def sample(): Update[EditorModel, EditorOutput] = {
    val nm = EditorSample.model
    Update(nm, Vector(DocChanged(nm.doc), Status(nm.status)))
  }

  def undo(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.undoStack match {
      case prev :: rest =>
        val nm = m.copy(
          doc = prev,
          redoStack = m.doc :: m.redoStack,
          undoStack = rest,
          editingId = None,
          selectedId = m.selectedId.filter(id => prev.getNode(id).isDefined),
          status = "Undo")
        Update(nm, Vector(DocChanged(prev), Status("Undo")))
      case Nil => status(m, "Nothing to undo")
    }

  def redo(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.redoStack match {
      case next :: rest =>
        val nm = m.copy(
          doc = next,
          undoStack = m.doc :: m.undoStack,
          redoStack = rest,
          editingId = None,
          selectedId = m.selectedId.filter(id => next.getNode(id).isDefined),
          status = "Redo")
        Update(nm, Vector(DocChanged(next), Status("Redo")))
      case Nil => status(m, "Nothing to redo")
    }
}
