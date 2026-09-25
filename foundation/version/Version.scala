package opelan.foundation.version

import opelan.foundation.document._

// A version of some data, forming part of a version tree — a Document whose
// node data is a Version. Version history is a DAG: a node has zero, one, or
// at most two parents (the second via merge).
//
// To find a common ancestor when merging, we maintain alongside each version
// an LSA (lowest single ancestor) link. The lowest single common ancestor of
// two nodes can then be found by walking their root paths in the LSA tree
// until they diverge — O(h) in the height of the LSA tree.
//
// See Fischer & Huson, "New common ancestor problems in trees and directed
// acyclic graphs", Information Processing Letters 110.8-9 (2010).
case class Version[A](
  mergedFromNodeId: Option[Int],
  data: A,
  lsaNodeId: Option[Int]
)

object Version {
  def buildRootVersionNode[A](data: A): Version[A] =
    Version(mergedFromNodeId = None, data = data, lsaNodeId = None)
}

object VersionTree {

  def getVersion[A](nodeId: Int, doc: Document[Version[A]]): Option[Version[A]] =
    doc.getNode(nodeId).map(_.data)

  // Add a new version node as a child of parentNodeId. A single-parent
  // version inherits its parent as its lowest single ancestor.
  def update[A](parentNodeId: Int, data: A, doc: Document[Version[A]]): Either[String, Document[Version[A]]] = {
    val version = Version(mergedFromNodeId = None, data = data, lsaNodeId = Some(parentNodeId))
    Edit.insertNode(DetachedNode.leaf(version), parentNodeId, index = 0, doc)
  }

  // Merge two versions: creates a new version under parentNid whose
  // mergedFromNodeId points at mergedFromNid. The merged node's LSA is the
  // LSCA of its two parents — all paths to the merged node pass through one
  // of the parents, so its lowest single ancestor is the lowest ancestor
  // common to both.
  def merge[A](parentNid: Int, mergedFromNid: Int, data: A, doc: Document[Version[A]]): Either[String, Document[Version[A]]] = {
    val lsca = getLsca(parentNid, mergedFromNid, doc)
    val version = Version(mergedFromNodeId = Some(mergedFromNid), data = data, lsaNodeId = lsca)
    Edit.insertNode(DetachedNode.leaf(version), parentNid, index = 0, doc)
  }

  // Lowest single common ancestor of two version nodes.
  def getLsca[A](nid1: Int, nid2: Int, doc: Document[Version[A]]): Option[Int] =
    lastCommonElement(lsaPathFromRootTo(nid1, doc), lsaPathFromRootTo(nid2, doc))

  def lsaPathFromRootTo[A](nodeId: Int, doc: Document[Version[A]]): List[Int] =
    lsaPathToRootFrom(nodeId, doc).reverse

  private def lsaPathToRootFrom[A](nodeId: Int, doc: Document[Version[A]]): List[Int] =
    getVersion(nodeId, doc).flatMap(_.lsaNodeId) match {
      case Some(lsaNodeId) => nodeId :: lsaPathToRootFrom(lsaNodeId, doc)
      case None            => List(nodeId)
    }

  def lastCommonElement[A](l1: List[A], l2: List[A]): Option[A] =
    l1.zip(l2).takeWhile { case (a, b) => a == b }.lastOption.map(_._1)
}
