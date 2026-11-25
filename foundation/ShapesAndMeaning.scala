package opelan.foundation

import opelan.foundation.document.NodeData.ExternalNodeRef
import opelan.foundation.document.Document
import opelan.foundation.document.NodeData
import opelan.foundation.document.store.Store
import opelan.foundation.document.DetachedNode

// Currently unused, hand-written conception of core concepts

enum Shape {
  case Product(types: List[ExternalNodeRef]) // Open Question: Should type be represented as a ref, or perhaps an id?
  case Sum                                   // Perhaps doc identifiers should translate to simple in-memory ids for live execution?
  case ListOf(element: ExternalNodeRef)
}

case class Meaning(tree: DetachedNode[NodeData])

trait NodeInterpreter {
  def interpret(nodeId: Int, document: Document[NodeData], children: List[Meaning]): Meaning
}

trait Type {
  val shape: Shape
  def interpreters: Map[Int, NodeInterpreter]
}

def interpret(document: Document[NodeData], interpretationId: Int): Meaning =
  interpret(document.rootId, document, interpretationId, ???)

enum InterpretationGap {
  case MissingNode(nodeId: Int)
  case MissingInterpretation(nodeId: Int, perspectiveId: Int)
}

def interpret(nodeId: Int, document: Document[NodeData], interpretationId: Int, gapHandler: InterpretationGap => Meaning): Meaning = {
  inline def interpretChild(childId: Int): Meaning = interpret(childId, document, interpretationId, gapHandler)
  val gapOrResult = for {
    nodeType <- typeOf(nodeId, document, gapHandler)
    interpreter <- getInterpreter(nodeId, nodeType, interpretationId, gapHandler)
    interpretedChildren = document.childrenOf(nodeId).map(interpretChild)
    interpretation = interpreter.interpret(nodeId, document, interpretedChildren)
  } yield interpretation

  gapOrResult.merge
}

def typeOf(nodeId: Int, document: Document[NodeData], gapHandler: InterpretationGap => Meaning): Either[Meaning, Type] = {
  ???
}

def getInterpreter(nodeId: Int, nodeType: Type, interpretationId: Int, gapHandler: InterpretationGap => Meaning): Either[Meaning, NodeInterpreter] = {
  nodeType.interpreters.get(interpretationId).toRight(gapHandler(InterpretationGap.MissingInterpretation(nodeId, interpretationId)))
}

// -- 

case class Runtime(
  types:    Map[ExternalNodeRef, Type],
  meanings:  Map[(ExternalNodeRef, Int), Meaning]
)

trait Perspective[T] {
  def id: Int
  def run(meaning: Meaning): T
  def compiled(ref: ExternalNodeRef): Vector[T] => T
}
