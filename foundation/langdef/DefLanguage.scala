package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.grammar._
import opelan.foundation.langdef.doc._
import opelan.foundation.langdef.syntax._
import opelan.foundation.means.RuleSet
import opelan.foundation.means.engine.{
  compileRules, evalView, lexiconSignature, printViewWith,
  rephraseRuleSet, leafView, withLiterals}
import opelan.foundation.langdef.view.{defSummary, defView}
import opelan.foundation.language.{
  EditSurface, GapRetry, InputSessions, Language, SyntaxChange,
  TextSyntax, grammarMarks}
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

  // lang: language's own surfaces derive like every language's — the
  // meta doc's stored views section is ignored, so a store seeded
  // before the rename (still saying `def`/`print`/`summary`) gets the
  // current menu regardless.
  private def isDocLanguage =
    languageDef.typeRef.documentUrl == langDefTypeRef.documentUrl &&
      languageDef.typeRef.nodeId == langDefTypeRef.nodeId

  // A def whose document is a single expression tree — rules and no
  // `surface` production — gets the standard derived pair: `print`
  // (rule-derived printing) and `eval` (rule-derived evaluation).
  // A surface-rooted document (markdown's `blocks`) doesn't read as
  // one expression, so the pair doesn't derive for it.
  private lazy val expressionDialect: Boolean =
    languageDef.syntax.exists(s =>
      s.rules.isDefined && s.surface.isEmpty)

  private lazy val derivedViews: List[String] =
    List(Option.when(syntax.isDefined)("edit"),
      Option.when(expressionDialect)("print"),
      Option.when(expressionDialect)("eval")).flatten

  // The menu derives from the def: `edit` wherever the document has a
  // text surface, `print`/`eval` for an expression dialect, then the
  // declared view entries minus the names the derived set covers —
  // a declared binding still outranks its derived default at render.
  def views =
    if (isDocLanguage) List("edit", "eval")
    else derivedViews ++
      languageDef.views.map(_.name).filterNot(derivedViews.contains)

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

  private lazy val syntax: Option[Grammar] =
    for {
      s <- languageDef.syntax
      lexicon <- lexiconFor(impls, s.lexicon)
    } yield s.rules match {
      // Rules compile over the lexicon's recovery as their operand
      // atom, under the lexicon's name — the `named`/`use` shape a
      // hand-written grammar memoizes by. `phrase` supplies the
      // to-EOF recovery.
      case Some(rules) =>
        // The patterns display the literal tokens — `a // b`'s `//`
        // tokenizes as soon as its rule exists (LiteralLexicon).
        val tokens = withLiterals(rules, lexicon)
        val atom = unrecognized(tokens.gapStart, tokens.gapRun)
        val lexicals = lexicalTerms(impls)
        // Quoted-gap meanings — phrases the set couldn't yet read —
        // retry at interpretation and heal as their rules arrive.
        val rephrased =
          rephraseRuleSet(rules, atom, termTag, s.lexicon, tokens, lexicals)
        // The document's root: the `where` production the def names as
        // its surface — `blocks` makes a whole document one parse —
        // else the rules' `self`.
        val start = s.surface.flatMap(lexicals.get).flatMap(_.parser)
          .getOrElse(compileRules(
            rephrased, atom, termTag, s.lexicon, lexicals))
        Grammar(phrase(start, Set.empty), tokens)
      // A rule-less syntax roots at its surface production alone —
      // `lang:`'s `document` outline — the whole input a gap without
      // one.
      case None => Grammar(
        phrase(s.surface.flatMap(lexicalTerms(impls).get)
          .flatMap(_.parser).getOrElse(restGap), Set.empty), lexicon)
    }

  def render(view: String, doc: Document[NodeData])
      : Either[String, DetachedNode[NodeData]] =
    renderWith(view, doc, GapRetry())

  override def renderWith(
      view: String, doc: Document[NodeData], retry: GapRetry,
      docUrl: Option[String] = None)
      : Either[String, DetachedNode[NodeData]] = {
    lazy val ctx = viewCtx(retry, docUrl)
    if (isDocLanguage) view match {
      // "print" isn't a listed view — it's the lang: doc's text
      // surface (docText): the content as an outline of `means` lines.
      case "print" => contentId(doc)
        .toRight(s"$name: typed document has no content")
        .map(id => ViewTree.block(defDocLines(
          doc, docUrl.getOrElse(""), id, resolveDef).map(ViewTree.txt)*))
      // The meta language's bootstrap pair: the decoded definition
      // laid out readably, and the summary standing in for `eval`
      // until examples land.
      case "edit" => contentId(doc).toRight(noContent)
        .map(id => defView(doc, id, ctx))
      case "eval" => contentId(doc).toRight(noContent)
        .map(id => defSummary(doc, id, ctx))
      case _ => Left(s"$name has no '$view' view")
    }
    else languageDef.views.find(_.name == view) match {
      case Some(vb) => impls.views.get(vb.builtin) match {
        case None => Left(s"$name: unknown builtin view '${vb.builtin}'")
        case Some(b) => runBuiltin(b, vb.settings, doc, retry, docUrl)
      }
      case None => view match {
        // The `edit` tile is the document's own text surface — the
        // same render `raw text` showed — while the pane edits through
        // `editSurface`'s marked editor.
        case "edit" if syntax.isDefined =>
          renderWith("print", doc, retry, docUrl).map(t =>
            ViewTree.block(ViewTree.flatten(t).split("\n", -1)
              .map(ViewTree.txt)*))
        case "print" if expressionDialect => contentId(doc)
          .toRight(noContent).map(id =>
            printViewWith(reading.rules, reading.tag, leafView,
              termName)(doc, id))
        case "eval" if expressionDialect => contentId(doc)
          .toRight(noContent).map(id =>
            evalView(reading.semantics, reading.terms, termName)(
              doc, id))
        // An unlisted builtin is still renderable by its own name —
        // `print` renders a language's text surface (docText) without
        // claiming a slot in the view menu.
        case _ => impls.views.get(view) match {
          case Some(b) => runBuiltin(b, Map.empty, doc, retry, docUrl)
          case None    => Left(s"$name has no '$view' view")
        }
      }
    }
  }

  private def noContent = s"$name: typed document has no content"

  private def viewCtx(retry: GapRetry, docUrl: Option[String]) =
    ViewContext(Map.empty, inputs.parse(retry), interpretDef, docUrl,
      termName, viewDialect)

  private def runBuiltin(
      b: BuiltinView, settings: Map[String, String],
      doc: Document[NodeData], retry: GapRetry, docUrl: Option[String])
      : Either[String, DetachedNode[NodeData]] =
    contentId(doc).toRight(noContent).map(id => b(doc, id,
      viewCtx(retry, docUrl).copy(settings = settings)))

  private lazy val viewDialect: Option[LanguageDef] =
    if (languageDef.syntax.flatMap(_.rules).isDefined) Some(languageDef)
    else inputs.dialect

  // The def's own rules and term anchors — `print` and `eval` derive
  // from them (Some whenever `expressionDialect` holds).
  private lazy val reading: RuleReading =
    ruleReading(languageDef).get

  // Total like every recovering grammar: Some whenever a syntax exists —
  // and always for the meta language, whose text form is the format in
  // doc/DefDocText.scala rather than a rule set.
  override def parse(text: String): Option[DetachedNode[NodeData]] =
    parseSpanned(text).map(unspanned)

  // `parse` with each node's span on the node — a text edit carries
  // node identity through the surface
  // (foundation/language/TextProvenance). Every language reports
  // spans the same way: the grammar positions them.
  override def parseSpanned(text: String): Option[SpannedTree[NodeData]] =
    syntax.map(g => spanned(g(text).tree))

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
  // unless the edit moved the derived literals (a changed tokenization
  // can have mis-scanned anything, so the session restarts) — and a
  // version bump re-pins kept content's term tags rather than
  // reparsing.
  override def textSyntax: Option[TextSyntax] =
    for {
      s <- languageDef.syntax
      grammar <- syntax
    } yield TextSyntax(grammar,
      (s.lexicon, s.rules, termTags),
      changedSince = previous => previous.key match {
        // Kept results are honest only while tokenization is unchanged:
        // literals derive from the rules, so a differing signature —
        // `a // b` added — can make any kept result wrong. Restart.
        case (s.lexicon, beforeRules: Option[RuleSet] @unchecked,
              beforeTags: Map[String, NodeData] @unchecked)
            if beforeRules.map(lexiconSignature) ==
               s.rules.map(lexiconSignature) =>
          Some(SyntaxChange(
            syntaxChanges(s.copy(rules = beforeRules), s),
            retagBy(beforeTags, termTag)))
        case _ => None
      })

  // The `edit` view's editor surface: the impls' marks when it names
  // one (the meta language's rule/gap marks, markdown's construct
  // marks), else marks derived from the compiled grammar — leaf
  // payloads, gaps, unmatched runs.
  override def editSurface = impls.editSurface.orElse(
    syntax.map(g => EditSurface(grammarMarks(g))))

  override def parseInput(text: String): Option[DetachedNode[NodeData]] =
    inputs.parse(GapRetry())(text)

  override def commitInput(id: Int, text: String, sessions: InputSessions)
      : Option[(DetachedNode[NodeData], InputSessions)] =
    inputs.commit(id, text, sessions)

  override def commitAction(
      id: Int, command: String, doc: Document[NodeData])
      : Option[Document[NodeData]] =
    impls.action.flatMap(_(doc, id, command))

  override def rememberGaps(
      doc: Document[NodeData], retry: GapRetry): GapRetry =
    rememberInputGaps(doc, inputs.syntax, retry)

  private lazy val inputs =
    InputGrammar(languageDef.inputParser, impls, resolve)
}
