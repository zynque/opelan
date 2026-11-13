package opelan.examples.markdown

import opelan.examples.termOf
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
  termOf(markdownTerms, doc, id) match {
    case Some(H1Tag) =>
      heading(src(id), styled("h1", inlines(doc, id)*))
    case Some(H2Tag) =>
      heading(src(id), styled("h2", inlines(doc, id)*))
    case Some(ListTag) =>
      block(src(id) +: doc.childrenOf(id).map(item =>
        flow((src(item) +: styled("marker", txt("• ")) +:
          inlines(doc, item))*))*)
    case _ =>
      flow((src(id) +: inlines(doc, id))*)
  }

private def inlines(
    doc: Document[NodeData], id: Int): List[DetachedNode[NodeData]] =
  doc.childrenOf(id).map(k =>
    termOf(markdownTerms, doc, k) match {
      case Some(BoldTag)   => styled("bold", from(k, inlines(doc, k)*))
      case Some(ItalicTag) => styled("italic", from(k, inlines(doc, k)*))
      case _               => from(k, txt(leafText(doc, k)))
    })

private def leafText(doc: Document[NodeData], id: Int): String =
  doc.getNode(id).map(_.data).collect {
    case NodeData.StringData(s) => s
    case d => NodeDataText.show(d)
  }.getOrElse("?")
