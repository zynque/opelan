package opelan.foundation.document

// The data contained in a document node:
//   a primitive value such as string or int,
//   or a reference to another node in this document
//   or a reference to a node in another document
sealed trait NodeData

object NodeData {
  case class StringData(value: String) extends NodeData
  case class IntData(value: Int) extends NodeData
  case class FloatData(value: Double) extends NodeData
  case class InternalNodeRef(nodeId: Int) extends NodeData
  case class ExternalNodeRef(ref: ExternalNodeReference) extends NodeData

  // Remap internal node references using the given id translation.
  // Needed after operations like compaction that rewrite node ids.
  def remapInternalRefs(data: NodeData, f: Int => Int): NodeData = data match {
    case InternalNodeRef(nodeId) => InternalNodeRef(f(nodeId))
    case other                   => other
  }
}

case class ExternalNodeReference(
  documentUrl: String,
  documentVersionId: Int,
  nodeId: Int
)
