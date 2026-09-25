package opelan.foundation.document

object Build {

  def beginDocument[A](data: A): Document[A] =
    Document(
      rootId = 0,
      nodes = Vector(Node(version = 0, data = data, childIds = Nil, parentId = None))
    )

  // Builds a document from a detached tree. Node ids are assigned in
  // post-order: each subtree occupies a contiguous range of ids ending with
  // its root, so the document root is the last node.
  def buildDocument[A](detached: DetachedNode[A]): Document[A] = {
    val (nodes, rootId) = addDetachedNode(detached, Vector.empty)
    Document(rootId = rootId, nodes = nodes)
  }

  // Recursive helper. Takes the nodes added so far and returns them with the
  // detached node and its descendants appended; the returned id is the index
  // of the detached node's root.
  private def addDetachedNode[A](
    detached: DetachedNode[A],
    nodes: Vector[Node[A]]
  ): (Vector[Node[A]], Int) = {
    var acc = nodes
    var childIdsRev = List.empty[Int]
    detached.children.foreach { child =>
      val (next, childId) = addDetachedNode(child, acc)
      acc = next
      childIdsRev = childId :: childIdsRev
    }
    val childIds = childIdsRev.reverse
    val id = acc.length
    // children's parent pointers can only be filled in once our own id is known
    acc = childIds.foldLeft(acc)((ns, cid) => ns.updated(cid, ns(cid).copy(parentId = Some(id))))
    (acc :+ Node(version = 0, data = detached.data, childIds = childIds, parentId = None), id)
  }
}
