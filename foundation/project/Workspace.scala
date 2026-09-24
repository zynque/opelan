package opelan.foundation.project

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName

// Workspace manages a collection of projects and their current state
class Workspace {
  private var projects: Map[String, Project] = Map.empty
  private var currentProjectId: Option[String] = None
  
  // Create a new project
  def createProject(name: String): String = {
    val projectId = s"proj_${System.currentTimeMillis()}"
    val project = Project.empty(name)
    projects += (projectId -> project)
    projectId
  }
  
  // Get a project by ID
  def getProject(id: String): Option[Project] = projects.get(id)
  
  // Set the current active project
  def setCurrentProject(id: String): Unit = {
    if (projects.contains(id)) {
      currentProjectId = Some(id)
    }
  }
  
  // Get the current project
  def currentProject: Option[Project] = currentProjectId.flatMap(projects.get)
  
  // List all projects
  def listProjects(): List[Project] = projects.values.toList
  
  // Add a definition to a project
  def addDefinition(projectId: String, definition: Definition): Boolean = {
    projects.get(projectId).map { project =>
      val updatedDefinitions = project.definitions :+ definition
      val updatedProject = project.copy(
        definitions = updatedDefinitions,
        metadata = project.metadata.copy(lastModified = new js.Date().toISOString())
      )
      projects += (projectId -> updatedProject)
      true
    }.getOrElse(false)
  }
  
  // Add a schema to a project
  def addSchema(projectId: String, schema: SchemaDefinition): Boolean = {
    projects.get(projectId).map { project =>
      val updatedSchemas = project.schemas :+ schema
      val updatedProject = project.copy(
        schemas = updatedSchemas,
        metadata = project.metadata.copy(lastModified = new js.Date().toISOString())
      )
      projects += (projectId -> updatedProject)
      true
    }.getOrElse(false)
  }
  
  // Update a definition
  def updateDefinition(projectId: String, definitionId: String, updatedDefinition: Definition): Boolean = {
    projects.get(projectId).map { project =>
      val index = project.definitions.indexWhere(_.id == definitionId)
      if (index >= 0) {
        val updatedDefinitions = project.definitions.updated(index, updatedDefinition)
        val updatedProject = project.copy(
          definitions = updatedDefinitions,
          metadata = project.metadata.copy(lastModified = new js.Date().toISOString())
        )
        projects += (projectId -> updatedProject)
        true
      } else {
        false
      }
    }.getOrElse(false)
  }
  
  // Delete a definition
  def deleteDefinition(projectId: String, definitionId: String): Boolean = {
    projects.get(projectId).map { project =>
      val updatedDefinitions = project.definitions.filter(_.id != definitionId)
      val updatedProject = project.copy(
        definitions = updatedDefinitions,
        metadata = project.metadata.copy(lastModified = new js.Date().toISOString())
      )
      projects += (projectId -> updatedProject)
      true
    }.getOrElse(false)
  }
}

// Global workspace instance
object Workspace {
  private val workspace = new Workspace()
  
  def createProject(name: String): String = workspace.createProject(name)
  def getProject(id: String): Option[Project] = workspace.getProject(id)
  def setCurrentProject(id: String): Unit = workspace.setCurrentProject(id)
  def currentProject: Option[Project] = workspace.currentProject
  def listProjects(): List[Project] = workspace.listProjects()
  def addDefinition(projectId: String, definition: Definition): Boolean = workspace.addDefinition(projectId, definition)
  def addSchema(projectId: String, schema: SchemaDefinition): Boolean = workspace.addSchema(projectId, schema)
  def updateDefinition(projectId: String, definitionId: String, updatedDefinition: Definition): Boolean = workspace.updateDefinition(projectId, definitionId, updatedDefinition)
  def deleteDefinition(projectId: String, definitionId: String): Boolean = workspace.deleteDefinition(projectId, definitionId)
}
