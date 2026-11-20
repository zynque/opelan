package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.grammar._

// The text surface's character provenance — for each character of the
// editor's text, the chain of document nodes it came from, innermost
// first (a token's leaf, its piece, the pattern, the definition…). The
// chain is frozen when it is written: a node keeps its identity while
// a parsed node's span still holds characters whose chain passes
// through it — text typed in front of `a * b means mul(a, b)` doesn't
// make it a new rule — and a span left holding none is a new node.
//
// Two rules keep a chain honest (ProvenanceStamp):
//   * a character that already has one keeps it — a node that printed
//     text owns that text until the text is deleted, even while
//     another node temporarily covers it (a `means` line merged into
//     the rule above it splits back out with its identity intact);
//   * a node claims unowned characters only while it is new — a
//     retained node never extends its ownership into characters it
//     did not already own, so a child that borrowed a parent's
//     characters can't vote the parent into a different construct.
//
// `doc` is the document the chain ids refer to — a provenance is only
// meaningful for that document, so a host checks identity (`eq`),
// never text coincidence. `owners` has one entry per character of
// `text`; an empty chain means nobody owns it (typed since the last
// commit, or a separator like a newline).
case class TextProvenance(
    doc: Document[NodeData],
    text: String,
    owners: Vector[List[Int]]) {

  // The provenance after the text became `next`: the edit is the span
  // between the texts' common prefix and suffix; characters outside it
  // keep their owners, characters inside have none.
  def retext(next: String): TextProvenance = {
    val prefix = text.lazyZip(next).takeWhile(_ == _).size
    val room = text.length.min(next.length) - prefix
    val suffix = (0 until room).takeWhile(i =>
      text.charAt(text.length - 1 - i) ==
        next.charAt(next.length - 1 - i)).length
    copy(text = next, owners = owners.take(prefix) ++
      Vector.fill(next.length - prefix - suffix)(Nil) ++
      owners.drop(text.length - suffix))
  }

  // The node each parsed node continues, as commit hints: the old node
  // whose original characters fill most of the new node's span —
  // one-to-one, best match first, innermost nodes first on a tie —
  // provided it builds the same construct (the check commitContent
  // applies to hints).
  def hints(spanned: SpannedTree[NodeData]): Map[List[Int], Int] = {
    val entries = importEntries(doc).toMap
    def externalData(id: Int): Option[NodeData] =
      doc.getNode(id).map(_.data).map {
        case NodeData.InternalNodeRef(e) if entries.contains(e) =>
          NodeData.ExternalNodeRef(entries(e))
        case data => data
      }
    val nodes = spannedNodes(spanned)
    val order = nodes.map(_._1).zipWithIndex.toMap
    nodes.flatMap { case (path, data, span) =>
      (span.from until span.to.min(owners.length)).iterator
        .flatMap(i => owners(i).filter(id => externalData(id).contains(data)))
        .toList.groupBy(identity)
        .map { case (id, chars) => (path, id, chars.size, span.to - span.from) }
    }.sortBy { case (path, _, n, size) => (-n, size, order(path)) }
      .foldLeft((Map.empty[List[Int], Int], Set.empty[Int])) {
        case ((acc, used), (path, id, _, _)) =>
          if (acc.contains(path) || used(id)) (acc, used)
          else (acc + (path -> id), used + id)
      }._1
  }

  // A commit placed the document's nodes over the parsed spans: each
  // still-unowned character's chain grows to its deepest covering
  // node — the commit's own account of who produced the character.
  def adopt(
      committed: Document[NodeData], rootId: Int,
      spanned: SpannedTree[NodeData]): TextProvenance =
    TextProvenance(committed, text,
      stamp(committed, rootId, spanned, owners))
}

private[language] def spannedNodes(
    t: SpannedTree[NodeData], path: List[Int] = Nil)
    : List[(List[Int], NodeData, Span)] =
  (path, t.data.data, t.data.span) ::
    t.children.zipWithIndex.flatMap { case (c, i) =>
      spannedNodes(c, path :+ i)
    }
