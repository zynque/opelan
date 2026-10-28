package opelan.foundation.document.store

import opelan.foundation.document._

// A document's external refs that pin behind a head — (node holding
// the ref, the ref, the head version, whether the node still resolves
// there). Scanning every reachable ExternalNodeRef covers the type ref
// (the root's data), the imports entries, and any inline ref not yet
// internalized — uses are InternalNodeRefs to entries, so bumping an
// entry retargets every use of that import at once.
private def behindHead(
    doc: Document[NodeData],
    store: Store): List[(Int, ExternalNodeReference, Int, Boolean)] =
  reachableIds(doc).toList.sorted.flatMap { id =>
    doc.getNode(id).map(_.data) match {
      case Some(NodeData.ExternalNodeRef(r)) =>
        store.head(r.documentUrl).toList.flatMap { case (v, target) =>
          if (v > r.documentVersionId)
            List((id, r, v, target.getNode(r.nodeId).isDefined))
          else Nil
        }
      case _ => Nil
    }
  }

// Pending updates: refs pinning behind a head whose node resolves at
// the newer version — bumpable.
def pendingUpdates(
    doc: Document[NodeData],
    store: Store): List[(Int, ExternalNodeReference, Int)] =
  behindHead(doc, store).collect { case (id, r, v, true) => (id, r, v) }

// Broken imports: refs pinning behind a head whose node is gone at the
// newer version — no update can bump them; they surface as broken.
def brokenRefs(
    doc: Document[NodeData],
    store: Store): List[(Int, ExternalNodeReference, Int)] =
  behindHead(doc, store).collect { case (id, r, v, false) => (id, r, v) }

// Accept pending updates: bump each pinned ref to the store's head —
// one ordinary structural edit, so it records as a version like any
// other. Broken refs stay pinned: a bump would lose the node.
def acceptUpdates(
    doc: Document[NodeData],
    store: Store): Option[Document[NodeData]] =
  pendingUpdates(doc, store) match {
    case Nil => None
    case bumps => Some(bumps.foldLeft(doc) { (d, b) =>
      updateNodeData(b._1, NodeData.ExternalNodeRef(
        b._2.copy(documentVersionId = b._3)), d).getOrElse(d) })
  }
