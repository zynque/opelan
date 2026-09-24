//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.ui.editor

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import org.scalajs.dom
import org.scalajs.dom.{Element, HTMLButtonElement, MouseEvent}
import opelan.foundation.project.{Project, Workspace}
import opelan.foundation.structure.{Schema, SchemaRegistry}
import opelan.foundation.dsl.{DSLParser, Expression}
import opelan.foundation.typing.{TypedGap, TypedGapManager}
import opelan.data.automerge.CollaborationManager
import opelan.data.storage.IndexedDBStore
import opelan.ui.visualization.DiagramRenderer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

// Unified workbench interface for the Opelan Language Workbench
class Workbench {
  private var container: dom.Element = null
  private var isInitialized = false
  private var currentProject: Option[Project] = None
  private var currentView: String = "editor" // editor, visualization, schema
  
  // Initialize the workbench
  def initialize(containerId: String): Unit = {
    if (!isInitialized) {
      container = dom.document.getElementById(containerId)
      if (container == null) {
        throw new IllegalArgumentException(s"Container element with id '$containerId' not found")
      }
      
      setupUI()
      initializeStorage()
      initializeCollaboration()
      isInitialized = true
    }
  }
  
  // Setup the main UI structure
  private def setupUI(): Unit = {
    // Create main layout
    container.innerHTML = """
      <div id="workbench-layout" style="display: flex; height: 100vh; font-family: Arial, sans-serif;">
        <!-- Sidebar -->
        <div id="sidebar" style="width: 250px; border-right: 1px solid #ccc; padding: 10px; background: #f5f5f5;">
          <h3>Projects</h3>
          <div id="project-list"></div>
          <button id="new-project-btn" style="width: 100%; padding: 5px; margin: 5px 0;">New Project</button>
          
          <h3>Definitions</h3>
          <div id="definition-list"></div>
          
          <h3>Schema</h3>
          <div id="schema-list"></div>
          
          <h3>Gaps</h3>
          <div id="gap-list"></div>
        </div>
        
        <!-- Main Content -->
        <div id="main-content" style="flex: 1; display: flex; flex-direction: column;">
          <!-- Toolbar -->
          <div id="toolbar" style="padding: 10px; border-bottom: 1px solid #ccc; background: #f9f9f9;">
            <button id="editor-view-btn" style="margin-right: 5px; padding: 5px 10px;">Editor</button>
            <button id="viz-view-btn" style="margin-right: 5px; padding: 5px 10px;">Visualization</button>
            <button id="schema-view-btn" style="padding: 5px 10px;">Schema</button>
            <span style="margin-left: 20px;">Project: <span id="current-project-name">None</span></span>
          </div>
          
          <!-- Content Area -->
          <div id="content-area" style="flex: 1; padding: 10px; overflow: auto;">
            <!-- Editor View -->
            <div id="editor-view" style="height: 100%;">
              <div id="editor-container" style="height: 100%; border: 1px solid #ccc;"></div>
            </div>
            
            <!-- Visualization View -->
            <div id="viz-view" style="height: 100%; display: none;">
              <div id="viz-container" style="height: 100%; border: 1px solid #ccc;"></div>
            </div>
            
            <!-- Schema View -->
            <div id="schema-view" style="height: 100%; display: none;">
              <div id="schema-container" style="height: 100%; border: 1px solid #ccc;"></div>
            </div>
          </div>
          
          <!-- Status Bar -->
          <div id="status-bar" style="padding: 5px; border-top: 1px solid #ccc; background: #f9f9f9; font-size: 12px;">
            <span id="status-message">Ready</span>
            <span style="float: right;" id="collab-status">Offline</span>
          </div>
        </div>
      </div>
    """
    
    // Setup event handlers
    setupEventHandlers()
  }
  
  // Setup event handlers for UI elements
  private def setupEventHandlers(): Unit = {
    // New project button
    val newProjectBtn = dom.document.getElementById("new-project-btn")
    newProjectBtn.asInstanceOf[dom.HTMLButtonElement].onclick = (event: dom.MouseEvent) => {
      createNewProject()
    }
    
    // View buttons
    val editorViewBtn = dom.document.getElementById("editor-view-btn")
    editorViewBtn.asInstanceOf[dom.HTMLButtonElement].onclick = (event: dom.MouseEvent) => {
      switchView("editor")
    }
    
    val vizViewBtn = dom.document.getElementById("viz-view-btn")
    vizViewBtn.asInstanceOf[dom.HTMLButtonElement].onclick = (event: dom.MouseEvent) => {
      switchView("visualization")
    }
    
    val schemaViewBtn = dom.document.getElementById("schema-view-btn")
    schemaViewBtn.asInstanceOf[dom.HTMLButtonElement].onclick = (event: dom.MouseEvent) => {
      switchView("schema")
    }
  }
  
  // Initialize storage layer
  private def initializeStorage(): Unit = {
    IndexedDBStore.initialize().onComplete {
      case Success(_) =>
        updateStatus("Storage initialized")
        loadPersistedData()
      case Failure(e) =>
        updateStatus(s"Storage initialization failed: ${e.getMessage}")
    }
  }
  
  // Initialize collaboration layer
  private def initializeCollaboration(): Unit = {
    CollaborationManager.initialize()
    updateStatus("Collaboration initialized")
    updateCollabStatus("Ready")
  }
  
  // Load persisted data
  private def loadPersistedData(): Unit = {
    IndexedDBStore.listProjects().onComplete {
      case Success(projects) =>
        updateProjectList(projects)
      case Failure(e) =>
        updateStatus(s"Failed to load projects: ${e.getMessage}")
    }
  }
  
  // Create a new project
  private def createNewProject(): Unit = {
    val projectName = dom.window.prompt("Enter project name:", "New Project")
    if (projectName != null && projectName.nonEmpty) {
      val projectId = Workspace.createProject(projectName)
      val project = Workspace.getProject(projectId).get
      
      // Store in IndexedDB
      val projectData = js.Dynamic.literal(
        "id" -> projectId,
        "name" -> project.name,
        "created" -> new js.Date().toISOString()
      )
      
      IndexedDBStore.storeProject(projectData).onComplete {
        case Success(_) =>
          updateStatus(s"Project '$projectName' created")
          setCurrentProject(project)
          updateProjectList()
        case Failure(e) =>
          updateStatus(s"Failed to save project: ${e.getMessage}")
      }
    }
  }
  
  // Set current project
  private def setCurrentProject(project: Project): Unit = {
    currentProject = Some(project)
    val projectNameEl = dom.document.getElementById("current-project-name")
    projectNameEl.textContent = project.name
    updateStatus(s"Switched to project: ${project.name}")
    
    // Update collaboration
    CollaborationManager.updateProject(project)
  }
  
  // Switch between different views
  private def switchView(view: String): Unit = {
    currentView = view
    
    // Hide all views
    val views = List("editor-view", "viz-view", "schema-view")
    views.foreach { viewId =>
      val element = dom.document.getElementById(viewId)
      element.asInstanceOf[dom.HTMLElement].style.display = "none"
    }
    
    // Show selected view
    val viewId = view match {
      case "visualization" => "viz-view"
      case _ => s"${view}-view"
    }
    val element = dom.document.getElementById(viewId)
    element.asInstanceOf[dom.HTMLElement].style.display = "block"
    
    // Update view-specific content
    view match {
      case "editor" => setupEditorView()
      case "visualization" => setupVisualizationView()
      case "schema" => setupSchemaView()
    }
    
    updateStatus(s"Switched to $view view")
  }
  
  // Setup editor view
  private def setupEditorView(): Unit = {
    val editorContainer = dom.document.getElementById("editor-container")
    
    // Create a simple CodeMirror-like editor
    editorContainer.innerHTML = """
      <div style="height: 100%; display: flex; flex-direction: column;">
        <div style="padding: 10px; border-bottom: 1px solid #ccc; background: #f9f9f9;">
          <strong>DSL Editor</strong>
          <span style="float: right; font-size: 12px; color: #666;">
            Type your expressions here
          </span>
        </div>
        <textarea id="dsl-editor" style="flex: 1; width: 100%; padding: 10px; border: none; font-family: monospace; font-size: 14px; resize: none; outline: none;" placeholder="// Enter your DSL code here
// Example:
// let x = 5
// let y = x + 3
// if (y > 7) { y * 2 } else { y }"></textarea>
        <div style="padding: 10px; border-top: 1px solid #ccc; background: #f9f9f9; font-size: 12px;">
          <span id="editor-status">Ready</span>
          <span style="float: right;">
            <button id="parse-btn" style="padding: 2px 8px; margin-left: 5px;">Parse</button>
            <button id="visualize-btn" style="padding: 2px 8px; margin-left: 5px;">Visualize</button>
          </span>
        </div>
      </div>
    """
    
    // Setup editor event handlers
    val dslEditor = dom.document.getElementById("dsl-editor")
    val parseBtn = dom.document.getElementById("parse-btn")
    val visualizeBtn = dom.document.getElementById("visualize-btn")
    
    parseBtn.asInstanceOf[dom.HTMLElement].onclick = (event: dom.MouseEvent) => {
      val code = dslEditor.asInstanceOf[dom.HTMLTextAreaElement].value
      parseDSL(code)
    }
    
    visualizeBtn.asInstanceOf[dom.HTMLElement].onclick = (event: dom.MouseEvent) => {
      val code = dslEditor.asInstanceOf[dom.HTMLTextAreaElement].value
      visualizeDSL(code)
    }
    
    updateStatus("Editor view ready")
  }
  
  // Setup visualization view
  private def setupVisualizationView(): Unit = {
    val vizContainer = dom.document.getElementById("viz-container")
    DiagramRenderer.initialize(vizContainer)
    
    // Render current project if available
    currentProject.foreach { project =>
      DiagramRenderer.renderProjectGraph(project)
    }
    
    updateStatus("Visualization view ready")
  }
  
  // Setup schema view
  private def setupSchemaView(): Unit = {
    val schemaContainer = dom.document.getElementById("schema-container")
    
    // Create a basic schema editor interface
    schemaContainer.innerHTML = """
      <div style="height: 100%; display: flex; flex-direction: column;">
        <div style="padding: 10px; border-bottom: 1px solid #ccc; background: #f9f9f9;">
          <strong>Schema Editor</strong>
          <span style="float: right; font-size: 12px; color: #666;">
            Define data structures and validation rules
          </span>
        </div>
        <div style="flex: 1; padding: 10px; overflow: auto;">
          <div style="margin-bottom: 20px;">
            <h4>Create New Schema</h4>
            <div style="margin: 10px 0;">
              <label>Schema Name:</label>
              <input type="text" id="schema-name" style="margin-left: 10px; padding: 5px; width: 200px;" placeholder="MySchema">
            </div>
            <div style="margin: 10px 0;">
              <label>Schema Type:</label>
              <select id="schema-type" style="margin-left: 10px; padding: 5px;">
                <option value="record">Record</option>
                <option value="list">List</option>
                <option value="map">Map</option>
                <option value="union">Union</option>
              </select>
            </div>
            <button id="create-schema-btn" style="padding: 5px 10px; margin-top: 10px;">Create Schema</button>
          </div>
          
          <div id="schema-fields" style="margin-top: 20px;">
            <h4>Fields</h4>
            <div id="fields-list" style="border: 1px solid #ccc; padding: 10px; min-height: 100px; background: #fafafa;">
              <div style="color: #666; text-align: center; padding: 20px;">
                No fields defined yet. Create a schema to add fields.
              </div>
            </div>
            <button id="add-field-btn" style="padding: 5px 10px; margin-top: 10px;" disabled>Add Field</button>
          </div>
          
          <div id="schema-preview" style="margin-top: 20px;">
            <h4>Preview</h4>
            <pre id="schema-json" style="background: #f5f5f5; padding: 10px; border: 1px solid #ccc; font-family: monospace; white-space: pre-wrap;">
{
  "name": "ExampleSchema",
  "type": "record",
  "fields": [
    {"name": "id", "type": "String", "required": true},
    {"name": "value", "type": "Int", "required": false}
  ]
}
            </pre>
          </div>
        </div>
      </div>
    """
    
    // Setup schema event handlers
    val createSchemaBtn = dom.document.getElementById("create-schema-btn")
    val addFieldBtn = dom.document.getElementById("add-field-btn")
    
    createSchemaBtn.asInstanceOf[dom.HTMLElement].onclick = (event: dom.MouseEvent) => {
      createSchema()
    }
    
    updateStatus("Schema view ready")
  }
  
  // Parse DSL code
  private def parseDSL(code: String): Unit = {
    updateStatus("Parsing DSL code...")
    // TODO: Integrate with DSLParser
    updateStatus("DSL parsing not yet implemented")
  }
  
  // Visualize DSL code
  private def visualizeDSL(code: String): Unit = {
    updateStatus("Visualizing DSL code...")
    // TODO: Integrate with visualization
    updateStatus("DSL visualization not yet implemented")
  }
  
  // Create a new schema
  private def createSchema(): Unit = {
    val nameInput = dom.document.getElementById("schema-name")
    val typeSelect = dom.document.getElementById("schema-type")
    
    val name = nameInput.asInstanceOf[dom.HTMLInputElement].value
    val schemaType = typeSelect.asInstanceOf[dom.HTMLSelectElement].value
    
    if (name.nonEmpty) {
      updateStatus(s"Creating schema '$name' of type '$schemaType'...")
      // TODO: Implement schema creation
      updateStatus(s"Schema '$name' created (placeholder)")
    } else {
      updateStatus("Please enter a schema name")
    }
  }
  
  // Update project list in sidebar
  private def updateProjectList(projects: List[js.Dynamic] = List.empty): Unit = {
    val projectListEl = dom.document.getElementById("project-list")
    projectListEl.innerHTML = ""
    
    if (projects.nonEmpty) {
      projects.foreach { project =>
        val projectEl = dom.document.createElement("div")
        projectEl.asInstanceOf[dom.HTMLElement].style.padding = "5px"
        projectEl.asInstanceOf[dom.HTMLElement].style.cursor = "pointer"
        projectEl.asInstanceOf[dom.HTMLElement].style.borderBottom = "1px solid #eee"
        projectEl.textContent = project.name.asInstanceOf[String]
        projectEl.asInstanceOf[dom.HTMLElement].onclick = (event: dom.MouseEvent) => {
          loadProject(project.id.asInstanceOf[String])
        }
        projectListEl.appendChild(projectEl)
      }
    } else {
      val noProjectsEl = dom.document.createElement("div")
      noProjectsEl.textContent = "No projects"
      noProjectsEl.asInstanceOf[dom.HTMLElement].style.padding = "5px"
      noProjectsEl.asInstanceOf[dom.HTMLElement].style.color = "#666"
      projectListEl.appendChild(noProjectsEl)
    }
  }
  
  // Load a specific project
  private def loadProject(projectId: String): Unit = {
    IndexedDBStore.getProject(projectId).onComplete {
      case Success(Some(projectData)) =>
        val project = Project(
          name = projectData.name.asInstanceOf[String],
          definitions = js.Array(),
          schemas = js.Array(),
          metadata = opelan.foundation.project.ProjectMetadata(
            created = projectData.created.asInstanceOf[String],
            lastModified = new js.Date().toISOString()
          )
        )
        setCurrentProject(project)
      case Success(None) =>
        updateStatus("Project not found")
      case Failure(e) =>
        updateStatus(s"Failed to load project: ${e.getMessage}")
    }
  }
  
  // Update status message
  private def updateStatus(message: String): Unit = {
    val statusEl = dom.document.getElementById("status-message")
    statusEl.textContent = message
  }
  
  // Update collaboration status
  private def updateCollabStatus(status: String): Unit = {
    val collabEl = dom.document.getElementById("collab-status")
    collabEl.textContent = status
  }
  
  // Get current project
  def getCurrentProject(): Option[Project] = currentProject
  
  // Get current view
  def getCurrentView(): String = currentView
  
  // Cleanup resources
  def cleanup(): Unit = {
    IndexedDBStore.close()
    isInitialized = false
  }
}

// Global workbench instance
object Workbench {
  private val workbench = new Workbench()
  
  def initialize(containerId: String): Unit = workbench.initialize(containerId)
  def getCurrentProject(): Option[Project] = workbench.getCurrentProject()
  def getCurrentView(): String = workbench.getCurrentView()
  def cleanup(): Unit = workbench.cleanup()
}
