package opelan.collaboration.automerge

import scala.scalajs.js

// Inverse-splice computation for "undo my change": what a given change did
// to the doc's `text`, expressed at the doc's *current* frontier. The
// change's before/after texts are diffed to a single span (longest common
// prefix/suffix), then its positions are moved forward through Automerge
// cursors so concurrent remote edits shift them correctly.
//
// A change can only revert cleanly if the span it inserted still holds
// exactly that text — a collaborator's edit inside it would otherwise be
// deleted too, so the revert is declined in that case (None).
object Revert {

  // (pos, delLen, insert) to apply at the current frontier to undo the
  // named change. None when the change had no text effect, the hash is
  // unknown, or the span can't be reverted without clobbering a peer.
  def inverseOf(doc: js.Dynamic, hash: String): Option[(Int, Int, String)] =
    Automerge.historyOf(doc).find(_.hash == hash).flatMap { entry =>
      val before = Automerge.textAtHeads(doc, entry.deps)
      val afterDoc = Automerge.view(doc, Vector(hash))
      val after = Automerge.textOf(afterDoc)
      val (p, s) = commonEdges(before, after)
      val myDel = before.substring(p, before.length - s)
      val myIns = after.substring(p, after.length - s)
      if (myDel.isEmpty && myIns.isEmpty) None
      else {
        // Anchor the start to the last element of the common prefix — a
        // context element that survives the change (the span's own edges
        // are unreliable anchors: a tombstoned element resolves on the
        // wrong side of a replacement at the same position). The end is
        // just ps + myIns.length — the content check below guarantees the
        // span still holds exactly what the change inserted.
        val ps =
          if (p == 0) 0
          else elementPosition(afterDoc, doc, p - 1) + 1
        val pe = ps + myIns.length
        if (pe <= Automerge.textOf(doc).length &&
            Automerge.textOf(doc).substring(ps, pe) == myIns)
          Some((ps, pe - ps, myDel))
        else None
      }
    }

  private def commonEdges(a: String, b: String): (Int, Int) = {
    var p = 0
    val max = math.min(a.length, b.length)
    while (p < max && a.charAt(p) == b.charAt(p)) p += 1
    var s = 0
    while (s < max - p &&
           a.charAt(a.length - 1 - s) == b.charAt(b.length - 1 - s)) s += 1
    (p, s)
  }

  // The position of element `index` (from the after-change view) in the
  // live doc. Cursor lookup can fail on degenerate positions (e.g. empty
  // text) — fall back to the raw index clamped into range.
  private def elementPosition(
      afterDoc: js.Dynamic, doc: js.Dynamic, index: Int): Int =
    try Automerge.cursorPosition(doc, Automerge.cursorAt(afterDoc, index))
    catch { case _: Throwable => math.min(index, Automerge.textOf(doc).length) }
}
