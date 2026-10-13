package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.language.{languageForRef, Language}

// A Language interpreted from a LanguageDef — the bootstrap mechanism:
// the def carries the grammar as data and names its semantics through
// the builtins registry, so a document-encoded def (read by
// langDefFromDocument) becomes a live language without touching code.
//
// `resolve` finds the language a cell grammar refers to; it defaults to
// the Scala registry — the honest fallback until DocLanguage resolves
// through the document store.
class DefLanguage(
    d: LanguageDef,
    resolve: ExternalNodeReference => Option[Language] = languageForRef)
    extends Language {

  def typeRef = d.typeRef
  def name = d.name
  def views = d.views.map(_.name)

  // The def's document is its encoding — the same bytes a lang: doc
  // holds in the store.
  def langDoc = langDefDoc(d)

  private lazy val syntax: Option[(String => Vector[GTok], Parser)] =
    for {
      s <- d.syntax
      lx <- builtinLexers.get(s.lexer)
    } yield (lx, interpret(s.grammar))

  def render(view: String, doc: Document[NodeData])
      : Either[String, DetachedNode[NodeData]] =
    d.views.find(_.name == view) match {
      case None => Left(s"$name has no '$view' view")
      case Some(vb) => builtinViews.get(vb.builtin) match {
        case None => Left(s"$name: unknown builtin view '${vb.builtin}'")
        case Some(b) => contentId(doc)
          .toRight(s"$name: typed document has no content")
          .map(id => b(doc, id))
      }
    }

  // Total like every recovering grammar: Some whenever a syntax exists.
  override def parse(text: String): Option[DetachedNode[NodeData]] =
    syntax.map { case (lx, g) => toDetached(g(In(text, lx(text)), 0).tree) }

  override def parseCell(text: String): Option[DetachedNode[NodeData]] =
    d.cellParser.map {
      case CellParserDef.Builtin(name) =>
        builtinCellParsers.get(name)
          .map(_(text))
          .getOrElse(DetachedNode.leaf(NodeData.GapData(text)))
      case CellParserDef.Language(ref, fb) => cellVia(ref, fb, text)
    }

  // A cell parsed by another language's grammar: explicit "?"-holes and
  // whitespace-only cells aside, the referenced language's total parse
  // either yields real content or — when the root is a pure hole — the
  // fallback value (a label string or a gap).
  private def cellVia(
      ref: ExternalNodeReference, fb: CellFallback,
      text: String): DetachedNode[NodeData] =
    if (text.startsWith("?")) DetachedNode.leaf(NodeData.GapData(text.drop(1)))
    else if (text.trim.isEmpty) DetachedNode.leaf(fallbackData(fb, ""))
    else resolve(ref).flatMap(_.parse(text)) match {
      case Some(t) if isHoleLeaf(t) =>
        DetachedNode.leaf(fallbackData(fb, text))
      case Some(t) => t
      case None    => DetachedNode.leaf(fallbackData(fb, text))
    }

  private def isHoleLeaf(t: DetachedNode[NodeData]): Boolean =
    t.children.isEmpty && t.data.isInstanceOf[NodeData.GapData]

  private def fallbackData(fb: CellFallback, text: String): NodeData =
    fb match {
      case CellFallback.Label => NodeData.StringData(text)
      case CellFallback.Hole  => NodeData.GapData(text)
    }
}
