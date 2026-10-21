package opelan.foundation.language

import opelan.foundation.document._

// Projected inputs' incremental parses, one per input node, carried
// across commits (*Incremental*). Keyed by node id — commits keep the
// input's id (commitContent) — so re-editing an input reparses in
// proportion to the edit, and a language change between commits
// carries the session through `retext`. A session that went stale
// (the input was edited some other way meanwhile) is still correct:
// the new text is just a larger edit against the session's own text.
case class InputSessions(sessions: Map[Int, TextSession] = Map.empty) {

  def parse(
      id: Int, syntax: TextSyntax,
      text: String): (DetachedNode[NodeData], InputSessions) = {
    val s = sessions.get(id)
      .fold(TextSession.start(syntax, text))(_.retext(syntax, text))
    (s.content, InputSessions(sessions.updated(id, s)))
  }

  // Keep only the sessions of inputs that still exist.
  def retain(keep: Int => Boolean): InputSessions =
    InputSessions(sessions.filter((id, _) => keep(id)))
}
