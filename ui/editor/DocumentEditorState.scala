//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.ui.editor

import org.scalajs.dom.Element
import opelan.foundation.document._

// Mutable session state for the document editor: the current document plus
// the edit session around it (selection, edit mode, undo/redo, clipboard).
// Documents are persistent values, so undo/redo stacks just hold references.
trait DocumentEditorState {

  private[editor] def container: Element // DOM element the editor renders into

  private[editor] var doc: Document[NodeData] = Build.beginDocument(Detached.s("root"))
  private[editor] var selectedId: Option[Int] = None
  private[editor] var editingId: Option[Int] = None
  private[editor] var undoStack: List[Document[NodeData]] = Nil
  private[editor] var redoStack: List[Document[NodeData]] = Nil
  private[editor] var clipboard: Option[DetachedNode[NodeData]] = None
  private[editor] var detachedNodeId: Option[Int] = None // cut node awaiting paste
  private[editor] var statusMessage: String = "Ready"

  protected val maxUndo = 100

  // Implemented by the view.
  private[editor] def render(): Unit

  // The node an operation applies to when nothing is explicitly selected.
  private[editor] def targetId: Int = selectedId.getOrElse(doc.rootId)

  // Flatten the document in outline (pre-order) traversal order.
  private[editor] def rowsFrom(id: Int, depth: Int = 0): List[(Int, Int)] =
    (id, depth) :: doc.childrenOf(id).flatMap(cid => rowsFrom(cid, depth + 1))

  private[editor] def flatIds(): List[Int] = rowsFrom(doc.rootId).map(_._1)

  // Apply an edit result: on success push the old doc onto the undo stack,
  // clear the redo stack, update state and re-render; on failure show the
  // error without touching the document.
  private[editor] def applyEdit(
    result: Either[String, Document[NodeData]],
    successMessage: String,
    selectAfter: Option[Int] = None
  ): Boolean =
    result match {
      case Right(newDoc) =>
        undoStack = (doc :: undoStack).take(maxUndo)
        redoStack = Nil
        doc = newDoc
        selectAfter.foreach(id => selectedId = Some(id))
        statusMessage = successMessage
        render()
        true
      case Left(err) =>
        statusMessage = err
        render()
        false
    }
}
