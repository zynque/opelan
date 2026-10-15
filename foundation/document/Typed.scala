package opelan.foundation.document

// A "raw" document has no constraints on its shape. A typed document
// carries its type in the root node's *data* — an ExternalNodeRef to
// the document defining the language the rest of the document is
// written in. That reference is the header: it determines how the
// remaining nodes are read.
//
// Simple documents may be nothing more — a root ref and data children.
// Most extend the standard core language, whose layout is positional:
//
//   root                     ExternalNodeRef(defining language document)
//   ├── title node           the document's name (Store.labelOf)
//   ├── imports node         one ExternalNodeRef leaf per imported node
//   └── content node         AST root in the type's language
//
// The imports section is the document's dependencies — itself a
// construct use, carrying a ref to the core language's `imports`
// declaration — holding one ExternalNodeRef leaf per imported node.
// Every external node the content uses — a language construct, a
// referenced language — is imported once and used through
// InternalNodeRefs to the entry (see Imports). A typed document without
// the imports-shaped second child has no dependencies and no standard
// content slot; `internalize` leaves its refs inline.
//
// The slots are positional.

// The document's type: the language/format ref on the root's data.
def typeRefOf(doc: Document[NodeData]): Option[ExternalNodeReference] =
  doc.getNode(doc.rootId).map(_.data).collect {
    case NodeData.ExternalNodeRef(ref) => ref
  }

def isTyped(doc: Document[NodeData]): Boolean =
  typeRefOf(doc).isDefined

// Whether the document follows the standard layout — detected by the
// imports-shaped second child: a node whose data is a reference
// (external, or internal once internalized; the def-doc encoder's
// pending markers are internal refs too) and whose children are all
// external-ref leaves — the import entries. A typed document that
// fails this check is treated as simple data: no title, no imports,
// no content slot. (Genuinely ambiguous: a simple document whose
// second child happens to be a bare ref leaf reads as an empty
// imports section.)
def hasStandardLayout(doc: Document[NodeData]): Boolean =
  isTyped(doc) && doc.childrenOf(doc.rootId).lift(1).exists { id =>
    doc.getNode(id).exists { n =>
      (n.data.isInstanceOf[NodeData.ExternalNodeRef] ||
        n.data.isInstanceOf[NodeData.InternalNodeRef]) &&
        n.childIds.forall(cid =>
          doc.getNode(cid).map(_.data)
            .exists(_.isInstanceOf[NodeData.ExternalNodeRef]))
    }
  }

// The title node: the root's first child, in a standard document. Its
// data references the core language's `title` declaration (imported,
// like every construct); the raw title text is its child.
def titleId(doc: Document[NodeData]): Option[Int] =
  if (hasStandardLayout(doc)) doc.childrenOf(doc.rootId).headOption
  else None

// The document's title text: the title node's first child.
def titleTextOf(doc: Document[NodeData]): Option[Int] =
  titleId(doc).flatMap(doc.childrenOf(_).headOption)

// The imports/dependencies section: the root's second child.
def importsId(doc: Document[NodeData]): Option[Int] =
  if (hasStandardLayout(doc)) doc.childrenOf(doc.rootId).lift(1) else None

// The content root: the root's child after title and imports.
def contentId(doc: Document[NodeData]): Option[Int] =
  if (hasStandardLayout(doc)) doc.childrenOf(doc.rootId).lift(2) else None

// Build a standard-layout document: the type ref on the root's data, a
// title node carrying `titleConstruct`, an imports node carrying
// `importsConstruct` (refs to the `title`/`imports` declarations — the
// encoder passes pending markers while lang: language's own schema is
// mid-build), then the content subtree. External refs inside the title
// and content subtrees — and the imports node's own construct ref —
// move into the imports section.
def makeTyped(
    typeRef: ExternalNodeReference,
    titleConstruct: NodeData,
    importsConstruct: NodeData,
    title: NodeData,
    content: DetachedNode[NodeData]): Document[NodeData] =
  internalizeStandard(beginTyped(typeRef, titleConstruct, importsConstruct,
    title, content))

// Internalize every ref in the standard slots: the title subtree, the
// imports node's own construct ref (its ext-ref-leaf children ARE the
// imports — they stay external), and the content subtree.
def internalizeStandard(
    doc: Document[NodeData]): Document[NodeData] = {
  val d = Seq(titleId(doc), contentId(doc)).flatten
    .foldLeft(doc)((d, id) => internalizeRefs(d, id).getOrElse(d))
  importsId(d).flatMap(iid =>
    d.getNode(iid).map(_.data) match {
      case Some(NodeData.ExternalNodeRef(r)) =>
        importRef(d, r).toOption.flatMap { case (d2, entry) =>
          updateNodeData(iid, NodeData.InternalNodeRef(entry), d2).toOption }
      case _ => None
    }).getOrElse(d)
}

// The standard skeleton — refs not yet internalized.
def beginTyped(
    typeRef: ExternalNodeReference,
    titleConstruct: NodeData,
    importsConstruct: NodeData,
    title: NodeData,
    content: DetachedNode[NodeData]): Document[NodeData] =
  buildDocument(
    DetachedNode(NodeData.ExternalNodeRef(typeRef), List(
      DetachedNode(titleConstruct, List(DetachedNode.leaf(title))),
      DetachedNode.leaf(importsConstruct),
      content)))
