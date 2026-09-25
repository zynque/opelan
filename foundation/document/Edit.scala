package opelan.foundation.document

// Persistent edit operations on documents. Each operation returns a new
// document; the original is unchanged.
object Edit {

  // Insert a detached subtree into the document as a child of parentId at the
  // given index within the parent's children.
  def insertNode[A](
    detached: DetachedNode[A],
    parentId: Int,
    index: Int,
    document: Document[A]
  ): Either[String, Document[A]] = {
    val docToMerge = Build.buildDocument(detached)
    val merged = offsetDocBy(document.nodes.length, docToMerge)
    val newNodeId = merged.rootId
    val withAdded = document.copy(nodes = document.nodes ++ merged.nodes)
    setNodesParent(newNodeId, parentId, withAdded)
      .flatMap(d => addChildToParent(parentId, newNodeId, index, d))
  }

  // Removes a node by updating its parent to no longer include it as a child.
  // The node (and its subtree) remains in the document's node sequence until
  // compaction is performed.
  def cutNode[A](nodeId: Int, document: Document[A]): Either[String, Document[A]] =
    document.parentOf(nodeId) match {
      case Some(parentId) =>
        transformNode(parentId, n => n.copy(
          version = n.version + 1,
          childIds = n.childIds.filter(_ != nodeId)
        ), document)
      case None =>
        Left(s"Edit.cutNode: could not look up parent of node with id: $nodeId")
    }

  // Re-attach a node (with its subtree) under parentId at the given index.
  // Intended for nodes previously detached with cutNode, or for nodes that
  // have never been attached. To relocate an attached node, prefer moveNode,
  // which performs the cut and paste atomically with a cycle check.
  def pasteNode[A](nodeId: Int, parentId: Int, index: Int, document: Document[A]): Either[String, Document[A]] = {
    val stillAttached = document.parentOf(nodeId)
      .exists(pid => document.childrenOf(pid).contains(nodeId))
    if (stillAttached)
      Left(s"Edit.pasteNode: node $nodeId is still attached to its parent; use moveNode instead")
    else
      setNodesParent(nodeId, parentId, document)
        .flatMap(d => addChildToParent(parentId, nodeId, index, d))
  }

  // Atomically move a node (with its subtree) to a new parent at the given
  // index. Fails if the target is the node itself or one of its descendants.
  def moveNode[A](nodeId: Int, newParentId: Int, index: Int, document: Document[A]): Either[String, Document[A]] =
    if (document.getNode(nodeId).isEmpty)
      Left(s"Edit.moveNode: node id $nodeId does not exist in document")
    else if (document.pathFromRootTo(newParentId).contains(nodeId))
      Left(s"Edit.moveNode: cannot move node $nodeId into its own descendant $newParentId")
    else
      cutNode(nodeId, document).flatMap(pasteNode(nodeId, newParentId, index, _))

  // Returns a detached copy of the subtree rooted at nodeId, suitable for
  // re-insertion elsewhere via insertNode.
  def extractSubtree[A](nodeId: Int, document: Document[A]): Either[String, DetachedNode[A]] =
    document.getNode(nodeId)
      .toRight(s"Edit.extractSubtree: node id $nodeId does not exist in document")
      .flatMap { node =>
        node.childIds
          .foldLeft[Either[String, List[DetachedNode[A]]]](Right(Nil)) { (acc, childId) =>
            for {
              siblings <- acc
              child <- extractSubtree(childId, document)
            } yield child :: siblings
          }
          .map(children => DetachedNode(node.data, children.reverse))
      }

  def updateNodeData[A](nodeId: Int, newData: A, document: Document[A]): Either[String, Document[A]] =
    transformNode(nodeId, n => n.copy(version = n.version + 1, data = newData), document)

  def updateNodeChildIds[A](nodeId: Int, newChildIds: List[Int], document: Document[A]): Either[String, Document[A]] =
    transformNode(nodeId, n => n.copy(version = n.version + 1, childIds = newChildIds), document)

  // Ids of all nodes reachable from the document root.
  def reachableIds[A](document: Document[A]): Set[Int] = {
    def go(id: Int, acc: Set[Int]): Set[Int] =
      document.getNode(id) match {
        case Some(node) if !acc.contains(id) =>
          node.childIds.foldLeft(acc + id)((a, cid) => go(cid, a))
        case _ => acc
      }
    go(document.rootId, Set.empty)
  }

  // Removes nodes that are unreachable from the root (e.g. detached by
  // cutNode), preserving the relative order of survivors. Returns the
  // compacted document plus a map from old node ids to new ids.
  //
  // NOTE: this rewrites node ids and does not touch node data — remap
  // InternalNodeRef values in node data with the returned map
  // (see NodeData.remapInternalRefs and Document.mapData). Treat compaction
  // as a lineage boundary for history/diffing purposes.
  def compact[A](document: Document[A]): (Document[A], Map[Int, Int]) = {
    val reachable = reachableIds(document)
    val survivors = document.nodes.indices.filter(reachable.contains).toList
    val remap = survivors.zipWithIndex.toMap
    val newNodes = survivors.map { oldId =>
      val n = document.nodes(oldId)
      n.copy(
        childIds = n.childIds.filter(reachable.contains).map(remap),
        parentId = n.parentId.map(remap)
      )
    }.toVector
    (Document(rootId = remap(document.rootId), nodes = newNodes), remap)
  }

  // Structural integrity check. Reports each inconsistency found: dangling
  // child or parent references, parent/child link mismatches, and a missing
  // or parented root. An empty result means the document is well-formed.
  // Unreachable nodes are not reported — they are expected after cutNode.
  def validate[A](document: Document[A]): List[String] = {
    val errors = List.newBuilder[String]
    if (!document.nodes.isDefinedAt(document.rootId)) {
      errors += s"root id ${document.rootId} does not exist"
    } else if (document.nodes(document.rootId).parentId.isDefined) {
      errors += s"root node ${document.rootId} has a parent"
    }
    document.nodes.indices.foreach { id =>
      val node = document.nodes(id)
      node.childIds.foreach { cid =>
        if (!document.nodes.isDefinedAt(cid)) {
          errors += s"node $id: child id $cid does not exist"
        } else if (!document.nodes(cid).parentId.contains(id)) {
          errors += s"node $id lists child $cid, but node $cid has parent ${document.nodes(cid).parentId}"
        }
      }
      node.parentId.foreach { pid =>
        if (!document.nodes.isDefinedAt(pid))
          errors += s"node $id: parent id $pid does not exist"
      }
    }
    errors.result()
  }

  private def setNodesParent[A](nodeId: Int, newParentId: Int, document: Document[A]): Either[String, Document[A]] =
    transformNode(nodeId, n => n.copy(parentId = Some(newParentId)), document)

  private def addChildToParent[A](parentId: Int, childId: Int, index: Int, document: Document[A]): Either[String, Document[A]] =
    transformNode(parentId, n => n.copy(
      version = n.version + 1,
      childIds = n.childIds.patch(index, List(childId), 0)
    ), document)

  private def transformNode[A](nodeId: Int, update: Node[A] => Node[A], document: Document[A]): Either[String, Document[A]] =
    document.getNode(nodeId) match {
      case Some(node) =>
        Right(document.copy(nodes = document.nodes.updated(nodeId, update(node))))
      case None =>
        Left(s"Edit.transformNode: node id $nodeId does not exist in document")
    }

  private def offsetDocBy[A](offset: Int, doc: Document[A]): Document[A] =
    doc.copy(
      rootId = doc.rootId + offset,
      nodes = doc.nodes.map(n => n.copy(
        childIds = n.childIds.map(_ + offset),
        parentId = n.parentId.map(_ + offset)
      ))
    )
}
