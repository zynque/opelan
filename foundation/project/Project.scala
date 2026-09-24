package opelan.foundation.project

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName

// Core project model - represents a workspace with definitions
case class Project(
  name: String,
  definitions: js.Array[Definition],
  schemas: js.Array[SchemaDefinition],
  metadata: ProjectMetadata
)

// Definition represents a structured unit of code/logic
case class Definition(
  id: String,
  name: String,
  typeName: String, // Type of the definition (e.g., "function", "type", "expression")
  content: js.Dynamic, // The actual definition content (structured data)
  metadata: DefinitionMetadata
)

// SchemaDefinition represents a schema/type definition in the project
case class SchemaDefinition(
  id: String,
  name: String,
  schema: js.Dynamic, // The schema definition (structure, validation rules, etc.)
  metadata: SchemaMetadata
)

// Metadata for different entity types
case class ProjectMetadata(
  created: String, // ISO timestamp
  lastModified: String,
  version: String = "0.1.0",
  description: Option[String] = None
)

case class DefinitionMetadata(
  created: String,
  lastModified: String,
  author: Option[String] = None,
  tags: js.Array[String] = js.Array[String](),
  visibility: String = "public" // public, private, internal
)

case class SchemaMetadata(
  created: String,
  lastModified: String,
  author: Option[String] = None,
  tags: js.Array[String] = js.Array[String](),
  stability: String = "experimental" // experimental, stable, deprecated
)

// Factory for creating empty projects
object Project {
  def empty(name: String): Project = {
    val now = new js.Date().toISOString()
    Project(
      name = name,
      definitions = js.Array[Definition](),
      schemas = js.Array[SchemaDefinition](),
      metadata = ProjectMetadata(
        created = now,
        lastModified = now,
        version = "0.1.0"
      )
    )
  }
}
