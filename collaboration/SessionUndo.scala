package opelan.collaboration

import opelan.collaboration.automerge.{Automerge, Revert}

// Per-user undo for a synced session. Ctrl+Z means "take back *my* last
// change": `undoable` holds the hashes this session authored (in order),
// and undo appends a revert change computed by Revert.inverseOf — an
// inverse splice translated to the current frontier, so peers' interleaved
// edits survive. The revert is an ordinary change: it syncs normally and
// never disturbs anyone's view.
//
// The undo change itself is not pushed back on `undoable` — it's stashed
// on `redoable` paired with the hash it reverted, so a second undo walks
// further back through my changes rather than toggling the first undo.
// Redo pops that stack by reverting the undo change (re-applying the
// original effect). Any new local edit clears the redo stack; the history
// graph itself never loses anything — a cleared redo is still reachable
// by browsing.
//
// Stacks are session-local: they start empty on attach/resume (rebuilding
// them from actor-tagged history is possible future work), and undoing a
// change a collaborator already rewrote reports failure rather than
// clobbering.
trait SessionUndo { self: DocSession =>

  private var undoable = Vector.empty[String]
  private var redoable = Vector.empty[(String, String)] // (undoHash, origHash)

  // Called after a local change lands — it becomes the newest undoable
  // unit and forfeits any pending redos.
  protected def pushedLocalChange(): Unit = {
    undoable :+= Automerge.headsOf(doc).head
    redoable = Vector.empty
  }

  def undo(): String =
    if (doc == null) "Nothing to undo"
    else undoable.lastOption match {
      case None => "Nothing to undo"
      case Some(h) =>
        Revert.inverseOf(doc, h) match {
          case Some((pos, del, ins)) =>
            doc = Automerge.spliceText(doc, pos, del, ins, s"$name (undo)")
            undoable = undoable.dropRight(1)
            redoable :+= (Automerge.headsOf(doc).head -> h)
            publish()
            "Undone"
          case None =>
            "Can't undo — a collaborator changed that text"
        }
    }

  def redo(): String =
    if (doc == null) "Nothing to redo"
    else redoable.lastOption match {
      case None => "Nothing to redo"
      case Some((undoHash, origHash)) =>
        Revert.inverseOf(doc, undoHash) match {
          case Some((pos, del, ins)) =>
            doc = Automerge.spliceText(doc, pos, del, ins, s"$name (redo)")
            redoable = redoable.dropRight(1)
            undoable :+= origHash
            publish()
            "Redone"
          case None => "Can't redo"
        }
    }
}
