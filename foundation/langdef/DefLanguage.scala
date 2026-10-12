package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.language.Language

// A Language interpreted from a LanguageDef — the bootstrap mechanism:
// the def carries the grammar as data and names its semantics through
// the builtins registry, so a document-encoded def (M2 step 9) becomes
// a live language without touching code.
class DefLanguage(d: LanguageDef) extends Language {

  def typeRef = d.typeRef
  def name = d.name
  def views = d.views.map(_.name)
  def langDoc = d.doc

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
    syntax.map { case (lx, g) => toDetached(go(text, lx, g).tree) }

  private def go(text: String, lx: String => Vector[GTok], g: Parser) =
    g(In(text, lx(text)), 0)

  override def parseCell(text: String): Option[DetachedNode[NodeData]] =
    d.cellParser.flatMap(builtinCellParsers.get).map(_(text))
}
