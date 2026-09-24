package opelan.data.automerge

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import opelan.foundation.project.{Project, Definition, SchemaDefinition}
import opelan.foundation.dsl.Expression
import opelan.foundation.typing.TypedGap

// Automerge collaboration manager for real-time collaborative editing
class CollaborationManager {
  private var automergeDoc: js.Dynamic = null
  private var isInitialized = false
  private var changeListeners: List[js.Function1[js.Dynamic, Unit]] = List.empty
  
  // Initialize Automerge document
  def initialize(): Unit = {
    if (!isInitialized) {
      // Create initial document structure
      automergeDoc = js.Dynamic.literal(
        "type" -> "workspace",
        "projects" -> js.Dynamic.literal(),
        "currentProject" -> js.Dynamic.literal(),
        "schemas" -> js.Dynamic.literal(),
        "gaps" -> js.Dynamic.literal(),
        "metadata" -> js.Dynamic.literal(
          "version" -> "0.1.0",
          "created" -> new js.Date().toISOString(),
          "lastModified" -> new js.Date().toISOString()
        )
      )
      isInitialized = true
    }
  }
  
  // Load existing document
  def loadDocument(doc: js.Dynamic): Unit = {
    automergeDoc = doc
    isInitialized = true
  }
  
  // Get current document
  def getDocument(): js.Dynamic = automergeDoc
  
  // Add a change listener
  def addChangeListener(listener: js.Function1[js.Dynamic, Unit]): Unit = {
    changeListeners = listener :: changeListeners
  }
  
  // Notify listeners of changes
  private def notifyListeners(change: js.Dynamic): Unit = {
    changeListeners.foreach(_(change))
  }
  
  // Update project in document
  def updateProject(project: Project): Unit = {
    if (isInitialized) {
      val projectData = js.Dynamic.literal(
        "name" -> project.name,
        "definitions" -> js.Array(project.definitions.toSeq.map(definitionToJs)*),
        "schemas" -> js.Array(project.schemas.toSeq.map(schemaToJs)*),
        "metadata" -> js.Dynamic.literal(
          "created" -> project.metadata.created,
          "lastModified" -> project.metadata.lastModified,
          "version" -> project.metadata.version
        )
      )
      
      automergeDoc.projects.updateDynamic(project.name)(projectData)
      automergeDoc.metadata.lastModified = new js.Date().toISOString()
      
      notifyListeners(js.Dynamic.literal(
        "type" -> "project_update",
        "projectName" -> project.name,
        "timestamp" -> new js.Date().toISOString()
      ))
    }
  }
  
  // Add definition to project
  def addDefinition(projectName: String, definition: Definition): Unit = {
    if (isInitialized && automergeDoc.projects.hasOwnProperty(projectName).asInstanceOf[Boolean]) {
      val projectData = automergeDoc.projects.selectDynamic(projectName)
      val definitions = projectData.definitions.asInstanceOf[js.Array[js.Dynamic]]
      definitions.push(definitionToJs(definition))
      projectData.definitions = definitions
      
      automergeDoc.projects.updateDynamic(projectName)(projectData)
      automergeDoc.metadata.lastModified = new js.Date().toISOString()
      
      notifyListeners(js.Dynamic.literal(
        "type" -> "definition_add",
        "projectName" -> projectName,
        "definitionId" -> definition.id,
        "timestamp" -> new js.Date().toISOString()
      ))
    }
  }
  
  // Update definition
  def updateDefinition(projectName: String, definitionId: String, updatedDefinition: Definition): Unit = {
    if (isInitialized && automergeDoc.projects.hasOwnProperty(projectName).asInstanceOf[Boolean]) {
      val projectData = automergeDoc.projects.selectDynamic(projectName)
      val definitions = projectData.definitions.asInstanceOf[js.Array[js.Dynamic]]
      
      val index = definitions.indexWhere { defn =>
        defn.selectDynamic("id").asInstanceOf[String] == definitionId
      }
      
      if (index >= 0) {
        definitions(index) = definitionToJs(updatedDefinition)
        projectData.definitions = definitions
        automergeDoc.projects.updateDynamic(projectName)(projectData)
        automergeDoc.metadata.lastModified = new js.Date().toISOString()
        
        notifyListeners(js.Dynamic.literal(
          "type" -> "definition_update",
          "projectName" -> projectName,
          "definitionId" -> definitionId,
          "timestamp" -> new js.Date().toISOString()
        ))
      }
    }
  }
  
  // Add schema
  def addSchema(schema: SchemaDefinition): Unit = {
    if (isInitialized) {
      automergeDoc.schemas.updateDynamic(schema.id)(schemaToJs(schema))
      automergeDoc.metadata.lastModified = new js.Date().toISOString()
      
      notifyListeners(js.Dynamic.literal(
        "type" -> "schema_add",
        "schemaId" -> schema.id,
        "timestamp" -> new js.Date().toISOString()
      ))
    }
  }
  
  // Add typed gap
  def addGap(gap: TypedGap): Unit = {
    if (isInitialized) {
      automergeDoc.gaps.updateDynamic(gap.id)(gapToJs(gap))
      automergeDoc.metadata.lastModified = new js.Date().toISOString()
      
      notifyListeners(js.Dynamic.literal(
        "type" -> "gap_add",
        "gapId" -> gap.id,
        "timestamp" -> new js.Date().toISOString()
      ))
    }
  }
  
  // Update typed gap
  def updateGap(gapId: String, updatedGap: TypedGap): Unit = {
    if (isInitialized && automergeDoc.gaps.hasOwnProperty(gapId).asInstanceOf[Boolean]) {
      automergeDoc.gaps.updateDynamic(gapId)(gapToJs(updatedGap))
      automergeDoc.metadata.lastModified = new js.Date().toISOString()
      
      notifyListeners(js.Dynamic.literal(
        "type" -> "gap_update",
        "gapId" -> gapId,
        "timestamp" -> new js.Date().toISOString()
      ))
    }
  }
  
  // Convert definition to JS object
  private def definitionToJs(definition: Definition): js.Dynamic = {
    js.Dynamic.literal(
      "id" -> definition.id,
      "name" -> definition.name,
      "typeName" -> definition.typeName,
      "content" -> definition.content,
      "metadata" -> js.Dynamic.literal(
        "created" -> definition.metadata.created,
        "lastModified" -> definition.metadata.lastModified,
        "author" -> definition.metadata.author.orNull,
        "tags" -> js.Array(definition.metadata.tags.toSeq*),
        "visibility" -> definition.metadata.visibility
      )
    )
  }
  
  // Convert schema to JS object
  private def schemaToJs(schema: SchemaDefinition): js.Dynamic = {
    js.Dynamic.literal(
      "id" -> schema.id,
      "name" -> schema.name,
      "schema" -> schema.schema,
      "metadata" -> js.Dynamic.literal(
        "created" -> schema.metadata.created,
        "lastModified" -> schema.metadata.lastModified,
        "author" -> schema.metadata.author.orNull,
        "tags" -> js.Array(schema.metadata.tags.toSeq*),
        "stability" -> schema.metadata.stability
      )
    )
  }
  
  // Convert gap to JS object
  private def gapToJs(gap: TypedGap): js.Dynamic = {
    js.Dynamic.literal(
      "id" -> gap.id,
      "position" -> gap.position,
      "expectedType" -> gap.expectedType.orNull,
      "constraint" -> gap.constraint.orNull,
      "content" -> gap.content.orNull,
      "metadata" -> js.Dynamic.literal(
        "created" -> gap.metadata.created,
        "lastModified" -> gap.metadata.lastModified,
        "origin" -> gap.metadata.origin,
        "confidence" -> gap.metadata.confidence,
        "alternatives" -> js.Array(gap.metadata.alternatives.toSeq*)
      )
    )
  }
  
  // Synchronize with remote peer
  def syncWithPeer(peerDoc: js.Dynamic): Unit = {
    if (isInitialized) {
      // Basic merge strategy - can be enhanced with proper CRDT merge
      mergeDocuments(peerDoc)
    }
  }
  
  // Merge two documents
  private def mergeDocuments(peerDoc: js.Dynamic): Unit = {
    // Simple merge - in a real implementation, this would use Automerge's merge logic
    if (peerDoc.hasOwnProperty("projects").asInstanceOf[Boolean]) {
      val peerProjects = peerDoc.selectDynamic("projects").asInstanceOf[js.Dictionary[js.Dynamic]]
      peerProjects.foreach { case (name, projectData) =>
        if (!automergeDoc.projects.hasOwnProperty(name).asInstanceOf[Boolean]) {
          automergeDoc.projects.updateDynamic(name)(projectData)
        }
      }
    }
    
    if (peerDoc.hasOwnProperty("schemas").asInstanceOf[Boolean]) {
      val peerSchemas = peerDoc.selectDynamic("schemas").asInstanceOf[js.Dictionary[js.Dynamic]]
      peerSchemas.foreach { case (id, schemaData) =>
        if (!automergeDoc.schemas.hasOwnProperty(id).asInstanceOf[Boolean]) {
          automergeDoc.schemas.updateDynamic(id)(schemaData)
        }
      }
    }
    
    if (peerDoc.hasOwnProperty("gaps").asInstanceOf[Boolean]) {
      val peerGaps = peerDoc.selectDynamic("gaps").asInstanceOf[js.Dictionary[js.Dynamic]]
      peerGaps.foreach { case (id, gapData) =>
        if (!automergeDoc.gaps.hasOwnProperty(id).asInstanceOf[Boolean]) {
          automergeDoc.gaps.updateDynamic(id)(gapData)
        }
      }
    }
  }
  
  // Get change history
  def getChangeHistory(): List[js.Dynamic] = {
    // This would track actual changes - simplified for now
    List.empty
  }
  
  // Export document for persistence
  def exportDocument(): js.Dynamic = {
    automergeDoc
  }
  
  // Import document from persistence
  def importDocument(doc: js.Dynamic): Unit = {
    loadDocument(doc)
  }
}

// Global collaboration manager instance
object CollaborationManager {
  private val manager = new CollaborationManager()
  
  def initialize(): Unit = manager.initialize()
  def loadDocument(doc: js.Dynamic): Unit = manager.loadDocument(doc)
  def getDocument(): js.Dynamic = manager.getDocument()
  def addChangeListener(listener: js.Function1[js.Dynamic, Unit]): Unit = manager.addChangeListener(listener)
  def updateProject(project: Project): Unit = manager.updateProject(project)
  def addDefinition(projectName: String, definition: Definition): Unit = manager.addDefinition(projectName, definition)
  def updateDefinition(projectName: String, definitionId: String, updatedDefinition: Definition): Unit = manager.updateDefinition(projectName, definitionId, updatedDefinition)
  def addSchema(schema: SchemaDefinition): Unit = manager.addSchema(schema)
  def addGap(gap: TypedGap): Unit = manager.addGap(gap)
  def updateGap(gapId: String, updatedGap: TypedGap): Unit = manager.updateGap(gapId, updatedGap)
  def syncWithPeer(peerDoc: js.Dynamic): Unit = manager.syncWithPeer(peerDoc)
  def getChangeHistory(): List[js.Dynamic] = manager.getChangeHistory()
  def exportDocument(): js.Dynamic = manager.exportDocument()
  def importDocument(doc: js.Dynamic): Unit = manager.importDocument(doc)
}
