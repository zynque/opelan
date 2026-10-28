package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.typed._

// The per-document auto-update flag: a `setting(auto-update, true)`
// node among the root's children — ordinary versioned, synced data the
// raw outline shows and edits, not a side channel. Read through the
// imports section like every construct use, so a raw document can
// carry it inline.
def autoUpdateEnabled(doc: Document[NodeData]): Boolean =
  autoUpdateFlag(doc).exists(id => childText(doc, id, 1).contains("true"))

// Toggle the flag — appending (or cutting) the setting node under the
// root; a no-op when already in the requested state.
def setAutoUpdate(
    doc: Document[NodeData], on: Boolean): Either[String, Document[NodeData]] =
  autoUpdateFlag(doc) match {
    case Some(id) if !on => cutNode(id, doc)
    case None if on =>
      insertNode(
        DetachedNode(
          NodeData.ExternalNodeRef(metaRef("setting").get),
          List(
            DetachedNode.leaf(NodeData.StringData("auto-update")),
            DetachedNode.leaf(NodeData.StringData("true")))),
        doc.rootId, doc.childrenOf(doc.rootId).length, doc)
        .flatMap(d => internalizeRefs(d, d.childrenOf(d.rootId).last))
    case _ => Right(doc)
  }

// The flag node under the root, if the document carries one. Direct
// children only — a `setting` deeper in the tree (a view setting in a
// lang: doc) is not the flag.
private def autoUpdateFlag(doc: Document[NodeData]): Option[Int] =
  doc.childrenOf(doc.rootId).find { id =>
    externalRefOf(doc, id).exists(metaNameOf(_) == Some("setting")) &&
      childText(doc, id, 0).contains("auto-update")
  }

private def childText(
    doc: Document[NodeData], id: Int, index: Int): Option[String] =
  doc.childrenOf(id).lift(index).flatMap(doc.getNode).map(_.data)
    .collect { case NodeData.StringData(s) => s }
