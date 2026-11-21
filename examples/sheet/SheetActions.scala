package opelan.examples.sheet

import opelan.examples.termOf
import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.document.typed._

// The document operations the sheet view's `action:` buttons name —
// whole-document row/column edits, since `commitInput` rewrites only a
// single node. Cell refs are positional (`ref > column, row`), so a
// structural edit rewrites them like a spreadsheet does: refs past the
// edit point shift to keep naming the same cells, and a ref into a
// removed row/column gets a "REF" column — out of grid, so it
// surfaces as a `?REFn` gap instead of silently rebinding.
//
//   add-row        (emitted for the sheet) appends a blank row
//   remove-row     (emitted for a row)     removes it
//   add-column     (emitted for the sheet) appends a blank cell column
//   remove-column  (emitted for a cell)    removes the cell's column
def sheetAction(
    doc: Document[NodeData], targetId: Int,
    command: String): Option[Document[NodeData]] =
  enclosingSheet(doc, targetId).flatMap { sheetId =>
    command match {
      case "add-row" => insertRow(doc, sheetId,
        doc.childrenOf(sheetId).length)
      case "remove-row" => removeRow(doc, sheetId, targetId)
      case "add-column" => insertColumn(doc, sheetId,
        columnWidth(doc, sheetId))
      case "remove-column" => removeColumn(doc, sheetId, targetId)
      case _ => None
    }
  }

private def columnWidth(doc: Document[NodeData], sheetId: Int): Int =
  doc.childrenOf(sheetId).map(r => doc.childrenOf(r).length)
    .foldLeft(0)(_ max _)

private def insertRow(
    doc: Document[NodeData], sheetId: Int,
    index: Int): Option[Document[NodeData]] = {
  val width = columnWidth(doc, sheetId)
  val row = n(sheetTag(RowTag), List.fill(width)(sl(""))*)
  val newId = doc.nodes.length
  insertNode(row, sheetId, index, doc).toOption
    .flatMap(d => internalizeRefs(d, newId).toOption)
    .map(d => shiftRefs(d, sheetId, at = index,
      isRow = true, delta = 1))
}

private def removeRow(
    doc: Document[NodeData], sheetId: Int,
    rowId: Int): Option[Document[NodeData]] =
  termOf(sheetTerms, doc, rowId)
    .filter(Set(RowTag, HRowTag)(_))
    .flatMap { _ =>
      val index = doc.childrenOf(sheetId).indexOf(rowId)
      cutNode(rowId, doc).toOption
        .map(d => shiftRefs(d, sheetId, at = index,
          isRow = true, delta = -1))
    }

private def insertColumn(
    doc: Document[NodeData], sheetId: Int,
    index: Int): Option[Document[NodeData]] =
  doc.childrenOf(sheetId).foldLeft(Option(doc)) { (acc, rowId) =>
    acc.flatMap(d => insertNode(sl(""), rowId,
      index min d.childrenOf(rowId).length, d).toOption)
  }.map(d => shiftRefs(d, sheetId, at = index,
    isRow = false, delta = 1))

private def removeColumn(
    doc: Document[NodeData], sheetId: Int,
    cellId: Int): Option[Document[NodeData]] =
  doc.parentOf(cellId)
    .map(rowId => doc.childrenOf(rowId).indexOf(cellId))
    .filter(_ >= 0)
    .map { col =>
      val cut = doc.childrenOf(sheetId).foldLeft(doc) { (d, rowId) =>
        doc.childrenOf(rowId).lift(col)
          .flatMap(c => cutNode(c, d).toOption).getOrElse(d)
      }
      shiftRefs(cut, sheetId, at = col, isRow = false, delta = -1)
    }

// Rewrite the positional field (row index or column letters) of every
// `ref` construct under `sheetId`: positions past `at` shift by `delta`
// to keep naming the same cells; a position equal to a removed index
// (`delta` = -1) dangles — its column displays "REF", out of grid.
private def shiftRefs(
    doc: Document[NodeData], sheetId: Int, at: Int,
    isRow: Boolean, delta: Int): Document[NodeData] =
  doc.preorder(sheetId).map(_._1).foldLeft(doc) { (d, refId) =>
    refPosition(d, refId).fold(d) { case (row, col) =>
      val pos = if (isRow) row else col
      if (pos > at) setRefPosition(d, refId, isRow, pos + delta)
      else if (pos == at && delta < 0) setRefColumn(d, refId, "REF")
      else d
    }
  }

// The field children of a `ref` node: `column` then `row`, each
// wrapping a single literal leaf.
private def refFieldLeaf(
    doc: Document[NodeData], refId: Int, fieldIndex: Int): Option[Int] =
  doc.childrenOf(refId).lift(fieldIndex)
    .flatMap(f => doc.childrenOf(f).headOption)

private def setRefColumn(
    doc: Document[NodeData], refId: Int,
    letters: String): Document[NodeData] =
  refFieldLeaf(doc, refId, 0)
    .flatMap(id => updateNodeData(id, NodeData.StringData(letters), doc)
      .toOption)
    .getOrElse(doc)

private def setRefPosition(
    doc: Document[NodeData], refId: Int, isRow: Boolean,
    position: Int): Document[NodeData] =
  if (isRow)
    refFieldLeaf(doc, refId, 1)
      .flatMap(id => updateNodeData(id, NodeData.IntData(position + 1),
        doc).toOption)
      .getOrElse(doc)
  else
    setRefColumn(doc, refId,
      cellRefName(0, position).dropRight(1))
