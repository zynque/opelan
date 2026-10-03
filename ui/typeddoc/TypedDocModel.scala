package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.ui.editor.EditorOutput

// Which editing surface is showing in the left pane: the generic
// structural editor or the CodeMirror text cell over the document's
// text surface. A language's derived views ("print", "eval") are always
// live in the sidebar — they are not switchable modes.
enum DocView {
  case Editor
  case Text
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
//
// `pushedSelect` rides along with `pushedDoc`: a load can name a node to
// select (e.g. a followed ref's target). It only matters when pushedDoc
// itself changes — the child input is resent only on change.
case class TypedDocModel(
    doc: Document[NodeData],
    pushedDoc: Document[NodeData],
    pushedSelect: Option[Int] = None,
    view: DocView = DocView.Editor,
    text: String = "",
    textEpoch: Int = 0,
    status: String = "Ready")

// Everything that can happen to the pane — view events, view switching,
// text edits, and outputs routed up from the editor child.
enum TypedDocInput {
  case SwitchView(view: DocView)
  // `selectId` optionally selects a node in the loaded document — the
  // follow-a-ref jump target.
  case Load(doc: Document[NodeData], selectId: Option[Int] = None)
  // Re-emit the current document as DocChanged — lets the owner pull the
  // live doc (e.g. to save it) without a shadow copy going stale.
  case RequestDoc
  case LoadExprSample
  case TextEdited(text: String)
  case FromEditor(out: EditorOutput)
}

// What the pane reports outward, interpreted by whoever mounts it.
enum TypedDocOutput {
  case DocChanged(doc: Document[NodeData])
  case Status(message: String)
  // The editor's request to follow an external ref; resolved by whoever
  // holds the document store.
  case FollowRef(ref: ExternalNodeReference)
}
