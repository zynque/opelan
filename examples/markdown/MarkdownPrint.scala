package opelan.examples.markdown

import opelan.examples.isTerm
import opelan.foundation.document._
import opelan.foundation.document.DetachedNode

// The content subtree as markdown lines — the "print" builtin's text.
// A `markdown` root's children print block per line — `#`/`##` headings
// with their `runs` inline, `list` items as `* ` lines, `para`s bare;
// `runs` splices transparently wherever it nests. Anything else
// degrades to its outline payload.
def markdownLines(
    doc: Document[NodeData], contentId: Int): List[String] =
  if (isTerm(markdownTerms, MarkdownTag, doc, contentId))
    doc.childrenOf(contentId).flatMap(blockLines(doc, _))
  else List(doc.getNode(contentId)
    .map(n => NodeDataText.show(n.data)).getOrElse(""))

private def blockLines(doc: Document[NodeData], id: Int): List[String] =
  if (isTerm(markdownTerms, H1Tag, doc, id))
    List("# " + inlineText(doc, id))
  else if (isTerm(markdownTerms, H2Tag, doc, id))
    List("## " + inlineText(doc, id))
  else if (isTerm(markdownTerms, ListTag, doc, id))
    doc.childrenOf(id).map(item => "* " + inlineText(doc, item))
  else if (doc.childrenOf(id).isEmpty)
    List(doc.getNode(id)
      .map(n => NodeDataText.show(n.data)).getOrElse(""))
  else List(inlineText(doc, id))

private def inlineText(doc: Document[NodeData], id: Int): String =
  doc.childrenOf(id).map(k =>
    if (isTerm(markdownTerms, RunsTag, doc, k)) inlineText(doc, k)
    else if (isTerm(markdownTerms, BoldTag, doc, k))
      "**" + inlineText(doc, k) + "**"
    else if (isTerm(markdownTerms, ItalicTag, doc, k))
      "*" + inlineText(doc, k) + "*"
    else doc.getNode(k).map(_.data).collect {
      case NodeData.StringData(s) => s
      case d => NodeDataText.show(d)
    }.getOrElse("")).mkString
