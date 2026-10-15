package opelan.foundation.document

import scala.collection.mutable

// The storage/sync codec: the outline format with each line prefixed by
// its node id — `#17 "mul"`. Unlike Outline.render/parse (the human
// text surface, which renumbers nodes on every parse), it preserves node
// identity, which documents depend on: InternalNodeRefs (imports,
// construct uses, cell refs) name ids, and other documents' external
// refs name `url@v#id`. A saved, reloaded, or synced document keeps
// every id. Unreachable nodes aren't written; their ids come back as
// unreachable placeholders, so ids are never reassigned.
//
// Text without ids (persisted before this codec) falls back to the
// plain outline parse.

def serialize(doc: Document[NodeData]): String =
  doc.preorder(doc.rootId).map { case (id, depth) =>
    "  " * depth + s"#$id " +
      NodeDataText.show(doc.getNode(id).map(_.data).getOrElse(NodeData.GapData("")))
  }.mkString("\n")

private val IdLine = "#(\\d+) (.*)".r

def deserialize(text: String): Option[Document[NodeData]] = {
  val lines = text.split("\n", -1).toList.filter(_.trim.nonEmpty)
  if (lines.isEmpty) None
  else if (!lines.head.trim.startsWith("#")) parse(text)
  else Some(fromEntries(lines.map { line =>
    val indent = line.takeWhile(c => c == ' ' || c == '\t').length
    line.trim match {
      case IdLine(id, rest) => (indent, id.toIntOption, NodeDataText.parse(rest))
      case other            => (indent, None, NodeDataText.parse(other))
    }
  }.toVector))
}

// Rebuild from (indent, stated id, data) rows. A missing or duplicate id
// (concurrent edits merged as text can produce one) gets a fresh id past
// the largest stated one.
private def fromEntries(
    rows: Vector[(Int, Option[Int], NodeData)]): Document[NodeData] = {
  val seen = mutable.Set.empty[Int]
  var next = rows.flatMap(_._2).maxOption.map(_ + 1).getOrElse(0)
  val ids = rows.map(_._2.filter(seen.add).getOrElse {
    next += 1; next - 1
  })
  val parents = parentRows(rows.map(_._1))
  val kids = parents.zipWithIndex.collect { case (Some(p), i) => p -> ids(i) }
    .groupMap(_._1)(_._2)
  val byId = rows.indices.map { i =>
    ids(i) -> Node(0, rows(i)._3, kids.getOrElse(i, Vector.empty).toList,
      parents(i).map(ids))
  }.toMap
  Document(ids(0), Vector.tabulate(next)(id =>
    byId.getOrElse(id, Node[NodeData](0, NodeData.GapData(""), Nil, None))))
}

// Each row's parent row: the nearest earlier row indented less. Rows not
// indented past the first one still hang off it — it is the root.
private def parentRows(indents: Vector[Int]): Vector[Option[Int]] = {
  val stack = mutable.Stack.empty[Int]
  indents.indices.map { i =>
    while (stack.nonEmpty && indents(stack.top) >= indents(i)) stack.pop()
    val p = if (i == 0) None else Some(stack.headOption.getOrElse(0))
    stack.push(i)
    p
  }.toVector
}
