package opelan.ui.editor

import opelan.foundation.document._
import opelan.ui.fp.Update
import EditorInput._
import EditorOutput._

// The pure transition function: (model, input) => new model + outputs.
// All document edits funnel through applyEdit, which pushes undo.
object EditorUpdate {

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
      case Undo             => EditorHistory.undo(m)
      case Redo             => EditorHistory.redo(m)
      case GoToVersion(v)   => EditorHistory.goTo(m, v, s"Version #$v")
      case NewDocument      => EditorSession.newDocument()
      case LoadDocument(d)  => EditorSession.loadDocument(d)
      case SyncDocument(d, sel, ml, h) =>
        EditorSession.syncDocument(m, d, sel, ml, h)
      case EditorInput.FollowRef => EditorDocOps.followRef(m)
      case LoadSample       => EditorSession.sample()
      case Compact          => EditorDocOps.compact(m)
    }

  // A status-only update.
  private[editor] def status(
      m: EditorModel, msg: String): Update[EditorModel, EditorOutput] =
    Update(m.copy(status = msg), Vector(Status(msg)))

  // Shared edit plumbing: on success record a version, update
  // selection/status, emit the doc output + Status; on failure report the
  // error. `extra` adjusts the new model on success only (e.g. entering
  // edit mode).
  //
  // An edit made while checked out on an old version of a synced doc is a
  // fork, not an edit: it emits BranchRequested so the owner can open a
  // separate draft — applying it to the shared frontier would silently
  // revert collaborators' work under their feet.
  private[editor] def applyEdit(
      m: EditorModel,
      result: Either[String, Document[NodeData]],
      msg: String,
      selectAfter: Option[Int] = None,
      extra: EditorModel => EditorModel = identity): Update[EditorModel, EditorOutput] =
    result match {
      case Right(d) =>
        val nm = extra(EditorHistory.record(m, d, msg).copy(
          selectedId = selectAfter.orElse(m.selectedId),
          status = msg))
        if (m.syncedHead.exists(_ != m.versionId))
          Update(nm, Vector(
            BranchRequested(d), Status("Editing old version — branching")))
        else
          Update(nm, Vector(DocChanged(d), Status(msg)))
      case Left(err) =>
        status(m, err)
    }
}
