package opelan.examples.sheet

import opelan.examples.termOf
import opelan.foundation.document._
import opelan.foundation.document.Detached._

// A1-style cell references: a column letter and a row number ("D5").
// The document fully reflects the semantics — a cell ref is a `ref`
// construct holding its `column` and `row` fields:
//
//   ref
//     column "B"
//     row 3
//
// Positions are resolved at read time against the live grid (GridExpr),
// never baked into a node id, so refs track moved cells. Text can't
// carry the construct, so a typed name commits as a gap or label leaf
// and `linkCellRefs` — the sheet's commit link (Impls.commitLink, run
// from Language.linkCommitted) — materializes the `ref` subtree.

private val CellName = "([A-Za-z]+)(\\d+)".r
private val RefText = "ref:(\\d+)".r

// Whether text is a whole cell name ("D5", case-insensitive).
def isCellRefName(text: String): Boolean = cellRefPosition(text).isDefined

// "D5" -> (row 4, column 3), zero-based; letters are bijective
// base-26 (A=0 .. Z=25, AA=26). Rows count from 1 — a sheet's header
// row is row 1 like any other.
def cellRefPosition(text: String): Option[(Int, Int)] = text.trim match {
  case CellName(letters, row) =>
    row.toIntOption.filter(_ >= 1)
      .map(_ - 1 -> columnIndex(letters))
  case _ => None
}

private def columnIndex(letters: String): Int =
  letters.toUpperCase.foldLeft(0)((n, ch) => n * 26 + (ch - 'A' + 1)) - 1

// (row 4, column 3) -> "D5".
def cellRefName(row: Int, col: Int): String = s"${letters(col)}${row + 1}"

private def letters(col: Int): String =
  if (col < 0) "" else letters(col / 26 - 1) + ('A' + col % 26).toChar

// The detached form of a cell reference — `ref(column("B"), row(3))` —
// the construct a name like "B3" denotes.
def cellRefTree(letters: String, row: Int): DetachedNode[NodeData] =
  n(sheetTag(RefTag),
    n(sheetTag(ColumnTag), sl(letters.toUpperCase)),
    n(sheetTag(RowTag), il(row)))

def cellRefTree(row: Int, col: Int): DetachedNode[NodeData] =
  cellRefTree(letters(col), row + 1)

// The sheet a node belongs to: its nearest ancestor (or itself)
// applying the `sheet` construct.
def enclosingSheet(doc: Document[NodeData], id: Int): Option[Int] =
  LazyList.iterate(Option(id))(_.flatMap(doc.parentOf))
    .takeWhile(_.isDefined).flatten
    .find(n => termOf(sheetTerms, doc, n).contains(SheetTag))

// The cell at (row, col) — zero-based — in a sheet's grid.
def cellAt(
    doc: Document[NodeData], sheetId: Int,
    row: Int, col: Int): Option[Int] =
  doc.childrenOf(sheetId).lift(row)
    .flatMap(r => doc.childrenOf(r).lift(col))

// The grid position `id` occupies under `sheetId`, as a cell name.
def cellName(doc: Document[NodeData], sheetId: Int, id: Int): Option[String] =
  doc.childrenOf(sheetId).zipWithIndex.collectFirst {
    case (rowId, row) if doc.childrenOf(rowId).contains(id) =>
      cellRefName(row, doc.childrenOf(rowId).indexOf(id))
  }

// The surface text of an `InternalNodeRef` leaf seen from `cellId`
// (the `ref:N` escape form, kept beside cell refs): the target's cell
// name within the enclosing sheet, `ref:N` outside the grid.
def refText(doc: Document[NodeData], cellId: Int, target: Int): String =
  enclosingSheet(doc, cellId).flatMap(cellName(doc, _, target))
    .getOrElse(s"ref:$target")

// `ref:N` spans in rendered formula text become the target's cell name —
// positions are derived at render, so names track moved cells.
def nameRefs(
    doc: Document[NodeData], cellId: Int, text: String): String =
  RefText.replaceAllIn(text, m => refText(doc, cellId, m.group(1).toInt))

// A committed subtree with each cell-name leaf — a gap or label the
// parse left holding "D5" — materialized as the `ref` construct it
// names: the reference structure text couldn't carry. Pure, so it runs
// on parsed content before the commit lands (Impls.commitLink) and on
// healed content in derived views alike.
def linkCellRefs(t: DetachedNode[NodeData]): DetachedNode[NodeData] = {
  val text = t.data match {
    case _ if t.children.nonEmpty   => None
    case NodeData.GapData(s)        => Some(s)
    case NodeData.StringData(s)     => Some(s)
    case _                          => None
  }
  val ref = text.flatMap(_.trim match {
    case CellName(ls, rs) =>
      rs.toIntOption.filter(_ >= 1).map(cellRefTree(ls, _))
    case _ => None
  })
  ref.getOrElse(DetachedNode(t.data, t.children.map(linkCellRefs)))
}
