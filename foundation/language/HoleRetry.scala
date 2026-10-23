package opelan.foundation.language

import opelan.foundation.document._

// A hole's last parse attempt: the syntax it ran under, every rule the
// parse consulted, and the content it produced.
case class HoleAttempt(
    syntax: TextSyntax, calls: Set[String], content: DetachedNode[NodeData])

// Retrying holes when a language changes (*Incremental*): a hole's text
// is reparsed only if its last attempt consulted a rule the change
// touched. Attempts under the same syntax are reused outright; under a
// changed syntax that can describe itself, an attempt that never
// consulted a changed rule is reused too — migrated, since kept
// content may carry data the change moved (a term tag's version pin).
// Anything else — no attempt yet, or a change that can't be described —
// parses.
case class HoleRetry(attempts: Map[String, HoleAttempt] = Map.empty) {

  def parse(
      syntax: TextSyntax, text: String): (DetachedNode[NodeData], HoleRetry) =
    attempts.get(text) match {
      case Some(a) if a.syntax.key == syntax.key => (a.content, this)
      case Some(a) =>
        syntax.changedSince(a.syntax).filter(ch =>
          (a.calls & ch.rules).isEmpty) match {
          case Some(ch) =>
            val content = a.content.map(ch.migrate)
            (content, HoleRetry(attempts.updated(
              text, a.copy(syntax = syntax, content = content))))
          case None => fresh(syntax, text)
        }
      case None => fresh(syntax, text)
    }

  private def fresh(
      syntax: TextSyntax,
      text: String): (DetachedNode[NodeData], HoleRetry) = {
    val s = TextSession.start(syntax, text)
    val calls = s.session.memo.iterator.flatMap(_.keySet).toSet
    (s.content, HoleRetry(attempts.updated(
      text, HoleAttempt(syntax, calls, s.content))))
  }
}
