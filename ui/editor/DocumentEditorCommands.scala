package opelan.ui.editor

import opelan.foundation.document._
import opelan.foundation.document.Detached._

// Whole-document commands: new, sample, compact.
trait DocumentEditorCommands extends DocumentEditorState {

  private[editor] def newDocument(): Unit = {
    doc = Build.beginDocument(s("root"))
    selectedId = Some(doc.rootId)
    editingId = None
    undoStack = Nil
    redoStack = Nil
    detachedNodeId = None
    statusMessage = "New document"
    render()
  }

  private[editor] def sampleDocument(): Unit = {
    doc = Build.buildDocument(
      n(s("Open Language Application Manifesto"),
        n(s("Open"),
          sl("Open Source"),
          sl("Frictionless"),
          sl("Transitive")),
        n(s("Language Oriented"),
          sl("Language"),
          sl("Programmable"),
          sl("Discoverable"),
          sl("Immediately Typed"),
          sl("Transitively Typed"),
          sl("Polysyntactic"),
          sl("Monosemantic")),
        n(s("Application"),
          sl("Available and Responsive"),
          sl("Online/Offline"),
          sl("Preserve History"),
          sl("Reactive"),
          sl("Real Time Collaboration"),
          sl("Self Documenting"))))
    selectedId = Some(doc.rootId)
    statusMessage = "Sample document loaded"
    render()
  }

  private[editor] def compactDocument(): Unit = {
    val before = doc.nodes.length
    val (compacted, remap) = Edit.compact(doc)
    undoStack = (doc :: undoStack).take(maxUndo)
    redoStack = Nil
    // remap internal refs in node data; refs to removed nodes are left dangling
    doc = compacted.mapData(d => NodeData.remapInternalRefs(d, id => remap.getOrElse(id, id)))
    selectedId = selectedId.flatMap(id => remap.get(id)).filter(id => doc.getNode(id).isDefined)
    detachedNodeId = None
    statusMessage = s"Compacted: $before -> ${doc.nodes.length} nodes"
    render()
  }
}
