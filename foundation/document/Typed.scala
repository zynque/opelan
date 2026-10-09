package opelan.foundation.document

// A "raw" document has no constraints on its shape. A standard (typed)
// document's root has the document's *type* as its first child — an
// ExternalNodeRef to the document that defines it — and a single
// *content* node after that: the root of the abstract syntax tree in
// whatever language the type defines. (Same convention as olw-p1:
// typeOf = children.head, content = children(1).)
//
//   root                     container; data is the document's label
//   ├── type node            ExternalNodeRef(defining document)
//   └── content node         AST root in the type's language

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

// The content root: the root's child after the type node.
def contentId(doc: Document[NodeData]): Option[Int] =
  doc.childrenOf(doc.rootId).drop(1).headOption

// Build a typed document: root label, type node, then the content subtree.
def makeTyped(
    typeRef: ExternalNodeReference,
    rootData: NodeData,
    content: DetachedNode[NodeData]): Document[NodeData] =
  buildDocument(
    DetachedNode(rootData,
      List(DetachedNode.leaf(NodeData.ExternalNodeRef(typeRef)), content)))
