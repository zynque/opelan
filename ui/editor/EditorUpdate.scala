package opelan.ui.editor

import opelan.foundation.document._
import opelan.ui.fp.Update
import EditorInput._
import EditorOutput._

// The pure transition function: (model, input) => new model + outputs.
// All document edits funnel through applyEdit, which pushes undo.
object EditorUpdate {

  val maxUndo = 100

  def apply(m: EditorModel, input: EditorInput): Update[EditorModel, EditorOutput] =
    input match {
      case Move(delta)      => EditorSession.move(m, delta)
      case Select(id)       => Update(m.copy(selectedId = Some(id)))
      case Deselect         => Update(m.copy(selectedId = None))
      case InsertSibling    => EditorDocOps.insertSibling(m)
      case InsertChild      => EditorDocOps.insertChild(m)
      case Indent           => EditorDocOps.indent(m)
      case Outdent          => EditorDocOps.outdent(m)
      case StartEdit(id)    => EditorSession.startEdit(m, id)
      case EditSelected     => EditorSession.editSelected(m)
      case CommitEdit(text) => EditorSession.commitEdit(m, text)
      case CancelEdit       => Update(m.copy(editingId = None))
      case Remove           => EditorClip.remove(m)
      case Cut              => EditorClip.cut(m)
      case Copy             => EditorClip.copy(m)
      case Paste            => EditorClip.paste(m)
      case Undo             => EditorSession.undo(m)
      case Redo             => EditorSession.redo(m)
      case NewDocument      => EditorSession.newDocument()
      case LoadDocument(d)  => EditorSession.loadDocument(d)
      case LoadSample       => EditorSession.sample()
      case Compact          => EditorDocOps.compact(m)
    }

  // A status-only update.
  private[editor] def status(
      m: EditorModel, msg: String): Update[EditorModel, EditorOutput] =
    Update(m.copy(status = msg), Vector(Status(msg)))

  // Shared edit plumbing: on success push undo, clear redo, update
  // selection/status, emit DocChanged + Status; on failure report the error.
  // `extra` adjusts the new model on success only (e.g. entering edit mode).
  private[editor] def applyEdit(
      m: EditorModel,
      result: Either[String, Document[NodeData]],
      msg: String,
      selectAfter: Option[Int] = None,
      extra: EditorModel => EditorModel = identity): Update[EditorModel, EditorOutput] =
    result match {
      case Right(d) =>
        val nm = extra(m.copy(
          doc = d,
          undoStack = (m.doc :: m.undoStack).take(maxUndo),
          redoStack = Nil,
          selectedId = selectAfter.orElse(m.selectedId),
          status = msg))
        Update(nm, Vector(DocChanged(d), Status(msg)))
      case Left(err) =>
        status(m, err)
    }
}
