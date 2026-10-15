package opelan.examples

import opelan.foundation.document._
import opelan.foundation.view.ViewTree
import opelan.foundation.view.ViewTree._

// The sheet document schema: the content subtree is a `sheet` node whose
// children are rows — `row`, or `hrow` for a header row — and a row's
// children are the cells. A cell holds its content directly: a literal,
// a label (a plain string leaf), a hole, or an expression subtree that
// may reference other cells via InternalNodeRef leaves. `sheet`, `row`
// and `hrow` are lang: sheet constructs and the operators lang:
// expression ones, all used by reference through the imports section.
//
//   sheet
//     hrow    "item"  "qty"  "price"  "line total"
//     row     "Apples"  4    1.20     mul(&qtyCell, &priceCell)
//     ...

// Kind names — keys into sheetKinds, never stored.
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
  kindOf(sheetKinds, doc, rowId).contains(HRowTag)

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
  mkCell(header, cellId, input(cellId, txt(cellText(doc, cellId))))

private def mkCell(
    header: Boolean, cellId: Int,
    content: DetachedNode[NodeData]*): DetachedNode[NodeData] =
  if (header) hcell((src(cellId) +: content)*) else cell((src(cellId) +: content)*)

// A cell's displayed value. Leaf cells — labels, literals, holes — are
// editable projections (`input`): typing a new value rewrites the source
// node. Refs and formulas stay read-only: their displayed value is the
// evaluation, with a hole linked to the blocking node when they can't
// compute.
private def valueOf(
    doc: Document[NodeData], cellId: Int): DetachedNode[NodeData] =
  doc.getNode(cellId) match {
    case Some(node)
        if node.childIds.isEmpty &&
           !node.data.isInstanceOf[NodeData.InternalNodeRef] =>
      input(cellId, txt(cellText(doc, cellId)))
    case _ => evalCell(doc, cellId)
  }

private def evalCell(
    doc: Document[NodeData], cellId: Int): DetachedNode[NodeData] =
  evalLoc(readLocExpr(doc, cellId), docResolver(doc, Set(cellId))) match {
    case Right(n)           => txt(Num.show(n))
    case Left((msg, srcId)) => from(srcId, hole(msg))
  }

// The surface text a cell's projected editor shows — in both views —
// matching what parseCellText produces on commit, so an unchanged edit
// is a genuine no-op: labels show bare, holes show `?text`, refs show
// `ref:N`, and formulas show their printed expression.
def cellText(doc: Document[NodeData], cellId: Int): String =
  doc.getNode(cellId) match {
    case Some(node) if node.childIds.isEmpty => node.data match {
      case NodeData.StringData(t)      => t
      case NodeData.IntData(i)         => i.toString
      case NodeData.FloatData(f)       => Num.show(Num.F(f))
      case NodeData.GapData(t)         => s"?$t"
      case NodeData.InternalNodeRef(n) => s"ref:$n"
      case other                       => NodeDataText.show(other)
    }
    case _ => ViewTree.flatten(printTree(readLocExpr(doc, cellId)))
  }

// The sheet's cell grammar (Language.parseCell): text committed in a
// cell's box becomes the cell's new content. A leading '?' writes a hole
// (matching the outline codec's `?text` gaps); otherwise a full
// expression parses to its subtree — numbers, refs, whole formulas;
// and anything that doesn't parse at all is a label — in a sheet, plain
// text is a string value, not an error.
def parseCellText(text: String): DetachedNode[NodeData] =
  if (text.startsWith("?")) DetachedNode.leaf(NodeData.GapData(text.drop(1)))
  else if (text.trim.isEmpty) DetachedNode.leaf(NodeData.StringData(""))
  else parseExpr(text).expr match {
    case _: Expr.Hole => DetachedNode.leaf(NodeData.StringData(text))
    case e            => exprToDetached(e)
  }
