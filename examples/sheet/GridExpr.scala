package opelan.examples.sheet

import opelan.examples.termOf
import opelan.examples.expr.gapText
import opelan.foundation.document._
import opelan.foundation.document.typed._

// Reading `ref` constructs in a sheet's grid: a `ref` node — `ref >
// column("B"), row(3)` — resolves positionally at read time against the
// live grid, never baked into a node id. `resolveGridRefs` projects the
// document: each `ref` node under `rootId` becomes an
// `InternalNodeRef` leaf naming the cell it resolves to — keeping its
// id, so provenance survives — or a gap leaf showing the name it
// spelled when the target sits outside the grid. The projected document
// is what the derived views evaluate and print: `evalNode` follows the
// internal refs, and its visiting set reports ref cycles as gaps.

// The document with every `ref` construct under `rootId` rewritten to
// the internal ref its position names.
def resolveGridRefs(
    doc: Document[NodeData], rootId: Int): Document[NodeData] =
  doc.preorder(rootId).map(_._1).foldLeft(doc) { (d, id) =>
    if (!termOf(sheetTerms, d, id).contains(RefTag)) d
    else {
      val content = refCellTarget(d, id) match {
        case Some(target) =>
          DetachedNode.leaf(NodeData.InternalNodeRef(target))
        case None =>
          DetachedNode.leaf(NodeData.GapData(refNodeName(d, id)
            .getOrElse(d.getNode(id).map(n => gapText(n.data))
              .getOrElse("ref"))))
      }
      commitContent(id, content, d).getOrElse(d)
    }
  }

// The cell a `ref` node names, resolved in its enclosing sheet's grid.
private def refCellTarget(
    doc: Document[NodeData], refId: Int): Option[Int] =
  for {
    (row, col) <- refPosition(doc, refId)
    sheetId    <- enclosingSheet(doc, refId)
    target     <- cellAt(doc, sheetId, row, col)
  } yield target

// The name a `ref` node spells ("Z99") — for display when the target
// sits outside the grid.
def refNodeName(doc: Document[NodeData], refId: Int): Option[String] =
  refPosition(doc, refId).map { case (row, col) => cellRefName(row, col) }

// (row, column) a `ref` node names — zero-based — read from its field
// children; None when the node isn't a legible ref construct.
def refPosition(doc: Document[NodeData], refId: Int): Option[(Int, Int)] =
  if (!termOf(sheetTerms, doc, refId).contains(RefTag)) None
  else for {
    col <- field(doc, refId, 0).flatMap(leafString(doc, _))
         .map(ls => ls.toUpperCase.foldLeft(0)((n, ch) => n * 26 + (ch - 'A' + 1)) - 1)
    row <- field(doc, refId, 1).flatMap(leafInt(doc, _))
  } yield (row - 1, col)

private def field(
    doc: Document[NodeData], refId: Int, i: Int): Option[Int] =
  doc.childrenOf(refId).lift(i)

private def leafString(
    doc: Document[NodeData], fieldId: Int): Option[String] =
  doc.childrenOf(fieldId).headOption
    .flatMap(id => doc.getNode(id).map(_.data))
    .collect { case NodeData.StringData(s) => s }

private def leafInt(
    doc: Document[NodeData], fieldId: Int): Option[Int] =
  doc.childrenOf(fieldId).headOption
    .flatMap(id => doc.getNode(id).map(_.data))
    .collect { case NodeData.IntData(i) => i }
