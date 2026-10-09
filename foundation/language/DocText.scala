package opelan.foundation.language

import opelan.foundation.document._

// The text surface of a document: typed documents use their language's
// syntax for the content cell; everything else falls back to the outline
// codec (one line per node, indentation = nesting).

def docText(doc: Document[NodeData]): String =
  languageForDoc(doc) match {
    case Some(lang) if lang.views.contains("print") =>
      lang.render("print", doc).map(Frag.flatten).getOrElse(contentOutline(doc))
    case _ => contentOutline(doc)
  }

// Apply edited text back onto the document, returning the new document.
// Typed documents with a language parser replace only the content
// subtree; the root and type nodes — document identity — are preserved.
def applyDocText(doc: Document[NodeData], text: String): Either[String, Document[NodeData]] =
  languageForDoc(doc).flatMap(_.parse(text)) match {
    case Some(content) => replaceContent(doc, content)
    case None =>
      if (isTyped(doc))
        contentId(doc) match {
          case Some(cid) => applyOutlineToSubtree(doc, cid, text)
          case None      => Left("typed document has no content node")
        }
      else applyOutline(doc, text)
  }

// Number of holes in the reachable document — feedback for the status bar.
def holeCount(doc: Document[NodeData]): Int =
  reachableIds(doc).count(id =>
    doc.getNode(id).exists(_.data.isInstanceOf[NodeData.GapData]))

private def contentOutline(doc: Document[NodeData]): String =
  if (isTyped(doc))
    contentId(doc).map(cid => renderSubtree(doc, cid)).getOrElse("")
  else render(doc)

private def replaceContent(
    doc: Document[NodeData],
    content: DetachedNode[NodeData]): Either[String, Document[NodeData]] =
  contentId(doc) match {
    case Some(cid) =>
      val index = doc.childrenOf(doc.rootId).indexOf(cid)
      cutNode(cid, doc).flatMap(d => insertNode(content, doc.rootId, index, d))
    case None =>
      insertNode(content, doc.rootId, math.min(1, doc.childrenOf(doc.rootId).length), doc)
  }
