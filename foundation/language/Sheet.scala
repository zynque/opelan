package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.view.ViewTree
import opelan.foundation.view.ViewTree._

// The sheet document schema: the content subtree is a "sheet" node whose
// children are rows — "row", or "hrow" for a header row — and a row's
// children are the cells. A cell holds its content directly: a literal,
// a label (a plain string leaf), a hole, or an expression subtree that
// may reference other cells via InternalNodeRef leaves.
//
//   sheet
//     hrow    "item"  "qty"  "price"  "line total"
//     row     "Apples"  4    1.20     mul(ref:qtyCell, ref:priceCell)
//     ...

val SheetTag = "sheet"
val RowTag = "row"
val HRowTag = "hrow"

// The "sheet" view: a grid of evaluated cells. A string leaf is a label
// rendered as-is; anything else is read as an expression and evaluated —
// refs to other cells compose through the document resolver, so a
// formula cell's value is its target's evaluated value.
def sheetView(doc: Document[NodeData], sheetId: Int): DetachedNode[NodeData] =
  grid(doc.childrenOf(sheetId).map(r => rowView(doc, r, header = isHRow(doc, r)))*)

// The "formulas" view: the same grid, showing each cell's formula
// (print view of its expression) rather than its value.
def formulasView(doc: Document[NodeData], sheetId: Int): DetachedNode[NodeData] =
  grid(doc.childrenOf(sheetId).map(r => formulaRow(doc, r, isHRow(doc, r)))*)

private def isHRow(doc: Document[NodeData], rowId: Int): Boolean =
  doc.getNode(rowId).exists(_.data == NodeData.StringData(HRowTag))

private def rowView(
    doc: Document[NodeData], rowId: Int,
    header: Boolean): DetachedNode[NodeData] =
  row(doc.childrenOf(rowId).map(cid => cellView(doc, cid, header))*)

private def formulaRow(
    doc: Document[NodeData], rowId: Int,
    header: Boolean): DetachedNode[NodeData] =
  row(doc.childrenOf(rowId).map(cid => formulaCell(doc, cid, header))*)

private def cellView(
    doc: Document[NodeData], cellId: Int,
    header: Boolean): DetachedNode[NodeData] =
  mkCell(header, cellId, valueOf(doc, cellId))

private def formulaCell(
    doc: Document[NodeData], cellId: Int,
    header: Boolean): DetachedNode[NodeData] =
  mkCell(header, cellId,
    if (isLabel(doc, cellId)) txt(labelOf(doc, cellId))
    else printTree(readLocExpr(doc, cellId)))

private def mkCell(
    header: Boolean, cellId: Int,
    content: DetachedNode[NodeData]*): DetachedNode[NodeData] =
  if (header) hcell((src(cellId) +: content)*) else cell((src(cellId) +: content)*)

// A cell's displayed value: labels show their text; expression cells
// evaluate, and a blocked cell shows the hole it's waiting on — linked
// to the blocking node, which may be inside a referenced cell.
private def valueOf(
    doc: Document[NodeData], cellId: Int): DetachedNode[NodeData] =
  if (isLabel(doc, cellId)) txt(labelOf(doc, cellId))
  else
    evalLoc(readLocExpr(doc, cellId), docResolver(doc, Set(cellId))) match {
      case Right(n)           => txt(Num.show(n))
      case Left((msg, srcId)) => from(srcId, hole(msg))
    }

// A plain string leaf is a label. (An op-tag leaf like "add" alone is
// also a label here — a bare tag is a label that happens to be named.)
private def isLabel(doc: Document[NodeData], cellId: Int): Boolean =
  doc.getNode(cellId).exists(n =>
    n.childIds.isEmpty && n.data.isInstanceOf[NodeData.StringData])

private def labelOf(doc: Document[NodeData], cellId: Int): String =
  doc.getNode(cellId).map(_.data).collect {
    case NodeData.StringData(t) => t
  }.getOrElse("")
