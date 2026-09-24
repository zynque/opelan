package opelan.foundation.structure

import scala.scalajs.js

// Basic type system for the schema-first approach
sealed trait Type {
  def name: String
}

object Type {
  case object StringType extends Type { def name = "String" }
  case object IntType extends Type { def name = "Int" }
  case object BoolType extends Type { def name = "Bool" }
  case object AnyType extends Type { def name = "Any" }
  case object NullType extends Type { def name = "Null" }
  
  case class ListType(elementType: Type) extends Type {
    def name = s"List[${elementType.name}]"
  }
  
  case class MapType(keyType: Type, valueType: Type) extends Type {
    def name = s"Map[${keyType.name}, ${valueType.name}]"
  }
  
  case class FunctionType(paramTypes: List[Type], returnType: Type) extends Type {
    def name = s"(${paramTypes.map(_.name).mkString(", ")}) -> ${returnType.name}"
  }
  
  case class RecordType(fields: Map[String, Type]) extends Type {
    def name = s"Record{${fields.map { case (k, v) => s"$k: ${v.name}" }.mkString(", ")}}"
  }
  
  case class UnionType(types: List[Type]) extends Type {
    def name = s"Union(${types.map(_.name).mkString(", ")})"
  }
}

// Schema constraint types
sealed trait Constraint {
  def name: String
}

object Constraint {
  case object Required extends Constraint { def name = "required" }
  case class DefaultValue(value: js.Any) extends Constraint { def name = "default" }
  case class Pattern(regex: String) extends Constraint { def name = "pattern" }
  case class MinLength(length: Int) extends Constraint { def name = "minLength" }
  case class MaxLength(length: Int) extends Constraint { def name = "maxLength" }
  case class Range(min: Double, max: Double) extends Constraint { def name = "range" }
}

// Schema field definition
case class Field(
  name: String,
  fieldType: Type,
  constraints: List[Constraint] = List.empty,
  description: Option[String] = None
)

// Schema definition
case class Schema(
  name: String,
  fields: List[Field],
  metadata: SchemaMetadata
)

// Schema metadata
case class SchemaMetadata(
  created: String,
  lastModified: String,
  author: Option[String] = None,
  tags: List[String] = List.empty,
  stability: String = "experimental" // experimental, stable, deprecated
)

// Schema validation result
case class ValidationResult(
  isValid: Boolean,
  errors: List[ValidationError] = List.empty
)

case class ValidationError(
  field: String,
  message: String,
  code: String
)

// Schema registry for managing schemas
class SchemaRegistry {
  private var schemas: Map[String, Schema] = Map.empty
  
  def register(schema: Schema): Unit = {
    schemas += (schema.name -> schema)
  }
  
  def get(name: String): Option[Schema] = schemas.get(name)
  
  def list(): List[String] = schemas.keys.toList
  
  def validate(schemaName: String, data: js.Dynamic): ValidationResult = {
    schemas.get(schemaName) match {
      case Some(schema) => validateAgainstSchema(data, schema)
      case None => ValidationResult(false, List(ValidationError(schemaName, s"Schema '$schemaName' not found", "SCHEMA_NOT_FOUND")))
    }
  }
  
  private def validateAgainstSchema(data: js.Dynamic, schema: Schema): ValidationResult = {
    // Basic implementation - can be extended
    ValidationResult(true)
  }
}

// Global schema registry instance
object SchemaRegistry {
  private val registry = new SchemaRegistry()
  
  def register(schema: Schema): Unit = registry.register(schema)
  def get(name: String): Option[Schema] = registry.get(name)
  def list(): List[String] = registry.list()
  def validate(schemaName: String, data: js.Dynamic): ValidationResult = registry.validate(schemaName, data)
}
