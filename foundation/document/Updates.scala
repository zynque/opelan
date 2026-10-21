package opelan.foundation.document

// Pending updates: a document's external refs that pin behind a head.
// Scanning every reachable ExternalNodeRef covers the type ref (the
// root's data), the imports entries, and any inline ref not yet
// internalized — uses are InternalNodeRefs to entries, so bumping an
// entry retargets every use of that import at once. A ref whose node
// doesn't resolve at the newer version is a broken import, not an
// update. Result: (node holding the ref, the ref, the head version).
def pendingUpdates(
    doc: Document[NodeData],
    store: Store): List[(Int, ExternalNodeReference, Int)] =
  reachableIds(doc).toList.sorted.flatMap { id =>
    doc.getNode(id).map(_.data) match {
      case Some(NodeData.ExternalNodeRef(r)) =>
        store.head(r.documentUrl).toList.flatMap { case (v, target) =>
          if (v > r.documentVersionId && target.getNode(r.nodeId).isDefined)
            List((id, r, v)) else Nil
        }
      case _ => Nil
    }
  }

// Accept pending updates: bump each pinned ref to the store's head —
// one ordinary structural edit, so it records as a version like any
// other.
def acceptUpdates(
    doc: Document[NodeData],
    store: Store): Option[Document[NodeData]] =
  pendingUpdates(doc, store) match {
    case Nil => None
    case bumps => Some(bumps.foldLeft(doc) { (d, b) =>
      updateNodeData(b._1, NodeData.ExternalNodeRef(
        b._2.copy(documentVersionId = b._3)), d).getOrElse(d) })
  }
