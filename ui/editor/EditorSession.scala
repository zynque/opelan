package opelan.ui.editor

import opelan.foundation.document._
import opelan.ui.fp.Update
import EditorOutput._
import EditorUpdate.applyEdit

// Editing-session transitions: selection movement, entering/leaving inline
// edit mode, and loading documents. History lives in EditorHistory.
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
      EditorModel.forDocument(d, status = "Document loaded"),
      Vector(DocChanged(d), Status("Document loaded")))

  // Parent-driven sync: replace the document without reporting it back.
  // `select` optionally picks a node in the new document — ignored if the
  // id doesn't exist there.
  //
  // A plain push opens a different document, so history restarts at a
  // fresh root. A `mergeLabel` push is the same document updated from
  // outside (a synced remote edit, a text-cell edit): record it as a new
  // version on the current history so undo walks back over it. State
  // bound to node ids (selection, an in-progress edit, a detached cut
  // node) survives only if the node still exists in the merged document.
  def syncDocument(
      m: EditorModel,
      d: Document[NodeData],
      select: Option[Int],
      mergeLabel: Option[String]): Update[EditorModel, EditorOutput] =
    mergeLabel match {
      case None =>
        Update(EditorModel.forDocument(
          d,
          selectedId = select.filter(id => d.getNode(id).isDefined),
          status = "Document synced"))
      case Some(label) =>
        if (d == m.doc) Update(m)
        else {
          val nm = EditorHistory.record(m, d, label).copy(
            selectedId =
              select.orElse(m.selectedId).filter(id => d.getNode(id).isDefined),
            editingId = m.editingId.filter(id => d.getNode(id).isDefined),
            detachedNodeId = m.detachedNodeId.filter(id => d.getNode(id).isDefined),
            status = label)
          Update(nm)
        }
    }

  def newDocument(): Update[EditorModel, EditorOutput] = {
    val d = Build.beginDocument(Detached.s("root"))
    Update(
      EditorModel.forDocument(d, Some(d.rootId), "New document"),
      Vector(DocChanged(d), Status("New document")))
  }

  def sample(): Update[EditorModel, EditorOutput] = {
    val nm = EditorSample.model
    Update(nm, Vector(DocChanged(nm.doc), Status(nm.status)))
  }
}
