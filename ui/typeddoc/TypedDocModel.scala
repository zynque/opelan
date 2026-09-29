package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.ui.editor.EditorOutput

// Which pane of a typed document is showing. Editor is the generic
// structural editor; Text is a CodeMirror cell over the document's text
// surface; Derived(name) is a view the document's language provides
// (e.g. "print", "eval").
enum DocView {
  case Editor
  case Text
  case Derived(name: String)
}

// The complete pane state — a pure value. `doc` mirrors the latest
// document (including edits made inside the editor child); `pushedDoc`
// is the value last pushed down into that child and changes only on
// external loads and text-originated edits, so editor-originated changes
// are never sent back — reloading the child would reset its selection,
// edit mode, and undo.
//
// `text` mirrors the text cell's content; `textEpoch` is bumped when
// text is re-derived from the document (structural edits, loads), which
// remounts the editor with the new text. TextEdited never bumps it —
// the cell already holds that text — so typed input never echoes back.
case class TypedDocModel(
    doc: Document[NodeData],
    pushedDoc: Document[NodeData],
    view: DocView = DocView.Editor,
    text: String = "",
    textEpoch: Int = 0,
    status: String = "Ready")

// Everything that can happen to the pane — view events, view switching,
// text edits, and outputs routed up from the editor child.
enum TypedDocInput {
  case SwitchView(view: DocView)
  case Load(doc: Document[NodeData])
  case LoadExprSample
  case TextEdited(text: String)
  case FromEditor(out: EditorOutput)
}

// What the pane reports outward, interpreted by whoever mounts it.
enum TypedDocOutput {
  case DocChanged(doc: Document[NodeData])
  case Status(message: String)
}
