//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.ui.editor

import org.scalajs.dom

// Keyboard dispatch for the outline (see DocumentEditor for the bindings).
// Active only when no inline edit is in progress — the edit input handles
// its own keys.
trait DocumentEditorKeys { self: DocumentEditor =>

  private[editor] def handleKey(e: dom.KeyboardEvent): Unit = {
    if (editingId.isDefined) return
    val ctrl = e.ctrlKey || e.metaKey
    var handled = true
    (e.key.toLowerCase, ctrl, e.shiftKey) match {
      case ("arrowdown", false, _) => moveSelection(1)
      case ("arrowup", false, _)   => moveSelection(-1)
      case ("enter", false, false) => insertSibling()
      case ("enter", false, true)  => insertChild()
      case ("tab", false, false)   => indent()
      case ("tab", false, true)    => outdent()
      case ("delete", false, _)    => deleteSelected()
      case ("backspace", false, _) => deleteSelected()
      case ("f2", false, _)        => startEditing()
      case ("z", true, false)      => undo()
      case ("y", true, _)          => redo()
      case ("z", true, true)       => redo()
      case ("x", true, _)          => cutSelected()
      case ("c", true, _)          => copySelected()
      case ("v", true, _)          => pasteIntoSelected()
      case ("escape", false, _)    => selectedId = None; render()
      case _                       => handled = false
    }
    if (handled) e.preventDefault()
  }
}
