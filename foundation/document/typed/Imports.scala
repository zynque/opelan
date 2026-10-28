package opelan.foundation.document.typed

import opelan.foundation.document._

// The imports section of a standard document (see Typed). An external
// node is imported once — an ExternalNodeRef leaf under the imports node
// — and every use of it in the content is an InternalNodeRef to that
// entry. A language construct (`mul`, `term`, `vocabulary`) is such a node:
// documents store which definition they use, never its name, so a
// rename touches only the definition.
//
// Detached trees (builder output, parser output, clipboard) have no
// import section of their own, so they carry external refs inline;
// internalizeRefs moves them into the destination's imports on insert.

// (entry id, imported ref) for each import entry.
def importEntries(doc: Document[NodeData]): List[(Int, ExternalNodeReference)] =
  importsId(doc).toList.flatMap(doc.childrenOf).flatMap(id =>
    doc.getNode(id).map(_.data).collect {
      case NodeData.ExternalNodeRef(r) => id -> r
    })

def isImportEntry(doc: Document[NodeData], id: Int): Boolean =
  importEntries(doc).exists(_._1 == id)

// The external node a node's data names: an inline ExternalNodeRef, or
// an InternalNodeRef to an import entry (one hop — the entry holds the
// address).
def externalRefOf(doc: Document[NodeData], id: Int): Option[ExternalNodeReference] =
  doc.getNode(id).map(_.data).flatMap {
    case NodeData.ExternalNodeRef(r) => Some(r)
    case NodeData.InternalNodeRef(t) =>
      importEntries(doc).collectFirst { case (`t`, r) => r }
    case _ => None
  }

// Two refs name the same node: same document, same node id. Versions
// pin a reading of a node, not its identity.
def sameNode(a: ExternalNodeReference, b: ExternalNodeReference): Boolean =
  a.documentUrl == b.documentUrl && a.nodeId == b.nodeId

// The entry importing `ref` — an existing one, or a new one appended to
// the imports section.
def importRef(
    doc: Document[NodeData],
    ref: ExternalNodeReference): Either[String, (Document[NodeData], Int)] =
  importEntries(doc).collectFirst { case (id, `ref`) => id } match {
    case Some(id) => Right((doc, id))
    case None => importsId(doc)
      .toRight("importRef: document has no imports section")
      .flatMap(iid => insertNode(DetachedNode.leaf(NodeData.ExternalNodeRef(ref)),
        iid, doc.childrenOf(iid).length, doc))
      .map(d => (d, d.nodes.length - 1))
  }

// The subtree at `id` as a self-contained detached tree: uses of import
// entries become inline ExternalNodeRefs again — the inverse of
// internalizeRefs, comparable with parser output and insertable into
// another document.
def extractExternalized(
    doc: Document[NodeData], id: Int): Option[DetachedNode[NodeData]] = {
  val entries = importEntries(doc).toMap
  def go(t: DetachedNode[NodeData]): DetachedNode[NodeData] =
    DetachedNode(t.data match {
      case NodeData.InternalNodeRef(e) if entries.contains(e) =>
        NodeData.ExternalNodeRef(entries(e))
      case d => d
    }, t.children.map(go))
  extractSubtree(id, doc).toOption.map(go)
}

// Move every inline ExternalNodeRef in the subtree at `rootId` into the
// imports section, rewriting it as an InternalNodeRef to its entry. A
// document without an imports section (raw) keeps its refs inline.
def internalizeRefs(
    doc: Document[NodeData], rootId: Int): Either[String, Document[NodeData]] =
  if (importsId(doc).isEmpty) Right(doc)
  else doc.subtreeIds(rootId).foldLeft[Either[String, Document[NodeData]]](Right(doc)) {
    (acc, id) => acc.flatMap { d =>
      d.getNode(id).map(_.data) match {
        case Some(NodeData.ExternalNodeRef(r)) =>
          importRef(d, r).flatMap { case (d2, entry) =>
            updateNodeData(id, NodeData.InternalNodeRef(entry), d2) }
        case _ => Right(d)
      }
    }
  }
