package opelan.foundation.language

import opelan.foundation.document._

// Projected cells' incremental parses, one per cell node, carried
// across commits (*Incremental*). Keyed by node id — commits keep the
// cell's id (commitContent) — so re-editing a cell reparses in
// proportion to the edit, and a language change between commits
// carries the session through `retext`. A session that went stale
// (the cell was edited some other way meanwhile) is still correct:
// the new text is just a larger edit against the session's own text.
case class CellSessions(sessions: Map[Int, TextSession] = Map.empty) {

  def parse(
      id: Int, syntax: TextSyntax,
      text: String): (DetachedNode[NodeData], CellSessions) = {
    val s = sessions.get(id)
      .fold(TextSession.start(syntax, text))(_.retext(syntax, text))
    (s.content, CellSessions(sessions.updated(id, s)))
  }

  // Keep only the sessions of cells that still exist.
  def retain(keep: Int => Boolean): CellSessions =
    CellSessions(sessions.filter((id, _) => keep(id)))
}
