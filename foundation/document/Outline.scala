package opelan.foundation.document

// Text codec for raw documents: one line per node, indentation encodes
// nesting (outliner-style). The first line is the root; a node owns every
// following line indented deeper than itself.
//
// Line payloads use NodeDataText's outline syntax:
//   "abc"   string      42 / 1.5      int / float
//   ref:5   internal    ref:url@v#n   external ref
//   ?text   gap         bare          string

def render(doc: Document[NodeData]): String =
  renderSubtree(doc, doc.rootId)

// Parse outline text into a fresh document — the inverse of `render`.
// The first line becomes the root's data, deeper lines its subtree.
// Nothing else is preserved: node ids are re-assigned, node versions
// reset, and unreachable nodes cannot be represented.
def parse(text: String): Option[Document[NodeData]] =
  parseLines(text).map { case (data, children) =>
    buildDocument(DetachedNode(data, children))
  }

def renderSubtree(doc: Document[NodeData], nodeId: Int): String = {
  def rows(id: Int, depth: Int): List[String] =
    ("  " * depth + NodeDataText.show(doc.getNode(id).get.data)) ::
      doc.childrenOf(id).flatMap(cid => rows(cid, depth + 1))
  rows(nodeId, 0).mkString("\n")
}

// Replace the whole document: first line becomes the root's data, the
// rest its subtree. The root node id — and so document identity — is
// preserved.
def applyOutline(doc: Document[NodeData], text: String): Either[String, Document[NodeData]] =
  applyOutlineToSubtree(doc, doc.rootId, text)

// Replace one node's data and children from outline text whose first
// line is that node's data.
def applyOutlineToSubtree(
    doc: Document[NodeData],
    nodeId: Int,
    text: String): Either[String, Document[NodeData]] =
  parseLines(text) match {
    case None => Left("Outline: no content")
    case Some((data, children)) => replaceSubtree(doc, nodeId, data, children)
  }

private def replaceSubtree(
    doc: Document[NodeData],
    nodeId: Int,
    data: NodeData,
    children: List[DetachedNode[NodeData]]): Either[String, Document[NodeData]] =
  for {
    d1 <- updateNodeData(nodeId, data, doc)
    d2 <- doc.childrenOf(nodeId).foldLeft[Either[String, Document[NodeData]]](Right(d1))(
      (acc, cid) => acc.flatMap(d => cutNode(cid, d)))
    d3 <- children.zipWithIndex.foldLeft[Either[String, Document[NodeData]]](Right(d2)) {
      case (acc, (child, i)) => acc.flatMap(d => insertNode(child, nodeId, i, d))
    }
  } yield d3

private def parseLines(text: String): Option[(NodeData, List[DetachedNode[NodeData]])] = {
  val entries = text.split("\n", -1).toList
    .filter(_.trim.nonEmpty)
    .map { line =>
      val indent = line.takeWhile(c => c == ' ' || c == '\t').length
      (indent, NodeDataText.parse(line.trim))
    }
  entries match {
    case Nil           => None
    case head :: rest  => Some((head._2, block(rest)))
  }
}

// Each entry absorbs the following entries indented deeper than itself.
private def block(entries: List[(Int, NodeData)]): List[DetachedNode[NodeData]] =
  entries match {
    case Nil => Nil
    case (indent, data) :: tail =>
      val (kids, rest) = tail.span(_._1 > indent)
      DetachedNode(data, block(kids)) :: block(rest)
  }
