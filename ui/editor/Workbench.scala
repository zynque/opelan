package opelan.ui.editor

import org.scalajs.dom
import opelan.data.storage.IndexedDBStore
import opelan.ui.typeddoc.{TypedDoc, TypedDocInput, TypedDocOutput}

// Application shell: a documents sidebar backed by the versioned document
// store (see WorkbenchDocs) and a toolbar switching between the document
// pane (TypedDoc) and the fp components demo.
class Workbench extends WorkbenchSync {
  private var container: dom.Element = null
  private var isInitialized = false
  private var currentView: String = "document" // document, components
  protected var documentEditor: Option[opelan.ui.fp.Handle[TypedDocInput, TypedDocOutput]] = None
  private var componentsHandle: Option[opelan.ui.fp.Handle[?, ?]] = None

  def initialize(containerId: String): Unit = {
    if (!isInitialized) {
      container = dom.document.getElementById(containerId)
      if (container == null) {
        throw new IllegalArgumentException(s"Container element with id '$containerId' not found")
      }

      container.innerHTML = WorkbenchLayout.markup
      setupEventHandlers()
      switchView(currentView)
      initializeDocuments()
      isInitialized = true
    }
  }

  private def setupEventHandlers(): Unit = {
    onClick("new-doc-btn", _ => createNewDocument())
    onClick("save-doc-btn", _ => saveCurrentDocument())
    onClick("sync-btn", _ => toggleSync())
    onClick("doc-view-btn", _ => switchView("document"))
    onClick("components-view-btn", _ => switchView("components"))
  }

  private def onClick(id: String, handler: dom.MouseEvent => Unit): Unit = {
    dom.document.getElementById(id)
      .asInstanceOf[dom.HTMLButtonElement].onclick = handler
  }

  private def switchView(view: String): Unit = {
    currentView = view
    List("doc-view", "components-view").foreach { viewId =>
      dom.document.getElementById(viewId)
        .asInstanceOf[dom.HTMLElement].style.display = "none"
    }
    val viewId = if (view == "document") "doc-view" else s"${view}-view"
    dom.document.getElementById(viewId)
      .asInstanceOf[dom.HTMLElement].style.display = "block"

    view match {
      case "document" => setupDocumentView()
      case "components" => setupComponentsView()
      case _ => ()
    }
    updateStatus(s"Switched to $view view")
  }

  // Document view: TypedDoc mounted once, then kept alive across switches.
  private def setupDocumentView(): Unit = {
    if (documentEditor.isEmpty) {
      val docContainer = dom.document.getElementById("doc-container")
      val handle = opelan.ui.fp.Runtime.mount(docContainer, TypedDoc)
      handle.outputs.subscribe(docOutput)
      documentEditor = Some(handle)
    }
  }

  // Components demo view: mounts the fp.CounterList example once.
  private def setupComponentsView(): Unit = {
    if (componentsHandle.isEmpty) {
      val c = dom.document.getElementById("components-container")
      val handle = opelan.ui.fp.Runtime.mount(c, opelan.ui.fp.demo.CounterList)
      handle.outputs.subscribe(o => updateStatus(s"Components: $o"))
      componentsHandle = Some(handle)
    }
  }

  protected def updateStatus(message: String): Unit = {
    dom.document.getElementById("status-message").textContent = message
  }

  def cleanup(): Unit = {
    closeSessions()
    IndexedDBStore.close()
    isInitialized = false
  }
}

// Global workbench instance
object Workbench {
  private val workbench = new Workbench()

  def initialize(containerId: String): Unit = workbench.initialize(containerId)
  def cleanup(): Unit = workbench.cleanup()
}
