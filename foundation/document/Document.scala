package opelan.foundation.document

// A document is a persistent data structure representing a tree with data at
// each of its nodes (internal or leaf). Nodes are stored in an indexed
// sequence for efficient updates; node ids are indexes into that sequence.
// Ids are never reused: detached nodes remain in the sequence until
// compaction (see Edit.compact).
case class Document[A](rootId: Int, nodes: Vector[Node[A]]) {

  def getNode(nodeId: Int): Option[Node[A]] =
    nodes.lift(nodeId)

  def childrenOf(nodeId: Int): List[Int] =
    getNode(nodeId).map(_.childIds).getOrElse(Nil)

  def parentOf(nodeId: Int): Option[Int] =
    getNode(nodeId).flatMap(_.parentId)

  def pathFromRootTo(nodeId: Int): List[Int] =
    pathToRootFrom(nodeId).reverse

  private def pathToRootFrom(nodeId: Int): List[Int] =
    parentOf(nodeId) match {
      case None           => List(nodeId)
      case Some(parentId) => nodeId :: pathToRootFrom(parentId)
    }

  // Ids of the whole subtree rooted at nodeId, including nodeId itself.
  def subtreeIds(nodeId: Int): List[Int] =
    nodeId :: childrenOf(nodeId).flatMap(subtreeIds)

  // Transform the data at every node, preserving structure and versions.
  def mapData[B](f: A => B): Document[B] =
    copy(nodes = nodes.map(n => n.copy(data = f(n.data))))
}

case class Node[A](
  version: Int,
  data: A,
  childIds: List[Int],
  parentId: Option[Int]
)
