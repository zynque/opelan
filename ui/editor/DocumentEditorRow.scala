//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.ui.editor

import org.scalajs.dom
import org.scalajs.dom.{Element, HTMLElement, HTMLInputElement}
import opelan.foundation.document._

// Rendering of a single outline row: an id label plus either the node's data
// display or, when the node is being edited, an inline text input.
trait DocumentEditorRow extends DocumentEditorState { self: DocumentEditor =>

  private[editor] def row(id: Int, depth: Int): Element = {
    val node = doc.getNode(id).get
    val row = dom.document.createElement("div").asInstanceOf[HTMLElement]
    row.id = s"doc-row-$id"
    row.style.cssText =
      s"padding: 2px 4px 2px ${depth * 24}px; white-space: nowrap; user-select: none;"
    if (selectedId.contains(id)) row.style.background = "#cce5ff"

    val idLabel = dom.document.createElement("span").asInstanceOf[HTMLElement]
    idLabel.textContent = s"#$id"
    idLabel.style.cssText = "color: #bbb; margin-right: 8px; font-size: 11px;"
    row.appendChild(idLabel)

    if (editingId.contains(id)) row.appendChild(editInput(node))
    else row.appendChild(dataLabel(node))

    row.onclick = (e: dom.MouseEvent) => {
      selectedId = Some(id)
      render()
      e.stopPropagation()
    }
    row.ondblclick = (e: dom.MouseEvent) => {
      self.startEditingAt(id)
      e.stopPropagation()
    }
    row
  }

  private def editInput(node: Node[NodeData]): Element = {
    val input = dom.document.createElement("input").asInstanceOf[HTMLInputElement]
    input.id = "doc-edit-input"
    input.value = DocumentEditor.editText(node.data)
    input.style.cssText = "font-family: Consolas, monospace; font-size: 14px; width: 40ch;"
    input.onkeydown = (e: dom.KeyboardEvent) => {
      e.key match {
        case "Enter"  => self.commitEditing(input.value); e.preventDefault()
        case "Escape" => self.cancelEdit(); e.preventDefault()
        case _        => ()
      }
      e.stopPropagation()
    }
    input.onclick = (e: dom.MouseEvent) => e.stopPropagation()
    input.onblur = (_: dom.FocusEvent) => self.commitEditing(input.value)
    input
  }

  private def dataLabel(node: Node[NodeData]): Element = {
    val label = dom.document.createElement("span").asInstanceOf[HTMLElement]
    label.textContent = DocumentEditor.displayData(node.data)
    node.data match {
      case _: NodeData.InternalNodeRef | _: NodeData.ExternalNodeRef =>
        label.style.color = "#7b2cbf"
      case _ => ()
    }
    label
  }
}
