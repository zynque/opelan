package opelan.examples.sheet

import opelan.examples.expr.codeExprReading
import opelan.foundation.document._
import opelan.foundation.means.engine.{evalNode, printViewWith, valueText, leafView}
import opelan.foundation.view.ViewTree
import opelan.foundation.view.ViewTree._
import opelan.foundation.langdef.RuleReading

// A cell's displayed value. Leaf cells — labels, literals, gaps — are
// editable projections (`input`): typing a new value rewrites the source
// node. Refs and formulas stay read-only: their displayed value is the
// evaluation, with a gap linked to the blocking node when they can't
// compute.
private[sheet] def valueOf(
    doc: Document[NodeData], cellId: Int,
    currency: Option[String], reading: RuleReading,
    nameOf: ExternalNodeReference => Option[String])
    : DetachedNode[NodeData] =
  doc.getNode(cellId) match {
    case Some(node)
        if node.childIds.isEmpty &&
           !node.data.isInstanceOf[NodeData.InternalNodeRef] =>
      input(cellId, txt(cellText(doc, cellId)))
    case _ => evalCell(doc, cellId, currency, reading, nameOf)
  }

// A computed value, evaluated by the derived evaluator over the
// ref-resolved projection (`evalNode`, `resolveGridRefs`): a formula's
// operators resolve to foundational definitions by reference, an
// `InternalNodeRef` leaf evaluates the cell it names, and a blocked or
// cyclic ref renders as a gap tagged to the blocking node. With the
// view's `currency` setting, every computed result shows as money
// ("$8.10", "$8.00") — literal inputs stay plain.
private def evalCell(
    doc: Document[NodeData], cellId: Int,
    currency: Option[String], reading: RuleReading,
    nameOf: ExternalNodeReference => Option[String])
    : DetachedNode[NodeData] =
  evalNode(reading.semantics, reading.terms, doc, cellId, Set(cellId),
    nameOf) match {
    case Right(v) => (currency, v) match {
      case (Some(c), NodeData.IntData(n))   => txt(c + "%.2f".format(n.toDouble))
      case (Some(c), NodeData.FloatData(n)) => txt(c + "%.2f".format(n))
      case _                                => txt(valueText(v))
    }
    case Left((msg, srcId)) => from(srcId, gap(msg))
  }

// The surface text a cell's projected editor shows — in both views —
// matching what the cell input grammar produces on commit, so an
// unchanged edit is a genuine no-op: labels show bare, gaps show
// `?text`, refs show their target's cell name ("D5"), and formulas
// show their printed expression with refs named the same way.
def cellText(
    doc: Document[NodeData], cellId: Int,
    nameOf: ExternalNodeReference => Option[String] = (_ => None),
    reading: RuleReading = codeExprReading)
    : String =
  doc.getNode(cellId) match {
    case Some(node) if node.childIds.isEmpty => node.data match {
      case NodeData.StringData(t)      => t
      case NodeData.IntData(i)         => i.toString
      case NodeData.FloatData(f)       => valueText(NodeData.FloatData(f))
      case NodeData.GapData(t)         => s"?$t"
      case NodeData.InternalNodeRef(n) => refText(doc, cellId, n)
      case other                       => NodeDataText.show(other)
    }
    case _ => nameRefs(doc, cellId,
      ViewTree.flatten(printViewWith(reading.rules, reading.tag, leafView,
        nameOf)(resolveGridRefs(doc, cellId), cellId)))
  }
