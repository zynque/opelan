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
// The imports section is the document's dependencies: every external
// node the content uses — a language construct, a referenced language —
// is imported once and used through InternalNodeRefs to the entry
// (see Imports). A typed document without the imports-shaped second
// child has no dependencies and no standard content slot; `internalize`
// leaves its refs inline.
//
// The slots are positional; the imports node's label is only a caption.

// The document's type: the language/format ref on the root's data.
def typeRefOf(doc: Document[NodeData]): Option[ExternalNodeReference] =
  doc.getNode(doc.rootId).map(_.data).collect {
    case NodeData.ExternalNodeRef(ref) => ref
  }

def isTyped(doc: Document[NodeData]): Boolean =
  typeRefOf(doc).isDefined

val ImportsLabel = "imports"

// Whether the document follows the standard layout — detected by the
// imports-shaped second child: a node labeled ImportsLabel, or one
// whose children are all external-ref leaves. A typed document that
// fails this check is treated as simple data: no title, no imports,
// no content slot.
def hasStandardLayout(doc: Document[NodeData]): Boolean =
  isTyped(doc) && doc.childrenOf(doc.rootId).lift(1).exists { id =>
    doc.getNode(id).exists { n =>
      n.data == NodeData.StringData(ImportsLabel) ||
        (n.childIds.nonEmpty && n.childIds.forall(cid =>
          doc.getNode(cid).map(_.data)
            .exists(_.isInstanceOf[NodeData.ExternalNodeRef])))
    }
  }

// The title node: the root's first child, in a standard document.
def titleId(doc: Document[NodeData]): Option[Int] =
  if (hasStandardLayout(doc)) doc.childrenOf(doc.rootId).headOption
  else None

// The imports/dependencies section: the root's second child.
def importsId(doc: Document[NodeData]): Option[Int] =
  if (hasStandardLayout(doc)) doc.childrenOf(doc.rootId).lift(1) else None

// The content root: the root's child after title and imports.
def contentId(doc: Document[NodeData]): Option[Int] =
  if (hasStandardLayout(doc)) doc.childrenOf(doc.rootId).lift(2) else None

// Build a standard-layout document: the type ref on the root, a title
// child, an imports section, then the content subtree. External refs
// inside `content` (constructs written inline by a builder or parser)
// move into the imports section.
def makeTyped(
    typeRef: ExternalNodeReference,
    title: NodeData,
    content: DetachedNode[NodeData]): Document[NodeData] = {
  val doc = beginTyped(typeRef, title, content)
  contentId(doc).flatMap(cid => internalizeRefs(doc, cid).toOption)
    .getOrElse(doc)
}

// The standard skeleton with `content` as-is — refs not yet internalized.
def beginTyped(
    typeRef: ExternalNodeReference,
    title: NodeData,
    content: DetachedNode[NodeData]): Document[NodeData] =
  buildDocument(
    DetachedNode(NodeData.ExternalNodeRef(typeRef), List(
      DetachedNode.leaf(title),
      DetachedNode.leaf(NodeData.StringData(ImportsLabel)),
      content)))
