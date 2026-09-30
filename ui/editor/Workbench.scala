//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.ui.editor

import scala.scalajs.js
import org.scalajs.dom
import opelan.data.storage.IndexedDBStore
import opelan.ui.typeddoc.{TypedDoc, TypedDocInput, TypedDocOutput}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

// Application shell: a projects sidebar backed by IndexedDB and a toolbar
// switching between the document pane (TypedDoc) and the fp components demo.
class Workbench {
  private var container: dom.Element = null
  private var isInitialized = false
  private var currentView: String = "document" // document, components
  private var documentEditor: Option[opelan.ui.fp.Handle[TypedDocInput, TypedDocOutput]] = None
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
      initializeStorage()
      isInitialized = true
    }
  }

  private def setupEventHandlers(): Unit = {
    onClick("new-project-btn", _ => createNewProject())
    onClick("doc-view-btn", _ => switchView("document"))
    onClick("components-view-btn", _ => switchView("components"))
  }

  private def onClick(id: String, handler: dom.MouseEvent => Unit): Unit = {
    dom.document.getElementById(id)
      .asInstanceOf[dom.HTMLButtonElement].onclick = handler
  }

  private def initializeStorage(): Unit = {
    IndexedDBStore.initialize().onComplete {
      case Success(_) =>
        updateStatus("Storage initialized")
        refreshProjects()
      case Failure(e) =>
        updateStatus(s"Storage initialization failed: ${e.getMessage}")
    }
  }

  private def refreshProjects(): Unit = {
    IndexedDBStore.listProjects().onComplete {
      case Success(projects) => renderProjectList(projects)
      case Failure(e) => updateStatus(s"Failed to load projects: ${e.getMessage}")
    }
  }

  private def createNewProject(): Unit = {
    val name = dom.window.prompt("Enter project name:", "New Project")
    if (name != null && name.nonEmpty) {
      val project = js.Dynamic.literal(
        "id" -> s"proj_${System.currentTimeMillis()}",
        "name" -> name,
        "created" -> new js.Date().toISOString()
      )
      IndexedDBStore.storeProject(project).onComplete {
        case Success(_) =>
          updateStatus(s"Project '$name' created")
          selectProject(project)
          refreshProjects()
        case Failure(e) =>
          updateStatus(s"Failed to save project: ${e.getMessage}")
      }
    }
  }

  private def selectProject(project: js.Dynamic): Unit = {
    val name = project.name.asInstanceOf[String]
    dom.document.getElementById("current-project-name").textContent = name
    updateStatus(s"Switched to project: $name")
  }

  private def renderProjectList(projects: List[js.Dynamic]): Unit = {
    val listEl = dom.document.getElementById("project-list")
    listEl.innerHTML = ""

    if (projects.isEmpty) {
      val empty = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
      empty.textContent = "No projects"
      empty.style.padding = "5px"
      empty.style.color = "#666"
      listEl.appendChild(empty)
    } else {
      projects.foreach { project =>
        val item = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
        item.textContent = project.name.asInstanceOf[String]
        item.style.padding = "5px"
        item.style.cursor = "pointer"
        item.style.borderBottom = "1px solid #eee"
        item.onclick = (_: dom.MouseEvent) => selectProject(project)
        listEl.appendChild(item)
      }
    }
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
      handle.outputs.subscribe {
        case TypedDocOutput.Status(msg) => updateStatus(msg)
        case _ => ()
      }
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

  private def updateStatus(message: String): Unit = {
    dom.document.getElementById("status-message").textContent = message
  }

  def cleanup(): Unit = {
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
