package opelan.ui.editor

import org.scalajs.dom
import opelan.foundation.document.Edit
import opelan.ui.fp._
import opelan.ui.fp.Dsl._

// Pure view: model => desired DOM tree. Focus is declared via the
// data-focus attribute — the outline while navigating, the input while
// editing — and the runtime applies it after patching.
object EditorView {

  def view(m: EditorModel): View[EditorInput] =
    el("div", style("height: 100%; display: flex; flex-direction: column;"))(
      toolbar(),
      el("div", style("flex: 1; display: flex; min-height: 0;"))(
        outline(m),
        EditorHistoryView.view(m)),
      statusBar(m))

  private def outline(m: EditorModel): View[EditorInput] = {
    val rows = m.rowsFrom(m.doc.rootId).map { case (id, depth) =>
      EditorRow.row(m, id, depth)
    }
    els("div",
      attrs = Map(
        "id" -> "doc-outline",
        "tabindex" -> "0",
        "style" -> ("flex: 1; overflow: auto; padding: 8px; outline: none; " +
          "font-family: Consolas, monospace; font-size: 14px; cursor: default;")) ++
        (if (m.editingId.isEmpty) Map("data-focus" -> "true") else Map.empty),
      events = Map("keydown" -> keyHandler(m)))(rows)
  }

  // Handled keys are consumed here (preventDefault); the edit input stops
  // its own keys from reaching this handler.
  private def keyHandler(m: EditorModel)(e: dom.Event): Option[EditorInput] =
    if (m.editingId.nonEmpty) None
    else
      EditorKeys.toInput(e.asInstanceOf[dom.KeyboardEvent]) match {
        case some @ Some(_) => e.preventDefault(); some
        case None           => None
      }

  private def statusBar(m: EditorModel): View[EditorInput] = {
    val reachable = Edit.reachableIds(m.doc).size
    val detached = m.doc.nodes.length - reachable
    val detachedText = if (detached > 0) s", $detached detached" else ""
    el("div",
      style("padding: 4px 10px; border-top: 1px solid #ddd; background: #f9f9f9; " +
        "font-family: Arial, sans-serif; font-size: 12px; color: #555;"))(
      text(s"${m.status} — $reachable nodes$detachedText — " +
        s"root #${m.doc.rootId} — v#${m.versionId}"))
  }

  private def toolbar(): View[EditorInput] =
    el("div",
      style("padding: 6px 10px; border-bottom: 1px solid #ddd; background: #f9f9f9; " +
        "font-family: Arial, sans-serif; font-size: 12px;"))(
      button("New", EditorInput.NewDocument),
      button("Sample", EditorInput.LoadSample),
      button("Compact", EditorInput.Compact),
      el("span", style("color: #888; margin-left: 10px;"))(
        text("Enter: sibling · Shift+Enter: child · Tab/S-Tab: indent/outdent · " +
          "F2/dbl-click: edit · Del: remove · ^X/^C/^V · ^Z/^Y · ^Enter: follow ref")))

  private def button(label: String, input: EditorInput): View[EditorInput] =
    el("button", style("margin-right: 6px; padding: 3px 8px;"), events = on("click", input))(
      text(label))
}
