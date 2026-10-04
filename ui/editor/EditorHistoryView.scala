package opelan.ui.editor

import opelan.ui.fp._
import opelan.ui.fp.Dsl._

// The version DAG as a column beside the outline: one indented row per
// version (children of a version are its branches), the current version
// highlighted, merge nodes annotated. Clicking a row jumps to that version.
object EditorHistoryView {

  def view(m: EditorModel): View[EditorInput] = {
    val rows = versionRows(m, m.history.rootId, 0)
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

  private def versionRows(
      m: EditorModel, id: Int, depth: Int): Vector[View[EditorInput]] =
    m.history.getNode(id).toVector.flatMap { node =>
      row(m, id, node.data.data.label, node.data.mergedFromNodeId, depth) +:
        m.history.childrenOf(id).toVector.flatMap(versionRows(m, _, depth + 1))
    }

  private def row(
      m: EditorModel, id: Int, label: String,
      mergedFrom: Option[Int], depth: Int): View[EditorInput] = {
    val current = id == m.versionId
    el("div",
      style(
        s"padding: 1px 4px 1px ${depth * 14 + 4}px; white-space: nowrap; " +
          s"overflow: hidden; text-overflow: ellipsis; user-select: none;" +
          (if (current) " background: #cce5ff;" else "")),
      events = on("click", EditorInput.GoToVersion(id)))(
      text(s"#$id $label${mergedFrom.fold("")(mf => s" ⇐ #$mf")}"))
  }
}
