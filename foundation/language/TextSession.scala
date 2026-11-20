package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.grammar.{
  Edit => TextEdit, Grammar, ParseSession, ParseTree, RuleName, SpannedTree,
  spanned, toDetached}

// What changed between two syntaxes: the rules whose definitions
// differ (callers keep every result those rules didn't produce), and a
// migration for content a kept result carries — e.g. term tags pinning
// an older definition version re-pin rather than reparse. `migrate`
// maps node data; results that hold other data are untouched.
case class SyntaxChange(
    rules: Set[RuleName],
    migrate: NodeData => NodeData = identity)

// A language's text syntax as an incremental parser: the grammar — its
// parser paired with the lexicon it tokenizes through — a `key`
// identifying both (equal keys parse identically), and how a parse tree
// becomes content. `changedSince` describes the change from an earlier
// syntax when the language can tell, and returns None when it can't,
// which restarts the parse.
case class TextSyntax(
    grammar: Grammar,
    key: Any,
    changedSince: TextSyntax => Option[SyntaxChange] = _ => None,
    toContent: ParseTree => DetachedNode[NodeData] = toDetached,
    // The same parse reporting each node's span, when `toContent`'s
    // output is the tree's own shape — a restructuring `toContent`
    // can't report spans aligned to content paths and returns None.
    // The text surface's provenance hints commits with it.
    toSpanned: ParseTree => Option[SpannedTree[NodeData]] =
      t => Some(spanned(t)))

// A text surface's parse carried across edits (*Incremental*). Each new
// text is applied as one edit against the session's own text, so a
// session that missed changes (the text was re-derived meanwhile) is
// still correct — the edit is just larger. The result always equals a
// full parse of the current text with the current syntax.
case class TextSession(syntax: TextSyntax, session: ParseSession) {

  def content: DetachedNode[NodeData] = syntax.toContent(session.tree)

  // `content` with spans, when the syntax's content is the tree's own
  // shape — the commit's provenance hints.
  def spannedContent: Option[SpannedTree[NodeData]] =
    syntax.toSpanned(session.tree)

  // Rule bodies the last parse ran — the work the edit cost.
  def evaluations: Int = session.evaluations

  def retext(next: TextSyntax, text: String): TextSession = {
    val carried =
      if (next.key == syntax.key) Some(session)
      else next.changedSince(syntax).map(ch =>
        session.changeRules(next.grammar, ch.rules, ch.migrate))
    carried match {
      case Some(s) => TextSession(next, s.edit(textEdit(s.text, text)))
      case None    => TextSession.start(next, text)
    }
  }
}

object TextSession {
  def start(syntax: TextSyntax, text: String): TextSession =
    TextSession(syntax, ParseSession.start(syntax.grammar, text))
}

// The single edit turning `before` into `after`: the common prefix and
// suffix are kept, the middle replaced.
def textEdit(before: String, after: String): TextEdit = {
  val prefix = before.lazyZip(after).takeWhile(_ == _).size
  val room = before.length.min(after.length) - prefix
  val suffix = (0 until room).takeWhile(i =>
    before(before.length - 1 - i) == after(after.length - 1 - i)).length
  TextEdit(prefix, before.length - suffix,
    after.substring(prefix, after.length - suffix))
}
