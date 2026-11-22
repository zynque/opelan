package opelan.examples.sheet

import opelan.examples.isTerm
import opelan.examples.expr.exprReading
import opelan.foundation.document._
import opelan.foundation.view.ViewTree._
import opelan.foundation.langdef.{RuleReading, ViewContext}

// The sheet document layout: the content subtree is a `sheet` node whose
// children are rows — `row`, or `hrow` for a header row — and a row's
// children are the cells. A cell holds its content directly: a literal,
// a label (a plain string leaf), a gap, or an expression subtree that
// references other cells via `ref` constructs (`ref > column, row` —
// resolved positionally at read time, GridExpr). `sheet`, `row`
// and `hrow` are lang: sheet constructs and the operators lang:
// expression ones, all used by reference through the imports section.
//
//   sheet
//     hrow    "item"  "qty"  "price"  "line total"
//     row     "Apples"  4    1.20     mul(&qtyCell, &priceCell)
//     ...

// The "sheet" view: a grid of evaluated cells. A string leaf is a label
// rendered as-is; anything else is read as an expression and evaluated —
// refs to other cells compose through the document resolver, so a
// formula cell's value is its target's evaluated value. Both views
// render the healed sheet (healGaps): gap cells the current cell
// grammar can read show as the formulas they now are.
def sheetView(
    doc: Document[NodeData], sheetId: Int,
    ctx: ViewContext = ViewContext()): DetachedNode[NodeData] = {
  val d = resolveGridRefs(healGaps(doc, sheetId, ctx.parseInput), sheetId)
  val currency = ctx.settings.get("currency")
  val reading = ctx.reading.getOrElse(exprReading)
  grid((List(deleteControlsRow(d, sheetId)) ++
      d.childrenOf(sheetId).map(r =>
        rowView(d, sheetId, r, isHRow(d, r), currency, reading,
          ctx.termName)) :+
      row(controls(action(sheetId, "add-row", txt("+")))))*)
}

// The "formulas" view: the same grid, showing each cell's formula
// (print view of its expression) rather than its value.
def formulasView(
    doc: Document[NodeData], sheetId: Int,
    ctx: ViewContext = ViewContext()): DetachedNode[NodeData] = {
  val d = healGaps(doc, sheetId, ctx.parseInput)
  grid(d.childrenOf(sheetId).map(r =>
    formulaRow(d, r, isHRow(d, r), ctx.reading.getOrElse(exprReading), ctx.termName))*)
}

private def isHRow(doc: Document[NodeData], rowId: Int): Boolean =
  isTerm(sheetTerms, HRowTag, doc, rowId)

// The controls row above the header: a blank over the left
// row-delete column, then a circular − above each column.
private def deleteControlsRow(
    doc: Document[NodeData], sheetId: Int): DetachedNode[NodeData] =
  row((List(controls()) ++ doc.childrenOf(sheetId).flatMap(r =>
      if (isHRow(doc, r)) doc.childrenOf(r) else List())
    .map(cid => controls(action(cid, "remove-column", txt("−")))))*)

private def rowView(
    doc: Document[NodeData], sheetId: Int, rowId: Int,
    header: Boolean, currency: Option[String], reading: RuleReading,
    nameOf: ExternalNodeReference => Option[String])
    : DetachedNode[NodeData] = {
  val cells = doc.childrenOf(rowId).map(cid =>
    cellView(doc, cid, header, currency, reading, nameOf))
  val left =
    if (header) List(controls())
    else List(controls(action(rowId, "remove-row", txt("−"))))
  val right =
    if (header) List(controls(action(sheetId, "add-column", txt("+"))))
    else List()
  row((left ++ cells ++ right)*)
}

private def formulaRow(
    doc: Document[NodeData], rowId: Int,
    header: Boolean, reading: RuleReading,
    nameOf: ExternalNodeReference => Option[String])
    : DetachedNode[NodeData] =
  row(doc.childrenOf(rowId).map(cid =>
    formulaCell(doc, cid, header, reading, nameOf))*)

private def cellView(
    doc: Document[NodeData], cellId: Int,
    header: Boolean, currency: Option[String], reading: RuleReading,
    nameOf: ExternalNodeReference => Option[String])
    : DetachedNode[NodeData] =
  mkCell(header, cellId,
    valueOf(doc, cellId, currency, reading, nameOf))

private def formulaCell(
    doc: Document[NodeData], cellId: Int,
    header: Boolean, reading: RuleReading,
    nameOf: ExternalNodeReference => Option[String])
    : DetachedNode[NodeData] =
  mkCell(header, cellId,
    input(cellId, txt(cellText(doc, cellId, nameOf, reading))))

private def mkCell(
    header: Boolean, cellId: Int,
    content: DetachedNode[NodeData]*): DetachedNode[NodeData] =
  if (header) hcell((src(cellId) +: content)*) else cell((src(cellId) +: content)*)
