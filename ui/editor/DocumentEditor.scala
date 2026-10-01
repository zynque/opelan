//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.ui.editor

import opelan.foundation.document._
import opelan.ui.fp._

// Raw document editor: an outliner-style structural editor over
// Document[NodeData], now as an fp component.
//
//   I: EditorInput  — one channel for everything (view events, keyboard,
//                     parent-pushed commands like LoadDocument)
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
//   EditorSession.scala  selection, edit mode, load/new/sample, undo/redo
//   EditorDocOps.scala   insert, indent/outdent, compact
//   EditorClip.scala     remove, cut, copy, paste
//   EditorKeys.scala     keymap (KeyboardEvent => Option[EditorInput])
//   EditorView.scala     toolbar, outline, status bar
//   EditorRow.scala      per-node row + inline edit input
//   EditorSample.scala   the manifesto as a sample document
object DocumentEditor extends Component[EditorInput, EditorOutput] {

  type State = EditorModel

  def init: State = EditorSample.model

  def update(state: State, input: EditorInput): Update[State, EditorOutput] =
    EditorUpdate(state, input)

  def view(state: State): View[EditorInput] = EditorView.view(state)

  // Display text for a node's data in the outline.
  def displayData(data: NodeData): String = data match {
    case NodeData.StringData(s)        => s"\"$s\""
    case NodeData.IntData(i)           => i.toString
    case NodeData.FloatData(f)         => f.toString
    case NodeData.InternalNodeRef(id)  => s"&$id"
    case NodeData.ExternalNodeRef(ref) => s"ext:${ref.documentUrl}@${ref.documentVersionId}#${ref.nodeId}"
    case NodeData.GapData(t)           => s"?$t"
  }

  // Initial text when a node enters edit mode.
  def editText(data: NodeData): String = data match {
    case NodeData.StringData(s) => s
    case NodeData.GapData(t)    => t
    case other                  => displayData(other)
  }

  // Parse edited text into node data:
  //   &5              -> internal ref to node 5
  //   ext:url@v#n     -> external ref to node n of url's version v
  //   42              -> int
  //   1.5             -> float
  //   ?abc            -> gap holding the text after '?'
  //   other           -> string
  def parseNodeData(text: String): NodeData =
    if (text.matches("&\\d+")) NodeData.InternalNodeRef(text.drop(1).toInt)
    else if (text.matches("ext:.+@\\d+#\\d+")) {
      val body = text.drop(4)
      NodeData.ExternalNodeRef(ExternalNodeReference(
        body.take(body.lastIndexOf('@')),
        body.substring(body.lastIndexOf('@') + 1, body.lastIndexOf('#')).toInt,
        body.drop(body.lastIndexOf('#') + 1).toInt))
    }
    else if (text.startsWith("?")) NodeData.GapData(text.drop(1))
    else if (text.matches("-?\\d+")) NodeData.IntData(text.toInt)
    else if (text.matches("-?\\d*\\.\\d+")) NodeData.FloatData(text.toDouble)
    else NodeData.StringData(text)

  // Total number of nodes in a detached tree.
  def subtreeSize[A](detached: DetachedNode[A]): Int =
    1 + detached.children.map(subtreeSize).sum
}
