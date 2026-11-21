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
// ids of whatever still matches. `hints` name, by child-index path
// below `content`, the node a subtree continues — a text surface's
// line identity — and win over content matching while that node is
// still a candidate there and applies the same construct.
def commitContent(
    nodeId: Int,
    content: DetachedNode[NodeData],
    document: Document[NodeData],
    hints: Map[List[Int], Int] = Map.empty
): Either[String, Document[NodeData]] =
  commitAt(nodeId, content, document, hints, Nil)

private def commitAt(
    nodeId: Int,
    content: DetachedNode[NodeData],
    document: Document[NodeData],
    hints: Map[List[Int], Int],
    path: List[Int]
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

      def wholeMatch(cid: Int, child: DetachedNode[NodeData]): Boolean =
        extractExternalized(document, cid).contains(child)

      // Which old child a new subtree keeps. Hinted nodes claim first;
      // then whole-subtree matches, the same index tried before the
      // rest so unchanged positions don't scan. Data-only matches — the
      // node kept, its children rematched — then pair the rest by how
      // many leaves they share, best pairs first, and only when they
      // share some: a sibling inserted beside unchanged ones must not
      // take a neighbor's id by position, and a literal piece
      // respelled (`*` → `//`) is a different piece. Former children
      // (cut, still parented here — ids are never reused) are
      // candidates after the live ones: a commit that drops a child
      // and the next that restores it keep its identity.
      val former = document.nodes.indices.filter(id =>
        !oldIds.contains(id) &&
          document.nodes(id).parentId.contains(nodeId)).toList
      val pool = oldIds ++ former

      def pick(index: Int, pred: Int => Boolean): Option[Int] =
        (oldIds.lift(index).toList ++ pool)
          .find(cid => !claimed(cid) && pred(cid))

      val hinted = content.children.zipWithIndex.map { case (child, i) =>
        val k = hints.get(path :+ i).filter(cid =>
          pool.contains(cid) && !claimed(cid) &&
            externalData(cid).contains(child.data))
        k.foreach(claimed += _)
        k.map(cid => cid -> wholeMatch(cid, child))
      }

      val whole = content.children.zipWithIndex.map { case (child, i) =>
        if (hinted(i).isDefined) None
        else {
          val k = pick(i, wholeMatch(_, child))
          k.foreach(claimed += _)
          k
        }
      }

      val partial = (for {
        (child, i) <- content.children.zipWithIndex
        if hinted(i).isEmpty && whole(i).isEmpty && child.children.nonEmpty
        cid <- pool
        if !claimed(cid) && externalData(cid).contains(child.data)
        old <- extractExternalized(document, cid)
        score = sharedLeaves(child, old)
        if score > 0
      } yield (i, cid, score,
        oldIds.lift(i).contains(cid), oldIds.contains(cid)))
        .sortBy { case (i, _, score, here, live) =>
          (-score, !here, !live, i) }
        .foldLeft(Map.empty[Int, Int]) { case (acc, (i, cid, _, _, _)) =>
          if (acc.contains(i) || claimed(cid)) acc
          else { claimed += cid; acc + (i -> cid) }
        }

      val pairs = content.children.zipWithIndex.map { case (child, i) =>
        (child, i, hinted(i)
          .orElse(whole(i).map(_ -> true))
          .orElse(partial.get(i).map(_ -> false)))
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
              case (_, _, Some((cid, true))) => Right((d, ids :+ cid))
              case (child, i, Some((cid, false))) =>
                commitAt(cid, child, d, hints, path :+ i)
                  .map(next => (next, ids :+ cid))
              case (child, i, None) =>
                graft(child, path :+ i, nodeId, d, hints, externalData)
                  .map { case (next, id) => (next, ids :+ id) }
            }
          }
        }
        (d3, newIds) = placed
        d4 <- if (d3.getNode(nodeId).exists(_.childIds == newIds))
                Right(d3)
              else updateNodeChildIds(nodeId, newIds, d3)
      } yield d4
  }

// Insert `child` under `parentId`. A hint inside a fresh subtree still
// reaches a node the commit freed — a construct moved under a new
// parent keeps its identity, and its children rematch like a retained
// node's. A hint naming a node still attached somewhere (matched, or a
// live child even of a former node) is left to insert whole — nothing
// is stolen.
private def graft(
    child: DetachedNode[NodeData], path: List[Int], parentId: Int,
    d: Document[NodeData], hints: Map[List[Int], Int],
    externalData: Int => Option[NodeData])
    : Either[String, (Document[NodeData], Int)] =
  if (!hints.keys.exists(k => k.startsWith(path) && k.length > path.length))
    insertNode(child, parentId, d.childrenOf(parentId).length, d)
      .map(d2 => (d2, d2.nodes.length - 1))
  else
    insertNode(DetachedNode(child.data, Nil), parentId,
        d.childrenOf(parentId).length, d)
      .flatMap { d2 =>
        val parent = d2.nodes.length - 1
        child.children.indices.foldLeft[
            Either[String, Document[NodeData]]](Right(d2)) {
          (acc, j) => acc.flatMap { dd =>
            val grandchild = child.children(j)
            hints.get(path :+ j).filter(n =>
              dd.getNode(n).flatMap(_.parentId)
                .forall(p => !dd.childrenOf(p).contains(n)) &&
              externalData(n).contains(grandchild.data)
            ) match {
              case Some(n) =>
                pasteNode(n, parent, dd.childrenOf(parent).length, dd)
                  .flatMap(commitAt(n, grandchild, _, hints, path :+ j))
              case None =>
                graft(grandchild, path :+ j, parent, dd, hints,
                  externalData).map(_._1)
            }
          }
        }.map((_, parent))
      }

// How many leaves two subtrees share, as multisets — the likeness a
// data-only match ranks candidates by.
private def sharedLeaves(
    a: DetachedNode[NodeData], b: DetachedNode[NodeData]): Int = {
  def leaves(t: DetachedNode[NodeData]): List[NodeData] =
    if (t.children.isEmpty) List(t.data) else t.children.flatMap(leaves)
  leaves(a).intersect(leaves(b)).size
}
