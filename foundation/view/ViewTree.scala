package opelan.foundation.view

import opelan.foundation.document._
import opelan.foundation.document.Detached._

// The view-tree vocabulary: the small set of presentational elements a
// derived view is built from. A view tree is ordinary document data — a
// DetachedNode[NodeData] — so it can be stored, diffed, and edited like
// anything else; the ui layer interprets it into Views.
//
//   "block"                     children stack vertically
//   "flow"                      children run inline (also the wrapper a
//                               leaf gets when it needs provenance)
//   "heading"                   emphasized line
//   "grid" / "row" / "cell" / "hcell"   a table, its rows, its cells
//   "input"                     an editable text region: its SourceRef
//                               first child names the node it edits;
//                               remaining children are the display text
//   "styled:role"               a text run carrying a presentation
//                               role (keyword, placeholder, ...) a
//                               surface may paint
//   leaves    StringData/IntData/FloatData render as text runs;
//             GapData renders as a hole chip
//   src:n     a SourceRef LEAF as an element's first child is its
//             provenance — the source-document node it was derived from
//
// The vocabulary is deliberately tiny: it is the contract between
// language renderers (foundation) and the single view interpreter (ui),
// and it is what a language definition's view spec will eventually
// encode as a document. Anything unrecognized still renders (Never
// Broken): unknown containers show their tag as a hole.
object ViewTree {

  val tags = Set("block", "flow", "heading", "grid", "row", "cell", "hcell", "input")

  private def tagged(tag: String, children: Seq[DetachedNode[NodeData]]) =
    DetachedNode(NodeData.StringData(tag), children.toList)

  def block(children: DetachedNode[NodeData]*)   = tagged("block", children)
  def flow(children: DetachedNode[NodeData]*)    = tagged("flow", children)
  def heading(children: DetachedNode[NodeData]*) = tagged("heading", children)
  def grid(rows: DetachedNode[NodeData]*)        = tagged("grid", rows)
  def row(cells: DetachedNode[NodeData]*)        = tagged("row", cells)
  def cell(children: DetachedNode[NodeData]*)    = tagged("cell", children)
  def hcell(children: DetachedNode[NodeData]*)   = tagged("hcell", children)

  // An inline text run.
  def txt(s: String): DetachedNode[NodeData] = sl(s)

  // A run carrying a presentation role (`styled:keyword`); the
  // interpreter owns the role → style mapping, unknown roles render
  // plain.
  def styled(role: String, children: DetachedNode[NodeData]*) =
    tagged(s"styled:$role", children)

  // A hole: the view is waiting on this (verbatim source text; an empty
  // hole renders '?').
  def hole(text: String): DetachedNode[NodeData] =
    DetachedNode.leaf(NodeData.GapData(text))

  // A provenance annotation: valid as an element's first child.
  def src(nodeId: Int): DetachedNode[NodeData] =
    DetachedNode.leaf(NodeData.SourceRef(nodeId))

  // An editable text region: `nodeId` is the document node whose content
  // a commit rewrites (via the language's cell grammar); the children
  // are the text shown in the box.
  def input(nodeId: Int, display: DetachedNode[NodeData]*) =
    tagged("input", src(nodeId) +: display)

  // Wrap children in a flow carrying provenance — how a leaf gets a
  // source node, since a leaf cannot carry children of its own.
  def from(nodeId: Int, children: DetachedNode[NodeData]*) =
    flow(src(nodeId) +: children*)

  // The view tree projected to plain text — for surfaces that only speak
  // text (the print view feeding docText). Text runs and holes
  // concatenate; block and grid break lines, row separates cells with
  // tabs; unrecognized structure degrades to holes so flattened output
  // reparses as gaps.
  def flatten(t: DetachedNode[NodeData]): String = t match {
    case DetachedNode(NodeData.SourceRef(_), _) => ""
    case DetachedNode(NodeData.GapData(text), children) =>
      (if (text.isEmpty) "?" else text) + children.map(flatten).mkString
    case DetachedNode(NodeData.StringData(tag), children)
        if tag.startsWith("styled:") =>
      children.map(flatten).mkString
    case DetachedNode(NodeData.StringData(tag), children) if tags(tag) =>
      val sep = tag match {
        case "block" | "grid" => "\n"
        case "row"            => "\t"
        case _                => ""
      }
      children.map(flatten).mkString(sep)
    case DetachedNode(NodeData.StringData(tag), children) if children.nonEmpty =>
      s"?$tag" + children.map(flatten).mkString
    case DetachedNode(NodeData.StringData(s), _) => s
    case DetachedNode(NodeData.IntData(i), children) =>
      i.toString + children.map(flatten).mkString
    case DetachedNode(data, children) =>
      NodeDataText.show(data) + children.map(flatten).mkString
  }
}
