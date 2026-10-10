package opelan.ui.editor

import opelan.foundation.document._
import opelan.ui.fp.Update
import EditorInput._
import EditorOutput._

// The pure transition function: (model, input) => new model + outputs.
// All document edits funnel through applyEdit, which pushes undo.
def editorUpdate(m: EditorModel, input: EditorInput): Update[EditorModel, EditorOutput, EditorInput] =
  input match {
    case Move(delta)      => move(m, delta)
    case Select(id)       => Update(m.copy(selectedId = Some(id)))
    case Deselect         => Update(m.copy(selectedId = None))
    case InsertSibling    => insertSibling(m)
    case InsertChild      => insertChild(m)
    case Indent           => indent(m)
    case Outdent          => outdent(m)
    case StartEdit(id)    => startEdit(m, id)
    case EditSelected     => editSelected(m)
    case CommitEdit(text) => commitEdit(m, text)
    case DraftEdit(text)  =>
      Update(m.copy(
        pendingInsert = m.pendingInsert.map(_.copy(draft = text))))
    case CancelEdit =>
      val sel =
        if (m.selectedId.contains(EditorModel.PendingId))
          m.pendingInsert.map(_.anchorId)
        else m.selectedId
      Update(m.copy(
        editingId = None, pendingInsert = None, selectedId = sel))
    case Remove           => remove(m)
    case Cut              => cut(m)
    case Copy             => copy(m)
    case Paste            => paste(m)
    case Undo             => undo(m)
    case Redo             => redo(m)
    case GoToVersion(v)   => goTo(m, v, s"Version #$v")
    case NewDocument      => newDocument()
    case SyncDocument(d, sel, push) =>
      syncDocument(m, d, sel, push)
    case EditorInput.FollowRef => followRef(m)
    case LoadSample       => loadSample()
    case Compact          => compactDoc(m)
  }

// A status-only update.
private[editor] def status(
    m: EditorModel, msg: String): Update[EditorModel, EditorOutput, EditorInput] =
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

// True while the user is browsing a checked-out version of a synced doc.
private[editor] def browsingStaleVersion(m: EditorModel): Boolean =
  m.syncedHead.exists(_ != m.versionId)

private[editor] def applyEdit(
    m: EditorModel,
    result: Either[String, Document[NodeData]],
    msg: String,
    selectAfter: Option[Int] = None,
    extra: EditorModel => EditorModel = identity): Update[EditorModel, EditorOutput, EditorInput] =
  result match {
    case Right(d) =>
      val nm = extra(record(m, d, msg).copy(
        selectedId = selectAfter.orElse(m.selectedId),
        status = msg))
      if (browsingStaleVersion(m))
        Update(nm, Vector(
          BranchRequested(d), Status("Editing old version — branching")))
      else
        Update(nm, Vector(DocChanged(d), Status(msg)))
    case Left(err) =>
      status(m, err)
  }
