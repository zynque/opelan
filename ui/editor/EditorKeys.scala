package opelan.ui.editor

import org.scalajs.dom
import EditorInput._

// Maps outline keyboard events to editor inputs. Unhandled keys return
// None — the DOM default proceeds and no input is dispatched.
object EditorKeys {

  def toInput(e: dom.KeyboardEvent): Option[EditorInput] = {
    val ctrl = e.ctrlKey || e.metaKey
    (e.key.toLowerCase, ctrl, e.shiftKey) match {
      case ("arrowdown", false, _) => Some(Move(1))
      case ("arrowup", false, _)   => Some(Move(-1))
      case ("enter", false, false) => Some(InsertSibling)
      case ("enter", true, _)      => Some(FollowRef)
      case ("enter", false, true)  => Some(InsertChild)
      case ("tab", false, false)   => Some(Indent)
      case ("tab", false, true)    => Some(Outdent)
      case ("delete", false, _)    => Some(Remove)
      case ("backspace", false, _) => Some(Remove)
      case ("f2", false, _)        => Some(EditSelected)
      case ("z", true, false)      => Some(Undo)
      case ("z", true, true)       => Some(Redo)
      case ("y", true, _)          => Some(Redo)
      case ("x", true, _)          => Some(Cut)
      case ("c", true, _)          => Some(Copy)
      case ("v", true, _)          => Some(Paste)
      case ("escape", false, _)    => Some(Deselect)
      case _                       => None
    }
  }
}
