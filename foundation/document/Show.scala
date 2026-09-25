package opelan.foundation.document

// Debug rendering of documents as lists of strings.
object Show {

  def showResult[A](show: A => List[String], result: Either[String, A]): List[String] =
    result match {
      case Right(x)   => show(x)
      case Left(err)  => List(err)
    }

  def showDocument(document: Document[NodeData]): List[String] = {
    val lines = document.nodes.zipWithIndex.map { case (node, i) =>
      s"n:$i ${showNode(node)}"
    }
    s"root:${document.rootId}" +: lines.toList
  }

  def showNode(node: Node[NodeData]): String = {
    val content =
      s"d:(${showNodeData(node.data)}) " +
      s"p:(${node.parentId.map(_.toString).getOrElse("")}) " +
      s"c:(${node.childIds.mkString(",")})"
    s"v:${node.version} - $content"
  }

  def showNodeData(data: NodeData): String = data match {
    case NodeData.StringData(s)        => s"\"$s\""
    case NodeData.IntData(i)           => i.toString
    case NodeData.FloatData(f)         => f.toString
    case NodeData.InternalNodeRef(id)  => s"ref:$id"
    case NodeData.ExternalNodeRef(ref) => s"ref:${ref.documentUrl}@${ref.documentVersionId}#${ref.nodeId}"
  }
}
