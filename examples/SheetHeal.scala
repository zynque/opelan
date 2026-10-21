package opelan.examples

import opelan.foundation.document._

// Holes heal when the language catches up. A hole cell keeps the text
// its grammar couldn't read; each render asks the current cell grammar
// for it (through the host's HoleRetry, which reparses only holes whose
// last attempt consulted a changed rule), and a cell that now parses is
// rendered as the formula it reads as. The document is unchanged — it still holds what
// the user wrote — so removing the production again re-breaks only the
// derived view (*Never broken*, *Reactive*).
def healHoles(
    doc: Document[NodeData], sheetId: Int,
    parseCell: String => Option[DetachedNode[NodeData]]): Document[NodeData] =
  doc.childrenOf(sheetId).flatMap(doc.childrenOf).foldLeft(doc) { (d, cellId) =>
    healableText(d, cellId).flatMap(parseCell).filter(heals) match {
      case Some(content) => commitContent(cellId, content, d).getOrElse(d)
      case None          => d
    }
  }

private def healableText(doc: Document[NodeData], cellId: Int): Option[String] =
  doc.getNode(cellId).collect {
    case n if n.childIds.isEmpty => n.data
  }.collect { case NodeData.GapData(t) if t.trim.nonEmpty => t }

// Healed means real content: not another hole, and not a label.
private def heals(t: DetachedNode[NodeData]): Boolean =
  t.children.nonEmpty || (t.data match {
    case _: NodeData.GapData | _: NodeData.StringData => false
    case _                                            => true
  })
