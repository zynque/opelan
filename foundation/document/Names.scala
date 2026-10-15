package opelan.foundation.document

// Names are projections: a document stores which node it refers to, and
// the name shown for a reference is read from the referenced definition
// at display time — so renaming a definition renames every use.

// A definition's name: its first string child (a schema entry reads
// kind("mul", fixed 2)); a document root's name is its label.
def definitionName(doc: Document[NodeData], id: Int): Option[String] =
  if (id == doc.rootId) Store.labelOf(doc)
  else doc.childrenOf(id).iterator.flatMap(doc.getNode).map(_.data)
    .collectFirst { case NodeData.StringData(s) => s }

// The name of the node `ref` addresses, read at the document's head so a
// rename shows at once; the pinned version answers when the head no
// longer has the node.
def nameOfRef(store: Store, ref: ExternalNodeReference): Option[String] =
  store.head(ref.documentUrl)
    .filter(_._2.getNode(ref.nodeId).isDefined)
    .orElse(store.get(ref.documentUrl, ref.documentVersionId).map(ref.documentVersionId -> _))
    .flatMap { case (_, d) => definitionName(d, ref.nodeId) }

// The name to show for a reference node in `doc`: external and imported
// refs resolve through the store, internal ones locally.
def refName(store: Store, doc: Document[NodeData], id: Int): Option[String] =
  externalRefOf(doc, id) match {
    case Some(r) => nameOfRef(store, r)
    case None => doc.getNode(id).map(_.data).collect {
      case NodeData.InternalNodeRef(t) => t
    }.flatMap(definitionName(doc, _))
  }
