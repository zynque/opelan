package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.language.{
  HoleRetry, InputSessions, Language, SyntaxChange, TextSyntax}

// A Language interpreted from a LanguageDef — the bootstrap mechanism:
// the def carries the grammar as data and names its semantics through
// an Impls bundle, so a document-encoded def (read by
// langDefFromDocument) becomes a live language without touching code.
//
// `impls` holds the implementations the def names — callers assemble it
// (e.g. the examples' implsFor binds the foundational bundle whose type
// ref the def claims). `resolve` finds the language an input grammar
// refers to; the default resolves nothing.
class DefLanguage(
    val languageDef: LanguageDef,
    impls: Impls,
    resolve: ExternalNodeReference => Option[Language] = (_ => None))
    extends Language {

  def typeRef = languageDef.typeRef
  def name = languageDef.name
  def views = languageDef.views.map(_.name)

  // The def's document is its encoding — the same bytes a lang: doc
  // holds in the store.
  def langDoc = langDefDoc(languageDef)

  // What a parsed node of `kind` carries: the construct's ref (inline —
  // the destination document imports it on insert). A kind the schema
  // doesn't define becomes a hole that keeps its parsed children.
  def kindTag(kind: String): NodeData =
    kindTags.getOrElse(kind, NodeData.GapData(kind))

  private lazy val kindTags: Map[String, NodeData] =
    kindRefs(languageDef).view.mapValues(NodeData.ExternalNodeRef(_)).toMap

  private lazy val syntax: Option[(Lexicon, Parser)] =
    for {
      s <- languageDef.syntax
      lexicon <- impls.lexicons.get(s.lexicon)
    } yield (lexicon, interpret(s.grammar, kindTag))

  def render(view: String, doc: Document[NodeData])
      : Either[String, DetachedNode[NodeData]] =
    renderWith(view, doc, HoleRetry())

  override def renderWith(
      view: String, doc: Document[NodeData], retry: HoleRetry)
      : Either[String, DetachedNode[NodeData]] =
    languageDef.views.find(_.name == view) match {
      case None => Left(s"$name has no '$view' view")
      case Some(vb) => impls.views.get(vb.builtin) match {
        case None => Left(s"$name: unknown builtin view '${vb.builtin}'")
        case Some(b) => contentId(doc)
          .toRight(s"$name: typed document has no content")
          .map(id => b(doc, id, ViewContext(vb.settings, inputs.parse(retry))))
      }
    }

  // Total like every recovering grammar: Some whenever a syntax exists.
  override def parse(text: String): Option[DetachedNode[NodeData]] =
    syntax.map { case (lexicon, g) => toDetached(g(In(text, lexicon), 0).tree) }

  // The interpreted grammar as an incremental parser. Its key is the
  // grammar data plus the construct refs it emits (they pin the def's
  // version), so a session survives re-decoding an unchanged def; after
  // an edit to the def, only rules whose definitions changed rerun —
  // and a version bump re-pins kept content's kind tags rather than
  // reparsing.
  override def textSyntax: Option[TextSyntax] =
    for {
      s <- languageDef.syntax
      (lexicon, parser) <- syntax
    } yield TextSyntax(parser, lexicon, (s.lexicon, s.grammar, kindTags),
      changedSince = previous => previous.key match {
        case (s.lexicon, before: GrammarDef,
              beforeTags: Map[String, NodeData] @unchecked) =>
          Some(SyntaxChange(
            changedRules(before, s.grammar), retagBy(beforeTags, kindTag)))
        case _ => None
      })

  override def parseInput(text: String): Option[DetachedNode[NodeData]] =
    inputs.parse(HoleRetry())(text)

  override def commitInput(id: Int, text: String, sessions: InputSessions)
      : Option[(DetachedNode[NodeData], InputSessions)] =
    inputs.commit(id, text, sessions)

  override def rememberHoles(
      doc: Document[NodeData], retry: HoleRetry): HoleRetry =
    rememberInputHoles(doc, inputs.syntax, retry)

  private lazy val inputs =
    InputGrammar(languageDef.inputParser, impls, resolve)
}
