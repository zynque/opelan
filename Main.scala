//> using scala 3.3.1
//> using platform js
//> using repository central
//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1
//> using jsModuleKind es

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSGlobal, JSImport}
import scala.scalajs.js.JSConverters._

// Automerge JS facade
@js.native
@JSImport("@automerge/automerge", JSImport.Namespace)
object Automerge extends js.Object {
  def init[T](): js.Dynamic = js.native
  def change[T](doc: js.Dynamic, callback: js.Function1[js.Dynamic, Unit]): js.Dynamic = js.native
  def toJS(doc: js.Dynamic): js.Dynamic = js.native
}

// CodeMirror JS facades
@js.native
@JSImport("@codemirror/state", JSImport.Namespace)
object StateModule extends js.Object {
  val EditorState: js.Dynamic = js.native
}

@js.native
@JSImport("@codemirror/view", JSImport.Namespace)
object ViewModule extends js.Object {
  val EditorView: js.Dynamic = js.native
  val keymap: js.Dynamic = js.native
}

@js.native
@JSImport("@codemirror/basic-setup", JSImport.Namespace)
object BasicSetupModule extends js.Object {
  val basicSetup: js.Dynamic = js.native
}

@js.native
@JSImport("@codemirror/commands", JSImport.Namespace)
object CommandsModule extends js.Object {
  val defaultKeymap: js.Array[js.Any] = js.native
}

@js.native
@JSImport("@codemirror/autocomplete", JSImport.Namespace)
object AutocompleteModule extends js.Object {
  val autocompletion: js.Dynamic = js.native
}

@main
def run(): Unit = {
  val document = dom.document
  
  // Initialize Automerge document
  val doc = Automerge.init[js.Dynamic]()
  
  // Create a simple data structure for the code editor
  val updatedDoc = Automerge.change(doc, (doc: js.Dynamic) => {
    doc.code = "Welcome to Opelan Language Workbench\n// Start typing here..."
    doc.history = js.Array[String]()
  })
  
  // Setup UI
  val appContainer = document.getElementById("app")
  val editorContainer = document.getElementById("editor")
  val infoPanel = document.getElementById("infoPanel")
  
  // Create CodeMirror editor
  val EditorState = StateModule.EditorState
  val EditorView = ViewModule.EditorView
  val basicSetup = BasicSetupModule.basicSetup
  val defaultKeymap = CommandsModule.defaultKeymap
  val keymap = ViewModule.keymap
  val autocompletion = AutocompleteModule.autocompletion
  
  // Setup Automerge integration for real-time syncing
  var currentDoc = updatedDoc
  var editorView: js.Dynamic = null
  
  // Simple text area as fallback for debugging
  editorContainer.innerHTML = s"<textarea id='codeEditor' style='width:100%;height:400px;font-family:monospace;'>${updatedDoc.code.asInstanceOf[String]}</textarea>"
  val textArea = document.getElementById("codeEditor").asInstanceOf[dom.html.TextArea]
  
  // Function to update the info panel
  def updateInfoPanel(): Unit = {
    val historyLength = currentDoc.history.asInstanceOf[js.Array[String]].length
    val contentLength = currentDoc.code.asInstanceOf[String].length
    
    infoPanel.innerHTML = s"""
      <h3>Automerge State</h3>
      <p><strong>Current content length:</strong> $contentLength characters</p>
      <p><strong>History entries:</strong> $historyLength</p>
      <button id="saveBtn">Save to Console</button>
      <button id="loadBtn">Reload from Automerge</button>
    """
    
    // Re-attach event listeners to buttons
    val saveBtn = document.getElementById("saveBtn").asInstanceOf[dom.html.Button]
    saveBtn.onclick = { (e: dom.MouseEvent) =>
      val serialized = js.JSON.stringify(Automerge.toJS(currentDoc))
      println(s"Document saved: ${serialized}")
      dom.window.alert("Document saved to console!")
    }
    
    val loadBtn = document.getElementById("loadBtn").asInstanceOf[dom.html.Button]
    loadBtn.onclick = { (e: dom.MouseEvent) =>
      val content = currentDoc.code.asInstanceOf[String]
      textArea.value = content
      dom.window.alert("Document reloaded from Automerge!")
    }
  }
  
  // Create the editor state with minimal setup
  val startState = EditorState.create(
    js.Dynamic.literal(
      doc = updatedDoc.code.asInstanceOf[String]
    )
  )
  
  // Create the editor using JavaScript interop
  val editorOptions = js.Dynamic.literal(
    state = startState,
    parent = editorContainer
  )
  editorView = js.Dynamic.newInstance(ViewModule.EditorView)(editorOptions)
  
  // Add change listener using DOM events
  textArea.addEventListener("input", { (e: dom.Event) =>
    val newContent = editorView.state.doc.toString
    currentDoc = Automerge.change(currentDoc, (doc: js.Dynamic) => {
      doc.code = newContent
      doc.history.asInstanceOf[js.Array[String]].push(s"Updated at ${new js.Date().toISOString()}")
    })
    updateInfoPanel()
  })
  
  // Initial info panel update
  updateInfoPanel()
  
  println("Opelan Language Workbench initialized")
  println("Automerge document created with initial state")
  println("CodeMirror editor ready for editing")
}
