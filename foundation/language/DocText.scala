package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.view.ViewTree

// The text surface of a document: typed documents use their language's
// syntax for the content cell; everything else falls back to the outline
// codec (one line per node, indentation = nesting). Resolution is
// injected — the caller decides how a type ref finds a language (the
// examples' store-aware resolver, for instance).

def docText(
    doc: Document[NodeData],
    languageFor: Document[NodeData] => Option[Language]): String =
  languageFor(doc) match {
    case Some(lang) if lang.views.contains("print") =>
      lang.render("print", doc).map(ViewTree.flatten).getOrElse(contentOutline(doc))
    case _ => contentOutline(doc)
  }

// Apply edited text back onto the document, returning the new document.
// Typed documents with a language parser rewrite only the content
// subtree — in place, keeping the ids of whatever did not change
// (commitContent) — while the root, type, and imports are preserved,
// and constructs the parse wrote inline are imported.
def applyDocText(
    doc: Document[NodeData],
    languageFor: Document[NodeData] => Option[Language],
    text: String): Either[String, Document[NodeData]] =
  languageFor(doc).flatMap(_.parse(text)) match {
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
    content: DetachedNode[NodeData]): Either[String, Document[NodeData]] = {
  val inserted = contentId(doc) match {
    case Some(cid) => commitContent(cid, content, doc)
    case None =>
      insertNode(content, doc.rootId, math.min(2, doc.childrenOf(doc.rootId).length), doc)
  }
  inserted.flatMap(d => contentId(d).map(internalizeRefs(d, _)).getOrElse(Right(d)))
}
