package opelan.ui.editor

import opelan.foundation.document._
import opelan.foundation.version._
import opelan.ui.fp.Update
import EditorOutput._
import EditorUpdate.status

// Branching history: every document the editor produces is a node in a
// version DAG (foundation/version). Editing after an undo appends a sibling
// branch rather than truncating the future — history is never destroyed.
// `versionId` is the position in the DAG whose snapshot is the live `doc`.
//
// Undo walks to the version's parent; redo descends to the newest child
// (children are prepended on insert, so childIds.head is the most recent
// branch). Any version is reachable directly via GoToVersion.
case class EditorSnapshot(doc: Document[NodeData], label: String)

object EditorHistory {

  def initial(doc: Document[NodeData]): Document[Version[EditorSnapshot]] =
    Build.beginDocument(
      Version.buildRootVersionNode(EditorSnapshot(doc, "initial")))

  // Record a new version of the document. The version tree should never
  // reject the update (versionId is always a live node), but if it does the
  // edit still applies — losing an edit is worse than losing history.
  def record(
      m: EditorModel, newDoc: Document[NodeData], label: String): EditorModel =
    VersionTree.update(m.versionId, EditorSnapshot(newDoc, label), m.history) match {
      case Right(h) => m.copy(doc = newDoc, history = h, versionId = h.nodes.length - 1)
      case Left(_)  => m.copy(doc = newDoc)
    }

  def undo(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.history.parentOf(m.versionId) match {
      case Some(parent) => goTo(m, parent, "Undo")
      case None         => status(m, "Nothing to undo")
    }

  def redo(m: EditorModel): Update[EditorModel, EditorOutput] =
    m.history.childrenOf(m.versionId).headOption match {
      case Some(child) => goTo(m, child, "Redo")
      case None        => status(m, "Nothing to redo")
    }

  // Jump to an arbitrary version — history-panel clicks and undo/redo all
  // land here. Selection survives only if the node exists in that version;
  // an in-progress edit and a detached (cut) node are bound to the old doc
  // and are dropped.
  def goTo(
      m: EditorModel, versionId: Int, msg: String): Update[EditorModel, EditorOutput] =
    m.history.getNode(versionId).map(_.data.data.doc) match {
      case Some(d) =>
        val nm = m.copy(
          doc = d,
          versionId = versionId,
          editingId = None,
          detachedNodeId = None,
          selectedId = m.selectedId.filter(id => d.getNode(id).isDefined),
          status = msg)
        Update(nm, Vector(DocChanged(d), Status(msg)))
      case None => status(m, s"Version #$versionId does not exist")
    }
}
