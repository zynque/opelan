package opelan.ui.editor

import opelan.foundation.document._
import opelan.foundation.version._

// Shared history pushed down by a synced session: the Automerge change
// graph. Each `SyncedEntry` is one change (author-tagged via `label`)
// with the document it produced; `deps` are parent change hashes, so a
// two-dep entry becomes a merge version. `heads` is the graph's current
// frontier — the version the live doc corresponds to. Entries arrive in
// dependency order, identical on every peer once converged.
case class SyncedEntry(
    hash: String, deps: Vector[String], label: String,
    doc: Document[NodeData])
case class SyncedHistory(entries: Vector[SyncedEntry], heads: Vector[String])

object EditorSynced {

  // Rebuild the version DAG from a synced change graph: entry order is
  // dependency order, so parents always precede children — entry i lands
  // as history node i. Two-dep changes become merge versions (the CRDT
  // counterpart of editing on divergent heads); a rare extra root (two
  // independently created docs later synced) hangs off the previous
  // entry rather than being dropped. The current version is the last
  // head — heads collapse into the next change, so which is "current"
  // under divergence is cosmetic anyway. Returns None for an empty graph.
  def build(
      h: SyncedHistory): Option[(Document[Version[EditorSnapshot]], Int)] =
    h.entries.headOption.map { first =>
      val idxOf = h.entries.map(_.hash).zipWithIndex.toMap
      val hist = h.entries.tail.foldLeft(
        Build.beginDocument(
          Version.buildRootVersionNode(
            EditorSnapshot(first.doc, first.label)))) { (hg, e) =>
        val snap = EditorSnapshot(e.doc, e.label)
        val parents = e.deps.flatMap(idxOf.get).distinct
        val written = parents match {
          case Vector()      => // extra root: hang off the previous entry
            VersionTree.update(hg.nodes.length - 1, snap, hg)
          case Vector(p)     => VersionTree.update(p, snap, hg)
          // deps beyond the second aren't representable in a version node
          case Vector(p, q, _*) => VersionTree.merge(p, q, snap, hg)
        }
        written.getOrElse(hg) // an insert can't fail here; if it did, drop the entry
      }
      val current = h.heads.lastOption.flatMap(idxOf.get)
        .getOrElse(hist.nodes.length - 1)
      (hist, current)
    }
}
