package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.view.ViewTree

// The text surface of a document: typed documents use their language's
// syntax for the content; everything else falls back to the outline
// format (one line per node, indentation = nesting). Resolution is
// injected — the caller decides how a type ref finds a language (the
// examples' store-aware resolver, for instance).

def docText(
    doc: Document[NodeData],
    languageFor: Document[NodeData] => Option[Language]): String =
  languageFor(doc) match {
    // `print` is the text-projection convention — a language may keep
    // it renderable without listing it in `views` (the lang: doc's
    // `means`-line outline). No print render falls back to the
    // structural outline.
    case Some(lang) =>
      lang.render("print", doc).map(ViewTree.flatten)
        .getOrElse(contentOutline(doc))
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
    case Some(content) => replaceContent(doc, content, languageFor)
    case None =>
      if (isTyped(doc))
        contentId(doc) match {
          case Some(cid) => applyOutlineToSubtree(doc, cid, text)
          case None      => Left("typed document has no content node")
        }
      else applyOutline(doc, text)
  }

// Number of gaps in the reachable document — feedback for the status bar.
def gapCount(doc: Document[NodeData]): Int =
  reachableIds(doc).count(id =>
    doc.getNode(id).exists(_.data.isInstanceOf[NodeData.GapData]))

private def contentOutline(doc: Document[NodeData]): String =
  if (isTyped(doc))
    contentId(doc).map(cid => renderSubtree(doc, cid)).getOrElse("")
  else render(doc)

// Commit already-parsed content (e.g. a TextSession's) the way
// applyDocText commits a parse.
def applyDocContent(
    doc: Document[NodeData],
    languageFor: Document[NodeData] => Option[Language],
    content: DetachedNode[NodeData]): Either[String, Document[NodeData]] =
  replaceContent(doc, content, languageFor)

private def replaceContent(
    doc: Document[NodeData],
    content: DetachedNode[NodeData],
    languageFor: Document[NodeData] => Option[Language])
    : Either[String, Document[NodeData]] = {
  val inserted = contentId(doc) match {
    case Some(cid) => commitContent(cid, content, doc)
    case None =>
      insertNode(content, doc.rootId, math.min(2, doc.childrenOf(doc.rootId).length), doc)
  }
  inserted.flatMap(d => contentId(d).map(cid =>
    internalizeRefs(d, cid).map(d2 =>
      languageFor(d2).map(_.linkCommitted(d2, cid)).getOrElse(d2)))
    .getOrElse(Right(d)))
}
