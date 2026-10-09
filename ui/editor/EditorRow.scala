package opelan.ui.editor

import org.scalajs.dom
import opelan.foundation.document._
import opelan.ui.fp._
import opelan.ui.fp.Dsl._
import EditorInput._

// One outline row per node: an id label plus either the data display or,
// while the node is being edited, an inline text input.
def row(m: EditorModel, id: Int, depth: Int): View[EditorInput] = {
  // The pending-insert phantom row has no node in the document — it
  // renders as an empty one, always in edit mode.
  val node = m.doc.getNode(id).getOrElse(PendingNode)
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

private val PendingNode =
  Node[NodeData](0, NodeData.StringData(""), List.empty, None)

private def idLabel(id: Int): View[EditorInput] =
  el("span", style("color: #bbb; margin-right: 8px; font-size: 11px;"))(
    text(if (id == EditorModel.PendingId) "new" else s"#$id"))

private def content(m: EditorModel, id: Int, node: Node[NodeData]): View[EditorInput] =
  if (id == EditorModel.PendingId) pendingInput(m)
  else if (m.editingId.contains(id)) editInput(node)
  else dataLabel(node)

// The phantom row's box: its text is tracked in the model (DraftEdit)
// so a remote push that reorders rows — rebuilding this element — can't
// lose what was typed.
private def pendingInput(m: EditorModel): View[EditorInput] =
  View.Elem("input",
    Map(
      "id" -> "doc-edit-input",
      "value" -> m.pendingInsert.map(_.draft).getOrElse(""),
      "style" -> "font-family: Consolas, monospace; font-size: 14px; width: 40ch;",
      "data-focus" -> "true"),
    events(
      onEventOpt("keydown")(keyDown),
      onEventOpt("input")(e => Some(DraftEdit(valueOf(e)))),
      onEventOpt("blur")(blurCommit),
      onEventOpt("click") { e => e.stopPropagation(); None }),
    Vector.empty)

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
      onEventOpt("blur")(blurCommit),
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

// A blur delivered while the input is leaving the document is DOM
// teardown (a patch rebuilt the row), not the user moving focus away —
// committing it would discard the edit, so only live blurs count.
private def blurCommit(e: dom.Event): Option[EditorInput] =
  if (e.target.asInstanceOf[dom.Element].isConnected)
    Some(CommitEdit(valueOf(e)))
  else None

private def valueOf(e: dom.Event): String =
  e.target.asInstanceOf[dom.HTMLInputElement].value
