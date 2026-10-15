package opelan.foundation.document

// A "raw" document has no constraints on its shape. A standard (typed)
// document's root has the document's *type* as its first child — an
// ExternalNodeRef to the document that defines it — then its *imports*
// section, then a single *content* node: the root of the abstract syntax
// tree in whatever language the type defines. (The olw-p2 layout:
// header, dependencies, content.)
//
//   root                     container; data is the document's label
//   ├── type node            ExternalNodeRef(defining document)
//   ├── imports node         one ExternalNodeRef leaf per imported node
//   └── content node         AST root in the type's language
//
// Content never names another document directly: every external node it
// uses — a language construct, a referenced language — is imported once
// and used through InternalNodeRefs to the import entry (see Imports).
// The slots are positional; the imports node's label is only a caption.

// Id of the type node: the root's first child.
def typeNodeId(doc: Document[NodeData]): Option[Int] =
  doc.childrenOf(doc.rootId).headOption

// The document's type, if its first child is an external reference.
def typeRefOf(doc: Document[NodeData]): Option[ExternalNodeReference] =
  typeNodeId(doc).flatMap(doc.getNode).map(_.data).collect {
    case NodeData.ExternalNodeRef(ref) => ref
  }

def isTyped(doc: Document[NodeData]): Boolean =
  typeRefOf(doc).isDefined

// The imports section: the root's second child, in a typed document.
def importsId(doc: Document[NodeData]): Option[Int] =
  if (isTyped(doc)) doc.childrenOf(doc.rootId).lift(1) else None

// The content root: the root's child after the imports section.
def contentId(doc: Document[NodeData]): Option[Int] =
  doc.childrenOf(doc.rootId).lift(2)

val ImportsLabel = "imports"

// Build a typed document: root label, type node, imports, then the
// content subtree. External refs inside `content` (constructs written
// inline by a builder or parser) move into the imports section.
def makeTyped(
    typeRef: ExternalNodeReference,
    rootData: NodeData,
    content: DetachedNode[NodeData]): Document[NodeData] = {
  val doc = beginTyped(typeRef, rootData, content)
  contentId(doc).flatMap(cid => internalizeRefs(doc, cid).toOption)
    .getOrElse(doc)
}

// The typed skeleton with `content` as-is — refs not yet internalized.
def beginTyped(
    typeRef: ExternalNodeReference,
    rootData: NodeData,
    content: DetachedNode[NodeData]): Document[NodeData] =
  buildDocument(
    DetachedNode(rootData, List(
      DetachedNode.leaf(NodeData.ExternalNodeRef(typeRef)),
      DetachedNode.leaf(NodeData.StringData(ImportsLabel)),
      content)))
