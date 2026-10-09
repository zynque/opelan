package opelan.ui.editor

import opelan.foundation.document._
import opelan.ui.fp._

// Raw document editor: an outliner-style structural editor over
// Document[NodeData], now as an fp component.
//
//   I: EditorInput  — one channel for everything (view events, keyboard,
//                     parent-pushed commands like SyncDocument)
//   S: EditorModel  — a pure value (EditorUpdate.scala)
//   O: EditorOutput — DocChanged / Status, interpreted by whoever mounts it
//
// Key bindings (when the outline has focus):
//   Up/Down                    move selection
//   Enter                      new sibling after selection
//   Shift+Enter                new child of selection
//   Tab / Shift+Tab            indent / outdent
//   F2 or double-click         edit node data
//   Delete / Backspace         remove subtree (node remains until compaction)
//   Ctrl+X / Ctrl+C / Ctrl+V   cut / copy / paste subtree
//   Ctrl+Z, Ctrl+Y             undo / redo
//   Ctrl+Enter                 follow the selected node's reference
//   Escape                     deselect / cancel edit
//
// Layout of this component (each file one responsibility):
//   EditorModel.scala    state value + input/output types
//   EditorUpdate.scala   dispatch + shared edit plumbing
//   EditorSession.scala  selection, edit mode, load/new/sample
//   EditorHistory.scala  version-DAG undo/redo/goTo + snapshot type
//   EditorHistoryView    the version-tree column beside the outline
//   EditorDocOps.scala   insert, indent/outdent, compact
//   EditorClip.scala     remove, cut, copy, paste
//   EditorKeys.scala     keymap (KeyboardEvent => Option[EditorInput])
//   EditorView.scala     toolbar, outline + history, status bar
//   EditorRow.scala      per-node row + inline edit input
//   EditorSample.scala   the manifesto as a sample document
object DocumentEditor extends Component[EditorInput, EditorOutput] {

  type State = EditorModel

  def init: State = sampleModel

  def update(state: State, input: EditorInput): Update[State, EditorOutput] =
    editorUpdate(state, input)

  def view(state: State): View[EditorInput] = editorView(state)

  // Display text for a node's data in the outline — the edit syntax
  // (&5, ext:url@v#n) of NodeDataText.
  def displayData(data: NodeData): String = NodeDataText.showEdit(data)

  // Initial text when a node enters edit mode.
  def editText(data: NodeData): String = data match {
    case NodeData.StringData(s) => s
    case NodeData.GapData(t)    => t
    case other                  => displayData(other)
  }

  // Parse edited text into node data — the edit-box syntax of
  // NodeDataText:
  //   &5              -> internal ref to node 5
  //   ext:url@v#n     -> external ref to node n of url's version v
  //   42              -> int
  //   1.5             -> float
  //   ?abc            -> gap holding the text after '?'
  //   other           -> string
  def parseNodeData(text: String): NodeData = NodeDataText.parseEdit(text)
}
