package opelan.foundation.document

// The data contained in a document node:
//   a primitive value such as string or int,
//   or a reference to another node in this document
//   or a reference to a node in another document
//   or, in a derived tree, a reference back to the source node
sealed trait NodeData

object NodeData {
  case class StringData(value: String) extends NodeData
  case class IntData(value: Int) extends NodeData
  case class FloatData(value: Double) extends NodeData
  case class InternalNodeRef(nodeId: Int) extends NodeData
  case class ExternalNodeRef(ref: ExternalNodeReference) extends NodeData

  // Provenance in a derived tree (e.g. a view tree produced by
  // Language.render): the id of the node in the *source* document this
  // node was derived from. Not a link within the containing document —
  // the source document is a different address space, so compact never
  // remaps it.
  case class SourceRef(nodeId: Int) extends NodeData

  // A typed hole: text that did not fit the language's grammar, kept
  // verbatim so it can heal when reparsing later accepts it. May carry
  // children holding any sub-fragments that did parse.
  case class GapData(text: String) extends NodeData

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
