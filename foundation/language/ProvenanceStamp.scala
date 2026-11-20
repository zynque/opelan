package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.grammar._

// Writing provenance: the one place ownership chains are written, so
// derive and adopt can't disagree about what "original" means.

private def nodeAtPath(
    doc: Document[NodeData], rootId: Int, path: List[Int]): Option[Int] =
  path.foldLeft(Option(rootId))((id, i) =>
    id.flatMap(n => doc.childrenOf(n).lift(i)))

// The chain an unowned character takes when a node claims it: the
// node's id, then its ancestors while none of them already owns
// characters — a retained ancestor closes the chain rather than
// gaining ownership it never had.
private def claim(
    doc: Document[NodeData], id: Int,
    owned: Int => Boolean): List[Int] = {
  def up(id: Int): List[Int] = doc.parentOf(id).toList
    .flatMap(p => if (owned(p)) Nil else p +: up(p))
  id +: up(id)
}

// Every unowned character's chain as the deepest covering node,
// post-order: children offer first, a node offers only what its
// children left. A retained node (one already owning characters
// somewhere) offers nothing. A character whose chain's innermost node
// the commit detached is unowned again — whatever survives under the
// root now produces it.
private[language] def stamp(
    doc: Document[NodeData], rootId: Int,
    spanned: SpannedTree[NodeData],
    owners: Vector[List[Int]]): Vector[List[Int]] = {
  val owned = owners.iterator.flatMap(identity).toSet
  val live = doc.subtreeIds(rootId).toSet
  def go(t: SpannedTree[NodeData], path: List[Int],
      os: Vector[List[Int]]): Vector[List[Int]] = {
    val deeper = t.children.zipWithIndex.foldLeft(os) {
      case (o, (child, i)) => go(child, path :+ i, o)
    }
    nodeAtPath(doc, rootId, path).fold(deeper) { id =>
      if (owned(id)) deeper
      else (t.data.span.from until t.data.span.to.min(os.length))
        .foldLeft(deeper)((o, i) =>
          if (o(i).headOption.exists(live)) o
          else o.updated(i, claim(doc, id, owned)))
    }
  }
  go(spanned, Nil, owners)
}

// Provenance freshly derived from the document's own print: the
// printed text's spans stamped with the ids at their paths — a freshly
// printed surface's characters are all original to their printer.
// `retext` aligns when the editor's text has drifted from the print.
private def deriveProvenance(
    doc: Document[NodeData],
    languageFor: Document[NodeData] => Option[Language],
    language: Language,
    text: String): TextProvenance = {
  val printed = docText(doc, languageFor)
  val owners = language.parseSpanned(printed).flatMap(spanned =>
      contentId(doc).map(cid =>
        stamp(doc, cid, spanned, Vector.fill(printed.length)(Nil))))
    .getOrElse(Vector.fill(printed.length)(Nil))
  TextProvenance(doc, printed, owners).retext(text)
}

// A text-surface edit through the surface's character provenance:
// `provenance` (re-derived from `doc` when it names a different
// document) advanced to `text`, its owners hinting the commit; the
// committed document's nodes then claim the unowned characters.
// A language without spans commits untracked.
def applyTrackedText(
    doc: Document[NodeData],
    languageFor: Document[NodeData] => Option[Language],
    before: String,
    provenance: Option[TextProvenance],
    text: String): (Either[String, Document[NodeData]], Option[TextProvenance]) =
  languageFor(doc).flatMap(l => l.parseSpanned(text)) match {
    case Some(spanned) =>
      applyTrackedContent(doc, languageFor, before, provenance, text,
        unspanned(spanned), spanned)
    case _ => (applyDocText(doc, languageFor, text), None)
  }

// The tracked commit with the parse already in hand — a text session's
// `content`/`spannedContent` are the same tree, so the session's
// incremental work isn't reparsed. `provenance` names `before`'s
// characters; re-derived from `doc` when it doesn't.
def applyTrackedContent(
    doc: Document[NodeData],
    languageFor: Document[NodeData] => Option[Language],
    before: String,
    provenance: Option[TextProvenance],
    text: String,
    content: DetachedNode[NodeData],
    spanned: SpannedTree[NodeData]): (Either[String, Document[NodeData]], Option[TextProvenance]) =
  languageFor(doc) match {
    case Some(lang) =>
      val current = provenance
        .filter(p => (p.doc eq doc) && p.text == before)
        .getOrElse(deriveProvenance(doc, languageFor, lang, before))
      val next = current.retext(text)
      val applied = applyDocContent(doc, languageFor, content,
        next.hints(spanned))
      applied match {
        case Right(d) => (applied,
          contentId(d).map(cid => next.adopt(d, cid, spanned)))
        case Left(_)  => (applied, None)
      }
    case _ => (applyDocContent(doc, languageFor, content), None)
  }
