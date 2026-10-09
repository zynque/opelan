package opelan.ui.editor

import opelan.ui.fp._
import opelan.ui.fp.Dsl._

// The version DAG as a column beside the outline: one row per version,
// the current version highlighted, merge nodes annotated. Clicking a row
// jumps to that version.
//
// Indentation is by lane, not depth: a linear chain of changes stays in
// column 0 no matter how long it gets; only a node's second+ children —
// real branches — shift right into a fresh lane. Depth-based indent
// would push long synced histories off the panel.
def historyView(m: EditorModel): View[EditorInput] = {
  val rows = versionRows(m)
  els("div",
    style("width: 180px; flex-shrink: 0; border-left: 1px solid #ddd; " +
      "overflow: auto; background: #fafafa; padding: 8px; " +
      "font-family: Consolas, monospace; font-size: 12px; cursor: default;"))(
    header +: rows)
}

private def header: View[EditorInput] =
  el("div",
    style("font-family: Arial, sans-serif; font-size: 11px; color: #999; " +
      "text-transform: uppercase; letter-spacing: 0.6px; margin-bottom: 6px;"))(
    text("history"))

// DFS over the version tree assigning each node a lane: the first
// (newest) child continues its parent's lane; siblings each claim a
// new one. Local accumulator only — lanes don't escape the render.
private def versionRows(m: EditorModel): Vector[View[EditorInput]] = {
  var nextLane = 1
  def rows(id: Int, lane: Int): Vector[View[EditorInput]] =
    m.history.getNode(id).toVector.flatMap { node =>
      val children = m.history.childrenOf(id).toVector
      versionRow(m, id, node.data.data.label, node.data.mergedFromNodeId, lane) +:
        children.flatMap { cid =>
          val childLane =
            if (cid == children.head) lane
            else { val l = nextLane; nextLane += 1; l }
          rows(cid, childLane)
        }
    }
  rows(m.history.rootId, 0)
}

private def versionRow(
    m: EditorModel, id: Int, label: String,
    mergedFrom: Option[Int], lane: Int): View[EditorInput] = {
  val current = id == m.versionId
  el("div",
    style(
      s"padding: 1px 4px 1px ${lane * 14 + 4}px; white-space: nowrap; " +
        s"overflow: hidden; text-overflow: ellipsis; user-select: none;" +
        (if (current) " background: #cce5ff;" else "")),
    events = on("click", EditorInput.GoToVersion(id)))(
    text(s"#$id $label${mergedFrom.fold("")(mf => s" ⇐ #$mf")}"))
}
