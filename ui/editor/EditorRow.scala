package opelan.ui.editor

import org.scalajs.dom
import opelan.foundation.document._
import opelan.ui.fp._
import opelan.ui.fp.Dsl._
import EditorInput._

// One outline row per node: an id label plus either the data display or,
// while the node is being edited, an inline text input.
object EditorRow {

  def row(m: EditorModel, id: Int, depth: Int): View[EditorInput] = {
    val node = m.doc.getNode(id).get
    val selected = m.selectedId.contains(id)
    View.Elem("div",
      Map(
        "id" -> s"doc-row-$id",
        "style" -> (s"padding: 2px 4px 2px ${depth * 24}px; white-space: nowrap; " +
          "user-select: none;" + (if (selected) " background: #cce5ff;" else ""))) ++
        (if (selected) Map("data-scroll" -> "true") else Map.empty),
      events(
        on("click", Select(id)),
        on("dblclick", StartEdit(id))),
      Vector(idLabel(id), content(m, id, node)))
  }

  private def idLabel(id: Int): View[EditorInput] =
    el("span", style("color: #bbb; margin-right: 8px; font-size: 11px;"))(
      text(s"#$id"))

  private def content(m: EditorModel, id: Int, node: Node[NodeData]): View[EditorInput] =
    if (m.editingId.contains(id)) editInput(node) else dataLabel(node)

  private def dataLabel(node: Node[NodeData]): View[EditorInput] = {
    val css = node.data match {
      case _: NodeData.InternalNodeRef | _: NodeData.ExternalNodeRef => "color: #7b2cbf;"
      case _: NodeData.GapData => "color: #b58900; background: #fdf6e3;"
      case _ => ""
    }
    el("span", style(css))(text(DocumentEditor.displayData(node.data)))
  }

  private def editInput(node: Node[NodeData]): View[EditorInput] =
    View.Elem("input",
      Map(
        "id" -> "doc-edit-input",
        "value" -> DocumentEditor.editText(node.data),
        "style" -> "font-family: Consolas, monospace; font-size: 14px; width: 40ch;",
        "data-focus" -> "true"),
      events(
        onEventOpt("keydown")(keyDown),
        onEventOpt("blur")(e => Some(CommitEdit(valueOf(e)))),
        onEventOpt("click") { e => e.stopPropagation(); None }),
      Vector.empty)

  // Enter commits, Escape cancels; all other keys are swallowed here so they
  // don't reach the outline's keymap while editing.
  private def keyDown(e: dom.Event): Option[EditorInput] = {
    e.stopPropagation()
    e.asInstanceOf[dom.KeyboardEvent].key match {
      case "Enter"  => e.preventDefault(); Some(CommitEdit(valueOf(e)))
      case "Escape" => e.preventDefault(); Some(CancelEdit)
      case _        => None
    }
  }

  private def valueOf(e: dom.Event): String =
    e.target.asInstanceOf[dom.HTMLInputElement].value
}
