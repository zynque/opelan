//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.ui.editor

import org.scalajs.dom
import org.scalajs.dom.{Element, HTMLElement}

// The document editor's toolbar: document commands plus a key-binding legend.
trait DocumentEditorToolbar { self: DocumentEditor =>

  private[editor] def toolbar(): Element = {
    val bar = dom.document.createElement("div").asInstanceOf[HTMLElement]
    bar.style.cssText =
      "padding: 6px 10px; border-bottom: 1px solid #ddd; background: #f9f9f9; " +
      "font-family: Arial, sans-serif; font-size: 12px;"

    bar.appendChild(button("New", () => self.newDocument()))
    bar.appendChild(button("Sample", () => self.sampleDocument()))
    bar.appendChild(button("Compact", () => self.compactDocument()))

    val hints = dom.document.createElement("span").asInstanceOf[HTMLElement]
    hints.style.cssText = "color: #888; margin-left: 10px;"
    hints.textContent =
      "Enter: sibling · Shift+Enter: child · Tab/S-Tab: indent/outdent · " +
      "F2/dbl-click: edit · Del: remove · ^X/^C/^V · ^Z/^Y"
    bar.appendChild(hints)
    bar
  }

  private def button(label: String, action: () => Unit): HTMLElement = {
    val b = dom.document.createElement("button").asInstanceOf[HTMLElement]
    b.textContent = label
    b.style.cssText = "margin-right: 6px; padding: 3px 8px;"
    b.onclick = (_: dom.MouseEvent) => action()
    b
  }
}
