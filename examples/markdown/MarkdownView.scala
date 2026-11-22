package opelan.examples.markdown

import opelan.examples.isTerm
import opelan.foundation.document._
import opelan.foundation.langdef.ViewContext
import opelan.foundation.view.ViewTree._

// The "preview" view: the content rendered as a formatted document —
// the tree the markdown text parses to, shown read-only. Blocks carry
// provenance, so a click selects the source node.
def markdownPreview(
    doc: Document[NodeData], contentId: Int,
    ctx: ViewContext = ViewContext()): DetachedNode[NodeData] =
  block(doc.childrenOf(contentId).map(blockView(doc, _))*)

private def blockView(
    doc: Document[NodeData], id: Int): DetachedNode[NodeData] =
  if (isTerm(markdownTerms, H1Tag, doc, id))
    heading(src(id), styled("h1", inlines(doc, id)*))
  else if (isTerm(markdownTerms, H2Tag, doc, id))
    heading(src(id), styled("h2", inlines(doc, id)*))
  else if (isTerm(markdownTerms, ListTag, doc, id))
    block(src(id) +: doc.childrenOf(id).map(item =>
      flow((src(item) +: styled("marker", txt("• ")) +:
        inlines(doc, item))*))*)
  else
    flow((src(id) +: inlines(doc, id))*)

private def inlines(
    doc: Document[NodeData], id: Int): List[DetachedNode[NodeData]] =
  doc.childrenOf(id).flatMap(k =>
    if (isTerm(markdownTerms, RunsTag, doc, k)) inlines(doc, k)
    else if (isTerm(markdownTerms, BoldTag, doc, k))
      List(styled("bold", from(k, inlines(doc, k)*)))
    else if (isTerm(markdownTerms, ItalicTag, doc, k))
      List(styled("italic", from(k, inlines(doc, k)*)))
    else List(from(k, txt(leafText(doc, k)))))

private def leafText(doc: Document[NodeData], id: Int): String =
  doc.getNode(id).map(_.data).collect {
    case NodeData.StringData(s) => s
    case d => NodeDataText.show(d)
  }.getOrElse("?")
