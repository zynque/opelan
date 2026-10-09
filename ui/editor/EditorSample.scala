package opelan.ui.editor

import opelan.foundation.document._
import opelan.foundation.document.Detached._

// The sample document: the manifesto as a tree — dogfooding demo content.

def sampleDoc: Document[NodeData] = buildDocument(
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
      sl("Polysyntactic")),
    n(s("Application"),
      sl("Available and Responsive"),
      sl("Online/Offline"),
      sl("Preserve History"),
      sl("Reactive"),
      sl("Real Time Collaboration"),
      sl("Self Documenting"))))

def sampleModel: EditorModel = {
  val d = sampleDoc
  EditorModel.forDocument(d, Some(d.rootId), "Sample document loaded")
}
