package opelan.examples.markdown

import opelan.examples.termOf
import opelan.foundation.document._
import opelan.foundation.document.DetachedNode

// The content subtree as markdown lines — the "print" builtin's text.
// A `markdown` root's children print block per line — `#`/`##` headings
// with their `runs` inline, `list` items as `* ` lines, `para`s bare;
// `runs` splices transparently wherever it nests. Anything else
// degrades to its outline payload.
def markdownLines(
    doc: Document[NodeData], contentId: Int): List[String] =
  if (termOf(markdownTerms, doc, contentId).contains(MarkdownTag))
    doc.childrenOf(contentId).flatMap(blockLines(doc, _))
  else List(doc.getNode(contentId)
    .map(n => NodeDataText.show(n.data)).getOrElse(""))

private def blockLines(doc: Document[NodeData], id: Int): List[String] =
  termOf(markdownTerms, doc, id) match {
    case Some(H1Tag)   => List("# " + inlineText(doc, id))
    case Some(H2Tag)   => List("## " + inlineText(doc, id))
    case Some(ListTag) =>
      doc.childrenOf(id).map(item => "* " + inlineText(doc, item))
    case _ if doc.childrenOf(id).isEmpty =>
      List(doc.getNode(id)
        .map(n => NodeDataText.show(n.data)).getOrElse(""))
    case _             => List(inlineText(doc, id))
  }

private def inlineText(doc: Document[NodeData], id: Int): String =
  doc.childrenOf(id).map(k =>
    termOf(markdownTerms, doc, k) match {
      case Some(RunsTag)   => inlineText(doc, k)
      case Some(BoldTag)   => "**" + inlineText(doc, k) + "**"
      case Some(ItalicTag) => "*" + inlineText(doc, k) + "*"
      case _ => doc.getNode(k).map(_.data).collect {
        case NodeData.StringData(s) => s
        case d => NodeDataText.show(d)
      }.getOrElse("")
    }).mkString
