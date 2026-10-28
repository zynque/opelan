package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.grammar._
import opelan.foundation.langdef.doc._
import opelan.foundation.langdef.syntax._
import opelan.foundation.means.{compileRules, desugarRuleSet, RuleSet}
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
// refers to; `interpretDef` runs a decoded definition as a live
// language — both store-dependent, both defaulting to nothing outside
// one.
class DefLanguage(
    val languageDef: LanguageDef,
    impls: Impls,
    resolve: ExternalNodeReference => Option[Language] = (_ => None),
    interpretDef: LanguageDef => Option[Language] = (_ => None))
    extends Language {

  def typeRef = languageDef.typeRef
  def name = languageDef.name
  def views = languageDef.views.map(_.name)

  // The def's document is its encoding — the same bytes a lang: doc
  // holds in the store.
  def langDoc = langDefDoc(languageDef)

  // What a parsed node of `term` carries: the construct's ref (inline —
  // the destination document imports it on insert). A term the
  // vocabulary doesn't define becomes a hole that keeps its parsed
  // children.
  def termTag(term: String): NodeData =
    termTags.getOrElse(term, NodeData.GapData(term))

  private lazy val termTags: Map[String, NodeData] =
    termRefs(languageDef).view.mapValues(NodeData.ExternalNodeRef(_)).toMap

  private lazy val syntax: Option[(Lexicon, Parser)] =
    for {
      s <- languageDef.syntax
      lexicon <- impls.lexicons.get(s.lexicon)
    } yield s.rules match {
      // Rules compile over the interpreted grammar as their operand
      // atom, under the lexicon's name — the `named`/`use` shape the
      // combinator form writes by hand. `phrase` supplies the to-EOF
      // recovery the combinator form encodes explicitly.
      case Some(rules) =>
        val atom = interpret(s.grammar, termTag)
        val lexicals = lexicalTerms(impls, leafTermNames(languageDef))
        // Phrase meanings desugar to term trees at interpretation —
        // the document form of *Phrases desugar at entry* (DESIGN §4).
        val desugared =
          desugarRuleSet(rules, atom, termTag, s.lexicon, lexicon, lexicals)
        (lexicon, phrase(compileRules(desugared, atom, termTag, s.lexicon,
          lexicals), Set.empty))
      case None => (lexicon, interpret(s.grammar, termTag))
    }

  def render(view: String, doc: Document[NodeData])
      : Either[String, DetachedNode[NodeData]] =
    renderWith(view, doc, HoleRetry())

  override def renderWith(
      view: String, doc: Document[NodeData], retry: HoleRetry,
      docUrl: Option[String] = None)
      : Either[String, DetachedNode[NodeData]] =
    languageDef.views.find(_.name == view) match {
      case None => Left(s"$name has no '$view' view")
      case Some(vb) => impls.views.get(vb.builtin) match {
        case None => Left(s"$name: unknown builtin view '${vb.builtin}'")
        case Some(b) => contentId(doc)
          .toRight(s"$name: typed document has no content")
          .map(id => b(doc, id, ViewContext(
            vb.settings, inputs.parse(retry), interpretDef, docUrl)))
      }
    }

  // Total like every recovering grammar: Some whenever a syntax exists.
  override def parse(text: String): Option[DetachedNode[NodeData]] =
    syntax.map { case (lexicon, g) => toDetached(g(In(text, lexicon), 0).tree) }

  // The interpreted grammar as an incremental parser. Its key is the
  // grammar data plus the construct refs it emits (they pin the def's
  // version), so a session survives re-decoding an unchanged def; after
  // an edit to the def, only rules whose definitions changed rerun —
  // and a version bump re-pins kept content's term tags rather than
  // reparsing.
  override def textSyntax: Option[TextSyntax] =
    for {
      s <- languageDef.syntax
      (lexicon, parser) <- syntax
    } yield TextSyntax(parser, lexicon,
      (s.lexicon, s.grammar, s.rules, termTags),
      changedSince = previous => previous.key match {
        case (s.lexicon, before: GrammarDef,
              beforeRules: Option[RuleSet] @unchecked,
              beforeTags: Map[String, NodeData] @unchecked) =>
          Some(SyntaxChange(
            syntaxChanges(SyntaxDef(s.lexicon, before, beforeRules), s),
            retagBy(beforeTags, termTag)))
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
