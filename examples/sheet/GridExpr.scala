package opelan.examples.sheet

import opelan.examples.termOf
import opelan.examples.expr.{evalLoc, readLocExpr, LocExpr, Num}
import opelan.foundation.document._

// Reading `ref` constructs in a sheet's grid: a `ref` node — `ref >
// column("B"), row(3)` — resolves positionally at read time against the
// live grid, never baked into a node id. `readGridExpr` rewrites a
// cell's located expression so each ref reads as a reference to the
// cell it names (a LocRef to the target's node — evaluation then
// composes through the ordinary doc resolver machinery); a name
// outside the grid reads as a gap showing the name.

// A cell's expression with its `ref` constructs resolved to the cells
// they name.
def readGridExpr(doc: Document[NodeData], cellId: Int): LocExpr =
  resolveRefs(doc, cellId, readLocExpr(doc, cellId))

// The ref resolver for grid expressions — `docResolver` over grid
// reads: a referenced cell's own expression, with its `ref` constructs
// resolved, supplies the value; the visiting set reports cycles.
def gridResolver(
    doc: Document[NodeData],
    visiting: Set[Int]): Int => Either[(String, Int), Num] =
  target =>
    if (visiting(target)) Left(("cycle", target))
    else evalLoc(readGridExpr(doc, target),
      gridResolver(doc, visiting + target))

private def resolveRefs(
    doc: Document[NodeData], cellId: Int, e: LocExpr): LocExpr =
  e match {
    case LocExpr.LocGap(id, text) =>
      refCellTarget(doc, cellId, id) match {
        case Some(target) => LocExpr.LocRef(id, target)
        case None         => LocExpr.LocGap(id, refNodeName(doc, id).getOrElse(text))
      }
    case LocExpr.LocAdd(id, l, r) =>
      LocExpr.LocAdd(id, resolveRefs(doc, cellId, l), resolveRefs(doc, cellId, r))
    case LocExpr.LocSub(id, l, r) =>
      LocExpr.LocSub(id, resolveRefs(doc, cellId, l), resolveRefs(doc, cellId, r))
    case LocExpr.LocMul(id, l, r) =>
      LocExpr.LocMul(id, resolveRefs(doc, cellId, l), resolveRefs(doc, cellId, r))
    case LocExpr.LocDiv(id, l, r) =>
      LocExpr.LocDiv(id, resolveRefs(doc, cellId, l), resolveRefs(doc, cellId, r))
    case _ => e
  }

// The cell a `ref` node names, resolved in `cellId`'s grid.
private def refCellTarget(
    doc: Document[NodeData], cellId: Int, refId: Int): Option[Int] =
  for {
    (row, col) <- refPosition(doc, refId)
    sheetId    <- enclosingSheet(doc, cellId)
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

private def leafInt(doc: Document[NodeData], fieldId: Int): Option[Int] =
  doc.childrenOf(fieldId).headOption
    .flatMap(id => doc.getNode(id).map(_.data))
    .collect { case NodeData.IntData(i) => i }
