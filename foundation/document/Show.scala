package opelan.foundation.document

// Debug rendering of documents as lists of strings.

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
    s"d:(${NodeDataText.show(node.data)}) " +
    s"p:(${node.parentId.map(_.toString).getOrElse("")}) " +
    s"c:(${node.childIds.mkString(",")})"
  s"v:${node.version} - $content"
}
