package opelan.foundation.grammar

import opelan.foundation.document.NodeData

// Incremental parsing: a parse that survives changes. A session holds
// the text, the grammar, and the memo of its last parse — one column
// per position, each holding the results of rules applied there,
// stored relative to the column. A change keeps every result it
// cannot have affected and reparses, so only what the change touched
// runs again (*Incremental*).
//
//   val s0 = ParseSession.start(grammar, lexicon, "1 + 2")
//   val s1 = s0.edit(Edit(4, 5, "20"))           // "1 + 20"
//   val s2 = s1.changeRules(grammar2, Set("expr"))
//
// Reused results keep their subtrees: nothing is copied or shifted,
// so a node reused across reparses is the same object — its identity,
// and eventually its document node id, survives. The guarantee, tested
// as a property: the result always equals a full parse of the current
// text with the current grammar.

// Replace the characters in [from, to) with `inserted`.
case class Edit(from: Int, to: Int, inserted: String)

case class ParseSession(
    grammar: Parser,
    lexicon: Lexicon,
    text: String,
    // One memo column per position, 0 through text.length.
    memo: Vector[Map[String, MemoEntry]],
    result: POut,
    // Rule bodies the last parse ran — the work the change cost.
    evaluations: Int) {

  def tree: PTree = result.tree

  // Columns before the edit keep the entries that read only text
  // before it (`column + examined length <= from`: parsers read only
  // forward). Columns inside the replaced range are dropped — the text
  // they started on is gone. Columns at or after `to` move wholesale:
  // the entries are relative, so nothing inside them changes and no
  // subtree is touched.
  def edit(e: Edit): ParseSession = {
    val from = e.from.max(0).min(text.length)
    val to = e.to.max(from).min(text.length)
    val kept =
      memo.take(from).zipWithIndex.map { case (column, i) =>
        column.filter { case (_, entry) =>
          i + entry.result.examined <= from }
      } ++
      Vector.fill(e.inserted.length)(Map.empty[String, MemoEntry]) ++
      memo.drop(to)
    val newText = text.substring(0, from) + e.inserted + text.substring(to)
    ParseSession.run(grammar, lexicon, newText, kept)
  }

  // A language change: `changed` names the rules whose definitions
  // differ. Results of those rules, and of any rule that called one,
  // are dropped; everything else is reused under the new grammar —
  // `migrate` rewrites node data a kept result carries (e.g. kind tags
  // re-pinning to a newer definition version).
  def changeRules(newGrammar: Parser, changed: Set[String],
      migrate: NodeData => NodeData = identity): ParseSession = {
    val kept = memo.map(_.collect { case (name, entry)
        if !changed(name) && !entry.calls.exists(changed) =>
      name -> entry.copy(result =
        entry.result.copy(tree = mapTreeData(migrate)(entry.result.tree)))
    })
    ParseSession.run(newGrammar, lexicon, text, kept)
  }
}

object ParseSession {
  def start(grammar: Parser, lexicon: Lexicon, text: String): ParseSession =
    run(grammar, lexicon, text, Vector.fill(text.length + 1)(Map.empty))

  private def run(
      grammar: Parser, lexicon: Lexicon, text: String,
      memo: Vector[Map[String, MemoEntry]]): ParseSession = {
    val table = MemoTable(memo)
    val result = grammar(In(text, lexicon, table), 0)
    ParseSession(grammar, lexicon, text, table.snapshot, result,
      table.evaluations)
  }
}
