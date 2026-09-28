//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.ui.editor

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.{Element, HTMLElement, HTMLInputElement}
import opelan.foundation.document._

// Rendering and the status bar. Every state change triggers a full re-render;
// focus is restored afterward — the inline input while editing, otherwise
// the outline so keyboard navigation keeps working.
trait DocumentEditorView extends DocumentEditorState { self: DocumentEditor =>

  private[editor] def render(): Unit = {
    container.innerHTML = ""
    container.appendChild(self.toolbar())

    val outline = dom.document.createElement("div").asInstanceOf[HTMLElement]
    outline.id = "doc-outline"
    outline.tabIndex = 0
    outline.style.cssText =
      "flex: 1; overflow: auto; padding: 8px; outline: none; " +
      "font-family: Consolas, monospace; font-size: 14px; cursor: default;"
    rowsFrom(doc.rootId).foreach { case (id, depth) => outline.appendChild(self.row(id, depth)) }
    outline.onkeydown = (e: dom.KeyboardEvent) => self.handleKey(e)
    container.appendChild(outline)
    container.appendChild(statusBar())

    editingId match {
      case Some(_) =>
        Option(dom.document.getElementById("doc-edit-input")).foreach { el =>
          val input = el.asInstanceOf[HTMLInputElement]
          input.focus()
          input.select()
        }
      case None =>
        outline.focus()
    }

    // keep the selected row visible during keyboard navigation
    selectedId.foreach { id =>
      Option(dom.document.getElementById(s"doc-row-$id")).foreach { el =>
        el.asInstanceOf[js.Dynamic]
          .scrollIntoView(js.Dynamic.literal("block" -> "nearest"))
      }
    }
  }

  private def statusBar(): Element = {
    val bar = dom.document.createElement("div").asInstanceOf[HTMLElement]
    bar.style.cssText =
      "padding: 4px 10px; border-top: 1px solid #ddd; background: #f9f9f9; " +
      "font-family: Arial, sans-serif; font-size: 12px; color: #555;"
    val reachable = Edit.reachableIds(doc).size
    val detached = doc.nodes.length - reachable
    val detachedText = if (detached > 0) s", $detached detached" else ""
    bar.textContent =
      s"$statusMessage — $reachable nodes$detachedText — root #${doc.rootId}"
    bar
  }
}
