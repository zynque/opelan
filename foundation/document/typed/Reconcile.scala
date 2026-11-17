package opelan.foundation.document.typed

import opelan.foundation.document._

// Committing new content into an existing node — the identity-
// preserving version of replaceNodeContent: the node keeps its id
// (refs into it survive), and each new child is matched against the
// node's current children so a subtree whose content is unchanged
// keeps its id — its whole subtree included. When only a construct's
// data still matches (same node, changed parts) it keeps its id and
// its children rematch the same way, one level down.
//
// This is what makes a reparse or a projected commit non-destructive:
// the parser produces fresh detached trees, but the nodes that mean
// the same thing keep their identity — references, selections, and
// history stay attached (*Incremental*).
//
// Matching is by *content*: a subtree a parse reused is identical
// content anyway, so comparing externalized forms captures reuse
// exactly — and also preserves ids for coincidentally equal content
// and for parses that never went through a session.

// Replace `nodeId`'s data and children with `content`'s, keeping the
// ids of whatever still matches.
def commitContent(
    nodeId: Int,
    content: DetachedNode[NodeData],
    document: Document[NodeData]
): Either[String, Document[NodeData]] =
  document.getNode(nodeId) match {
    case None => Left(s"commitContent: node id $nodeId does not exist")
    case Some(node) =>
      val entries = importEntries(document).toMap
      val oldIds = node.childIds
      var claimed = Set.empty[Int]

      // A stored node's data with its uses of import entries turned
      // back into inline refs — the form parser output compares to.
      def externalData(id: Int): Option[NodeData] =
        document.getNode(id).map(_.data).map {
          case NodeData.InternalNodeRef(e) if entries.contains(e) =>
            NodeData.ExternalNodeRef(entries(e))
          case d => d
        }

      // Which old child a new subtree keeps. Whole-subtree matches
      // claim first, the same index tried before the rest so unchanged
      // positions don't scan. Data-only matches — the node kept, its
      // children rematched — then pair the rest by how many leaves they
      // share, best pairs first: a sibling inserted beside unchanged
      // ones must not take a neighbor's id by position. Former
      // children (cut, still parented here — ids are never reused)
      // are candidates after the live ones: text typed mid-line can
      // merge a line away for a keystroke, and its content returning
      // must find its identity again.
      val former = document.nodes.indices.filter(id =>
        !oldIds.contains(id) &&
          document.nodes(id).parentId.contains(nodeId)).toList
      val pool = oldIds ++ former

      def pick(index: Int, pred: Int => Boolean): Option[Int] =
        (oldIds.lift(index).toList ++ pool)
          .find(cid => !claimed(cid) && pred(cid))

      val whole = content.children.zipWithIndex.map { case (child, i) =>
        val k = pick(i, cid => extractExternalized(document, cid).contains(child))
        k.foreach(claimed += _)
        k
      }

      val partial = (for {
        (child, i) <- content.children.zipWithIndex
        if whole(i).isEmpty && child.children.nonEmpty
        cid <- pool
        if !claimed(cid) && externalData(cid).contains(child.data)
        old <- extractExternalized(document, cid)
      } yield (i, cid, sharedLeaves(child, old),
        oldIds.lift(i).contains(cid), oldIds.contains(cid)))
        .sortBy { case (i, _, score, here, live) =>
          (-score, !here, !live, i) }
        .foldLeft(Map.empty[Int, Int]) { case (acc, (i, cid, _, _, _)) =>
          if (acc.contains(i) || claimed(cid)) acc
          else { claimed += cid; acc + (i -> cid) }
        }

      val pairs = content.children.zipWithIndex.map { case (child, i) =>
        child -> whole(i).map(_ -> true)
          .orElse(partial.get(i).map(_ -> false))
      }

      for {
        d1 <- if (externalData(nodeId).contains(content.data)) Right(document)
              else updateNodeData(nodeId, content.data, document)
        d2 <- oldIds.filterNot(claimed)
              .foldLeft[Either[String, Document[NodeData]]](Right(d1)) {
                (acc, cid) => acc.flatMap(cutNode(cid, _)) }
        placed <- pairs.foldLeft[
            Either[String, (Document[NodeData], List[Int])]](
            Right((d2, List.empty[Int]))) { (acc, pair) =>
          acc.flatMap { case (d, ids) =>
            pair match {
              case (_, Some((cid, true))) => Right((d, ids :+ cid))
              case (child, Some((cid, false))) =>
                commitContent(cid, child, d).map(next => (next, ids :+ cid))
              case (child, None) =>
                insertNode(child, nodeId, d.childrenOf(nodeId).length, d)
                  .map(next => (next, ids :+ (next.nodes.length - 1)))
            }
          }
        }
        (d3, newIds) = placed
        d4 <- if (d3.getNode(nodeId).exists(_.childIds == newIds))
                Right(d3)
              else updateNodeChildIds(nodeId, newIds, d3)
      } yield d4
  }

// How many leaves two subtrees share, as multisets — the likeness a
// data-only match ranks candidates by.
private def sharedLeaves(
    a: DetachedNode[NodeData], b: DetachedNode[NodeData]): Int = {
  def leaves(t: DetachedNode[NodeData]): List[NodeData] =
    if (t.children.isEmpty) List(t.data) else t.children.flatMap(leaves)
  leaves(a).intersect(leaves(b)).size
}
