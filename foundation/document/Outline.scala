package opelan.foundation.document

// Text codec for raw documents: one line per node, indentation encodes
// nesting (outliner-style). The first line is the root; a node owns every
// following line indented deeper than itself.
//
// Line payloads use Show.showNodeData's surface syntax:
//   "abc"   string      42 / 1.5      int / float
//   ref:5   internal    ref:url@v#n   external ref
//   ?text   gap         bare          string
object Outline {

  def render(doc: Document[NodeData]): String =
    renderSubtree(doc, doc.rootId)

  def renderSubtree(doc: Document[NodeData], nodeId: Int): String = {
    def rows(id: Int, depth: Int): List[String] =
      ("  " * depth + Show.showNodeData(doc.getNode(id).get.data)) ::
        doc.childrenOf(id).flatMap(cid => rows(cid, depth + 1))
    rows(nodeId, 0).mkString("\n")
  }

  // Replace the whole document: first line becomes the root's data, the
  // rest its subtree. The root node id — and so document identity — is
  // preserved.
  def apply(doc: Document[NodeData], text: String): Either[String, Document[NodeData]] =
    parseLines(text) match {
      case None => Left("Outline: no content")
      case Some((rootData, children)) =>
        replaceSubtree(doc, doc.rootId, rootData, children)
    }

  // Replace one node's data and children from outline text whose first
  // line is that node's data.
  def applyToSubtree(
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
      d1 <- Edit.updateNodeData(nodeId, data, doc)
      d2 <- doc.childrenOf(nodeId).foldLeft[Either[String, Document[NodeData]]](Right(d1))(
        (acc, cid) => acc.flatMap(d => Edit.cutNode(cid, d)))
      d3 <- children.zipWithIndex.foldLeft[Either[String, Document[NodeData]]](Right(d2)) {
        case (acc, (child, i)) => acc.flatMap(d => Edit.insertNode(child, nodeId, i, d))
      }
    } yield d3

  private def parseLines(text: String): Option[(NodeData, List[DetachedNode[NodeData]])] = {
    val entries = text.split("\n", -1).toList
      .filter(_.trim.nonEmpty)
      .map { line =>
        val indent = line.takeWhile(c => c == ' ' || c == '\t').length
        (indent, parseData(line.trim))
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

  private def parseData(text: String): NodeData = text match {
    case s if s.length >= 2 && s.startsWith("\"") && s.endsWith("\"") =>
      NodeData.StringData(s.substring(1, s.length - 1))
    case s if s.startsWith("?") => NodeData.GapData(s.drop(1))
    case s if s.matches("ref:\\d+") => NodeData.InternalNodeRef(s.drop(4).toInt)
    case s if s.matches("ref:.+@\\d+#\\d+") =>
      val body = s.drop(4)
      NodeData.ExternalNodeRef(ExternalNodeReference(
        body.take(body.lastIndexOf('@')),
        body.substring(body.lastIndexOf('@') + 1, body.lastIndexOf('#')).toInt,
        body.drop(body.lastIndexOf('#') + 1).toInt))
    case s if s.matches("-?\\d+")        => NodeData.IntData(s.toInt)
    case s if s.matches("-?\\d*\\.\\d+") => NodeData.FloatData(s.toDouble)
    case s                             => NodeData.StringData(s)
  }
}
