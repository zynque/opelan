package opelan.foundation.typing

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName

// Typed gap represents a placeholder that can be filled progressively
case class TypedGap(
  id: String,
  position: js.Dynamic, // Position in the document (line, column, etc.)
  expectedType: Option[String] = None, // Expected type for the gap
  constraint: Option[js.Dynamic] = None, // Type constraint for the gap
  content: Option[String] = None, // Current content of the gap (if any)
  metadata: GapMetadata
)

// Metadata for typed gaps
case class GapMetadata(
  created: String,
  lastModified: String,
  origin: String = "user", // user, inferred, suggested
  confidence: Double = 1.0, // Confidence level for inferred gaps
  alternatives: js.Array[String] = js.Array[String]() // Alternative fillings
)

// Gap constraint types
sealed trait GapConstraint {
  def name: String
}

object GapConstraint {
  case object Required extends GapConstraint { def name = "required" }
  case class TypeConstraint(expectedType: String) extends GapConstraint { def name = s"type:$expectedType" }
  case class PatternConstraint(regex: String) extends GapConstraint { def name = s"pattern:$regex" }
  case class CustomConstraint(rule: String) extends GapConstraint { def name = s"custom:$rule" }
}

// Typed gap manager for managing gaps in the editor
class TypedGapManager {
  private var gaps: Map[String, TypedGap] = Map.empty
  private var nextGapId: Int = 1
  
  // Create a new typed gap
  def createGap(position: js.Dynamic, expectedType: Option[String] = None): String = {
    val gapId = s"gap_$nextGapId"
    nextGapId += 1
    
    val now = new js.Date().toISOString()
    val gap = TypedGap(
      id = gapId,
      position = position,
      expectedType = expectedType,
      metadata = GapMetadata(
        created = now,
        lastModified = now
      )
    )
    
    gaps += (gapId -> gap)
    gapId
  }
  
  // Get a gap by ID
  def getGap(id: String): Option[TypedGap] = gaps.get(id)
  
  // Update a gap
  def updateGap(id: String, updatedGap: TypedGap): Boolean = {
    if (gaps.contains(id)) {
      val updated = updatedGap.copy(
        metadata = updatedGap.metadata.copy(lastModified = new js.Date().toISOString())
      )
      gaps += (id -> updated)
      true
    } else {
      false
    }
  }
  
  // Fill a gap with content
  def fillGap(id: String, content: String): Boolean = {
    gaps.get(id).map { gap =>
      val updated = gap.copy(
        content = Some(content),
        metadata = gap.metadata.copy(
          lastModified = new js.Date().toISOString(),
          origin = "filled"
        )
      )
      gaps += (id -> updated)
      true
    }.getOrElse(false)
  }
  
  // Remove a gap
  def removeGap(id: String): Boolean = {
    if (gaps.contains(id)) {
      gaps -= id
      true
    } else {
      false
    }
  }
  
  // List all gaps
  def listGaps(): List[TypedGap] = gaps.values.toList
  
  // Get gaps by type constraint
  def getGapsByType(typeName: String): List[TypedGap] = {
    gaps.values.filter(_.expectedType.contains(typeName)).toList
  }
  
  // Suggest fillings for a gap based on context
  def suggestFillings(id: String, context: js.Dynamic): List[String] = {
    gaps.get(id).map { gap =>
      // Basic implementation - can be enhanced with AI/ML suggestions
      gap.metadata.alternatives.toList
    }.getOrElse(List.empty)
  }
  
  // Validate gap content against constraints
  def validateGap(id: String, content: String): Boolean = {
    gaps.get(id).exists { gap =>
      // Basic validation - can be extended with proper type checking
      gap.constraint match {
        case Some(constraint) =>
          val constraintName = constraint.asInstanceOf[js.Dynamic].selectDynamic("name").asInstanceOf[String]
          constraintName match {
            case "required" => content.nonEmpty
            case typeConstraint if typeConstraint.startsWith("type:") =>
              // Basic type validation - placeholder
              true
            case patternConstraint if patternConstraint.startsWith("pattern:") =>
              val regex = patternConstraint.substring(8)
              content.matches(regex)
            case _ => true
          }
        case None => true
      }
    }
  }
}

// Global typed gap manager instance
object TypedGapManager {
  private val manager = new TypedGapManager()
  
  def createGap(position: js.Dynamic, expectedType: Option[String] = None): String = manager.createGap(position, expectedType)
  def getGap(id: String): Option[TypedGap] = manager.getGap(id)
  def updateGap(id: String, updatedGap: TypedGap): Boolean = manager.updateGap(id, updatedGap)
  def fillGap(id: String, content: String): Boolean = manager.fillGap(id, content)
  def removeGap(id: String): Boolean = manager.removeGap(id)
  def listGaps(): List[TypedGap] = manager.listGaps()
  def getGapsByType(typeName: String): List[TypedGap] = manager.getGapsByType(typeName)
  def suggestFillings(id: String, context: js.Dynamic): List[String] = manager.suggestFillings(id, context)
  def validateGap(id: String, content: String): Boolean = manager.validateGap(id, content)
}
