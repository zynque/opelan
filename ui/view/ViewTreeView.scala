package opelan.ui.view

import opelan.foundation.document._
import opelan.foundation.view.ViewTree
import opelan.ui.fp._
import opelan.ui.fp.Dsl._

// The single interpreter from view trees — the detached document trees
// Language.render produces — to fp Views. Every derived view of every
// language renders through here, so languages emit data, never DOM.
//
// Recovering (Never Broken): an unrecognized tag or a datum with no
// presentational meaning renders as a hole chip rather than failing, and
// children always render — a malformed view tree degrades gracefully.
//
// Provenance: an element whose first child is a SourceRef leaf is tagged
// — it renders with a click handler emitting `onSource(id)`, so the host
// can navigate to the producing node.
def viewTree[I](t: DetachedNode[NodeData], onSource: Int => I): View[I] = {
  val (src, kids) = t.children match {
    case DetachedNode(NodeData.SourceRef(id), Nil) :: rest => (Some(id), rest)
    case _ => (None, t.children)
  }
  val v = content(t.data, kids, onSource)
  src.fold(v)(id => annotated(v, id, onSource))
}

private def content[I](
    data: NodeData,
    kids: List[DetachedNode[NodeData]],
    onSource: Int => I): View[I] =
  data match {
    case NodeData.StringData(tag) if ViewTree.tags(tag) =>
      element(tag, kids.map(k => viewTree(k, onSource)))
    case NodeData.StringData(tag) if kids.nonEmpty =>
      // An unrecognized element: surface the tag as a hole, children
      // still render.
      element("block", holeView(s"?$tag") +: kids.map(k => viewTree(k, onSource)))
    case NodeData.StringData(s) => text(s)
    case NodeData.GapData(t) =>
      val chip = holeView(if (t.isEmpty) "?" else t)
      if (kids.isEmpty) chip
      else els("span")(chip +: kids.map(k => viewTree(k, onSource)))
    case other =>
      // Scalars render as text; refs and stray SourceRefs become holes.
      val lead = other match {
        case NodeData.IntData(i)   => text(i.toString)
        case NodeData.FloatData(f) => text(f.toString)
        case d                     => holeView(NodeDataText.show(d))
      }
      if (kids.isEmpty) lead
      else element("block", lead +: kids.map(k => viewTree(k, onSource)))
  }

private def element[I](tag: String, kids: Iterable[View[I]]): View[I] =
  tag match {
    case "block" =>
      els("div", style("display: flex; flex-direction: column;"))(kids)
    case "flow" => els("span")(kids)
    case "heading" =>
      els("div", style("font-weight: bold; margin-bottom: 4px;"))(kids)
    case "grid" =>
      el("table", style("border-collapse: collapse;"))(
        els("tbody")(kids.map(asRow)))
    case "row" => els("tr")(kids.map(asCell))
    case "cell" => els("td", style(cellCss))(kids)
    case "hcell" =>
      els("th",
        style(cellCss + " text-align: left; background: #f5f5f5;"))(kids)
  }

private val cellCss = "border: 1px solid #ddd; padding: 2px 8px;"

// Recover invalid table structure: grid children that aren't rows get
// wrapped in one, row children that aren't cells likewise — a <div>
// inside <tbody> would invite browser DOM normalization to fight the
// positional patcher.
private def asRow[I](v: View[I]): View[I] = v match {
  case e @ View.Elem("tr", _, _, _) => e
  case other => el("tr")(el("td", style(cellCss))(other))
}

private def asCell[I](v: View[I]): View[I] = v match {
  case e @ View.Elem(tag, _, _, _) if tag == "td" || tag == "th" => e
  case other => el("td", style(cellCss))(other)
}

private def holeView[I](t: String): View[I] =
  el("span",
    style("background: #fdf3d7; color: #8a6d1a; border: 1px solid #e8d48a; " +
      "border-radius: 3px; padding: 0 4px;"))(
    text(t))

// Tag a rendered fragment with provenance: click emits onSource(id).
private def annotated[I](v: View[I], id: Int, onSource: Int => I): View[I] =
  v match {
    case View.Elem(tag, attrs, evs, kids) =>
      View.Elem(tag,
        attrs
          .updated("title", s"node #$id")
          .updated("style", attrs.getOrElse("style", "") + " cursor: pointer;"),
        evs ++ on("click", onSource(id)),
        kids)
    case other =>
      el("span",
        Map("title" -> s"node #$id", "style" -> "cursor: pointer;"),
        on("click", onSource(id)))(
        other)
  }
