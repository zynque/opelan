//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.ui.editor

import org.scalajs.dom.{Element, HTMLElement}
import opelan.foundation.document._
import opelan.foundation.document.Detached._

// Raw document editor: an outliner-style structural editor over
// Document[NodeData]. One row per node; indentation shows depth.
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
//   Escape                     deselect / cancel edit
//
// Composed of small traits in this package:
//   DocumentEditorState      session state (doc, selection, stacks, clipboard)
//   DocumentEditorCommands   whole-document commands (new/sample/compact)
//   DocumentEditorOps        structural edits (insert/indent/outdent)
//   DocumentEditorClipboard  remove/cut/copy/paste of subtrees
//   DocumentEditorSession    selection, edit mode, undo/redo
//   DocumentEditorKeys       keyboard dispatch
//   DocumentEditorView       render + focus management + status bar
//   DocumentEditorRow        per-node row rendering (label or inline input)
//   DocumentEditorToolbar    toolbar buttons and key hints
class DocumentEditor(val container: Element)
    extends DocumentEditorState
    with DocumentEditorCommands
    with DocumentEditorOps
    with DocumentEditorClipboard
    with DocumentEditorSession
    with DocumentEditorKeys
    with DocumentEditorView
    with DocumentEditorRow
    with DocumentEditorToolbar {

  def getDocument: Document[NodeData] = doc

  def setDocument(d: Document[NodeData]): Unit = {
    doc = d
    selectedId = None
    editingId = None
    undoStack = Nil
    redoStack = Nil
    detachedNodeId = None
    render()
  }

  def initialize(): Unit = {
    container.asInstanceOf[HTMLElement].style.cssText =
      "height: 100%; display: flex; flex-direction: column;"
    sampleDocument()
  }
}

// Pure helpers for translating between NodeData and editable text.
object DocumentEditor {

  // Display text for a node's data in the outline.
  def displayData(data: NodeData): String = data match {
    case NodeData.StringData(s)        => s"\"$s\""
    case NodeData.IntData(i)           => i.toString
    case NodeData.FloatData(f)         => f.toString
    case NodeData.InternalNodeRef(id)  => s"&$id"
    case NodeData.ExternalNodeRef(ref) => s"ext:${ref.documentUrl}@${ref.documentVersionId}#${ref.nodeId}"
  }

  // Initial text when a node enters edit mode.
  def editText(data: NodeData): String = data match {
    case NodeData.StringData(s) => s
    case other                  => displayData(other)
  }

  // Parse edited text into node data:
  //   &5    -> internal ref to node 5
  //   42    -> int
  //   1.5   -> float
  //   other -> string
  def parseNodeData(text: String): NodeData =
    if (text.matches("&\\d+")) NodeData.InternalNodeRef(text.drop(1).toInt)
    else if (text.matches("-?\\d+")) NodeData.IntData(text.toInt)
    else if (text.matches("-?\\d*\\.\\d+")) NodeData.FloatData(text.toDouble)
    else NodeData.StringData(text)

  // Total number of nodes in a detached tree.
  def subtreeSize[A](detached: DetachedNode[A]): Int =
    1 + detached.children.map(subtreeSize).sum
}
