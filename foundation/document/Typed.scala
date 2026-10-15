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
//   ├── imports node         one ExternalNodeRef leaf per imported node
//   ├── title node           the document's name (Store.labelOf)
//   └── content node         AST root in the type's language
//
// The imports section comes first so it is in scope for everything
// after it. It is itself a construct use — a direct ExternalNodeRef to
// the core language's `imports` declaration (self-importing it would be
// circular) — holding one ExternalNodeRef leaf per imported node. Every
// external node used later in the document — a language construct, a
// referenced language, the `title` construct on the title node — is
// imported once and used through InternalNodeRefs to the entry
// (see Imports). A typed document without the imports-shaped first
// child has no dependencies and no standard title/content slots;
// `internalize` leaves its refs inline.
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
// imports-shaped first child: a node whose data is a reference
// (the imports construct — normally an external ref; the def-doc
// encoder's pending markers are internal refs) and whose children are
// all external-ref leaves — the import entries. A typed document that
// fails this check is treated as simple data: no title, no imports,
// no content slot. (Genuinely ambiguous: a simple document whose
// first child happens to be a bare ref leaf reads as an empty
// imports section.)
def hasStandardLayout(doc: Document[NodeData]): Boolean =
  isTyped(doc) && doc.childrenOf(doc.rootId).headOption.exists { id =>
    doc.getNode(id).exists { n =>
      (n.data.isInstanceOf[NodeData.ExternalNodeRef] ||
        n.data.isInstanceOf[NodeData.InternalNodeRef]) &&
        n.childIds.forall(cid =>
          doc.getNode(cid).map(_.data)
            .exists(_.isInstanceOf[NodeData.ExternalNodeRef]))
    }
  }

// The imports/dependencies section: the root's first child.
def importsId(doc: Document[NodeData]): Option[Int] =
  if (hasStandardLayout(doc)) doc.childrenOf(doc.rootId).headOption
  else None

// The title node: the root's child after the imports section. Its data
// references the core language's `title` declaration (imported, like
// every construct); the raw title text is its child.
def titleId(doc: Document[NodeData]): Option[Int] =
  if (hasStandardLayout(doc)) doc.childrenOf(doc.rootId).lift(1) else None

// The document's title text: the title node's first child.
def titleTextOf(doc: Document[NodeData]): Option[Int] =
  titleId(doc).flatMap(doc.childrenOf(_).headOption)

// The content root: the root's child after imports and title.
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

// Internalize every ref in the standard slots: the title subtree and
// the content subtree. The imports node's own construct ref stays
// external — self-importing it would be circular — and its ext-ref-leaf
// children ARE the imports.
def internalizeStandard(
    doc: Document[NodeData]): Document[NodeData] =
  Seq(titleId(doc), contentId(doc)).flatten
    .foldLeft(doc)((d, id) => internalizeRefs(d, id).getOrElse(d))

// The standard skeleton — refs not yet internalized.
def beginTyped(
    typeRef: ExternalNodeReference,
    titleConstruct: NodeData,
    importsConstruct: NodeData,
    title: NodeData,
    content: DetachedNode[NodeData]): Document[NodeData] =
  buildDocument(
    DetachedNode(NodeData.ExternalNodeRef(typeRef), List(
      DetachedNode.leaf(importsConstruct),
      DetachedNode(titleConstruct, List(DetachedNode.leaf(title))),
      content)))
