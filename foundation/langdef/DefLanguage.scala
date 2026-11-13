package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.grammar._
import opelan.foundation.langdef.doc._
import opelan.foundation.langdef.syntax._
import opelan.foundation.means.RuleSet
import opelan.foundation.means.engine.{compileRules, rephraseRuleSet}
import opelan.foundation.language.{
  GapRetry, InputSessions, Language, SyntaxChange, TextSyntax}
import opelan.foundation.view.ViewTree

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

  // lang: language's own surfaces are bootstrap scaffolding: a def doc
  // interpreted as the meta language gets `edit` and `summary` whether
  // the stored document advertises them or not — a store seeded before
  // the rename still says `def`/`print` — so an old store's menu reads
  // like a fresh one's.
  private def isDocLanguage =
    languageDef.typeRef.documentUrl == langDefTypeRef.documentUrl &&
      languageDef.typeRef.nodeId == langDefTypeRef.nodeId

  // A stored meta doc's view entries under their current names: `def`
  // reads as `edit` (keeping the stored builtin); `print` is dropped —
  // `summary` supersedes it and comes from the code bindings below.
  private def docViewBinding(v: ViewBinding): Option[ViewBinding] =
    v.name match {
      case "def"   => Some(v.copy(name = "edit"))
      case "print" => None
      case _       => Some(v)
    }

  // The view bindings the meta language presents: decoded ones
  // renamed, then any bootstrap binding the stored doc lacks — so the
  // menu and the renders agree.
  private lazy val docViewBindings: List[ViewBinding] = {
    val renamed = languageDef.views.flatMap(docViewBinding)
    renamed ++ docLanguageDef.views.filterNot(b =>
      renamed.exists(_.name == b.name))
  }

  def views =
    if (isDocLanguage) docViewBindings.map(_.name)
    else languageDef.views.map(_.name)

  // The def's document is its encoding — the same bytes a lang: doc
  // holds in the store.
  def langDoc = langDefDoc(languageDef)

  // What a parsed node of `term` carries: the construct's ref (inline —
  // the destination document imports it on insert). A term the
  // vocabulary doesn't define becomes a gap that keeps its parsed
  // children.
  def termTag(term: String): NodeData =
    termTags.getOrElse(term, NodeData.GapData(term))

  // The ambient registry under the def's declared vocabulary — a
  // declared term wins over a placeholder or foundation name.
  private lazy val termTags: Map[String, NodeData] =
    (ambientRefs ++ termRefs(languageDef))
      .view.mapValues(NodeData.ExternalNodeRef(_)).toMap

  // A ref into another definition document resolves to that
  // definition — naming non-ambient refs at decode and binding their
  // names at commit.
  private def resolveDef(
      r: ExternalNodeReference): Option[LanguageDef] =
    resolve(r).collect { case d: DefLanguage => d.languageDef }

  // The term name a construct ref stands for — the ambient registry,
  // then the referenced definition's own vocabulary. ViewContext hands
  // it to views so a stored `+` node evals through the rule declaring
  // it, wherever its document lives.
  private def termName(r: ExternalNodeReference): Option[String] =
    ambientTermOf(r).map(_.name).orElse(
      resolveDef(r).flatMap(d => termRefs(d).collectFirst {
        case (n, tr) if sameNode(tr, r) => n }))

  private lazy val syntax: Option[(Lexicon, Parser)] =
    for {
      s <- languageDef.syntax
      lexicon <- impls.lexicons.get(s.lexicon)
    } yield s.rules match {
      // Rules compile over the lexicon's recovery as their operand
      // atom, under the lexicon's name — the `named`/`use` shape a
      // hand-written grammar memoizes by. `phrase` supplies the
      // to-EOF recovery.
      case Some(rules) =>
        val atom = unrecognized(lexicon.gapStart, lexicon.gapRun)
        val lexicals = lexicalTerms(impls)
        // Quoted-gap meanings — phrases the set couldn't yet read —
        // retry at interpretation and heal as their rules arrive.
        val rephrased =
          rephraseRuleSet(rules, atom, termTag, s.lexicon, lexicon, lexicals)
        (lexicon, phrase(compileRules(rephrased, atom, termTag, s.lexicon,
          lexicals), Set.empty))
      case None => (lexicon, restGap)
    }

  def render(view: String, doc: Document[NodeData])
      : Either[String, DetachedNode[NodeData]] =
    renderWith(view, doc, GapRetry())

  override def renderWith(
      view: String, doc: Document[NodeData], retry: GapRetry,
      docUrl: Option[String] = None)
      : Either[String, DetachedNode[NodeData]] =
    // "print" isn't a listed view — it's the lang: doc's text surface
    // (docText): the content as an outline of `means` lines.
    if (isDocLanguage && view == "print")
      contentId(doc).toRight(s"$name: typed document has no content")
        .map(id => ViewTree.block(defDocLines(
          doc, docUrl.getOrElse(""), id, resolveDef).map(ViewTree.txt)*))
    else {
      val bindings =
        if (isDocLanguage) docViewBindings else languageDef.views
      bindings.find(_.name == view).map(vb => (vb, vb.settings)) match {
        case Some((vb, settings)) =>
          impls.views.get(vb.builtin) match {
            case None => Left(s"$name: unknown builtin view '${vb.builtin}'")
            case Some(b) => runBuiltin(b, settings, doc, retry, docUrl)
          }
        // An unlisted builtin is still renderable by its own name —
        // `print` renders a language's text surface (docText) without
        // claiming a slot in the view menu.
        case None => impls.views.get(view) match {
          case Some(b) => runBuiltin(b, Map.empty, doc, retry, docUrl)
          case None    => Left(s"$name has no '$view' view")
        }
      }
    }

  private def runBuiltin(
      b: BuiltinView, settings: Map[String, String],
      doc: Document[NodeData], retry: GapRetry, docUrl: Option[String])
      : Either[String, DetachedNode[NodeData]] =
    contentId(doc)
      .toRight(s"$name: typed document has no content")
      .map(id => b(doc, id, ViewContext(
        settings, inputs.parse(retry), interpretDef, docUrl, termName)))

  // Total like every recovering grammar: Some whenever a syntax exists —
  // and always for the meta language, whose text form is the format in
  // doc/DefDocText.scala rather than a rule set.
  override def parse(text: String): Option[DetachedNode[NodeData]] =
    if (isDocLanguage) Some(defDocParse(text))
    else syntax.map { case (lexicon, g) =>
      toDetached(g(ParseInput(text, lexicon), 0).tree) }
      .orElse(impls.textParser.map(_(text)))

  // lang: documents relink what text can't carry — placeholder uses and
  // term constructs pointed at their nodes — after a text-surface
  // commit lands (the same links the doc format writes).
  override def linkCommitted(
      doc: Document[NodeData], rootId: Int): Document[NodeData] =
    if (isDocLanguage)
      linkTerms(linkRuleUses(doc, rootId), rootId,
        deriveTermIds(doc, rootId), termImports(doc, rootId, resolveDef))
    else doc

  // The compiled rules as an incremental parser. Its key is the
  // syntax plus the construct refs it emits (they pin the def's
  // version), so a session survives re-decoding an unchanged def; after
  // an edit to the def, only rules whose definitions changed rerun —
  // and a version bump re-pins kept content's term tags rather than
  // reparsing.
  override def textSyntax: Option[TextSyntax] =
    for {
      s <- languageDef.syntax
      (lexicon, parser) <- syntax
    } yield TextSyntax(parser, lexicon,
      (s.lexicon, s.rules, termTags),
      changedSince = previous => previous.key match {
        case (s.lexicon, beforeRules: Option[RuleSet] @unchecked,
              beforeTags: Map[String, NodeData] @unchecked) =>
          Some(SyntaxChange(
            syntaxChanges(SyntaxDef(s.lexicon, beforeRules), s),
            retagBy(beforeTags, termTag)))
        case _ => None
      })

  // The `edit` view's editor surface, when the impls name one (the
  // meta language's rule/gap marks, markdown's construct marks).
  override def editSurface = impls.editSurface

  override def parseInput(text: String): Option[DetachedNode[NodeData]] =
    inputs.parse(GapRetry())(text)

  override def commitInput(id: Int, text: String, sessions: InputSessions)
      : Option[(DetachedNode[NodeData], InputSessions)] =
    inputs.commit(id, text, sessions)

  override def rememberGaps(
      doc: Document[NodeData], retry: GapRetry): GapRetry =
    rememberInputGaps(doc, inputs.syntax, retry)

  private lazy val inputs =
    InputGrammar(languageDef.inputParser, impls, resolve)
}
