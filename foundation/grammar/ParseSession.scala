package opelan.foundation.grammar

import opelan.foundation.document.NodeData

// Incremental parsing: a parse that survives changes. A session holds
// the text, the grammar, and the memo of its last parse — which now
// mostly lives *on the result tree*: every node a memoized rule
// produced carries a ParseMark recording the rule, the lengths it
// consumed and examined, and the rules it called (Memo.scala). The
// column store (`side`) keeps only what the tree cannot represent —
// results whose trees never made it into the outcome: rejected
// alternatives and results re-spanned since stamping. A change keeps
// every result it cannot have affected and reparses, so only what
// the change touched runs again (*Incremental*).
//
//   val s0 = ParseSession.start(grammar, lexicon, "1 + 2")
//   val s1 = s0.edit(Edit(4, 5, "20"))           // "1 + 20"
//   val s2 = s1.changeRules(grammar2, Set("expr"))
//
// Reused results keep their subtrees: nothing is copied or shifted,
// so a node reused across reparses is the same object — and the
// identical content means commitContent keeps its document node id
// too. The guarantee, tested as a property: the result always equals
// a full parse of the current text with the current grammar.

// Replace the characters in [from, to) with `inserted`.
case class Edit(from: Int, to: Int, inserted: String)

case class ParseSession(
    grammar: Parser,
    lexicon: Lexicon,
    text: String,
    // Memo entries the result tree doesn't represent: one column per
    // position, holding the rejected and re-spanned results only.
    side: Vector[Map[String, MemoEntry]],
    result: POut,
    // Rule bodies the last parse ran — the work the change cost.
    evaluations: Int) {

  def tree: PTree = result.tree

  // Every rule the last parse applied — the marks on its result tree
  // plus whatever the side table still holds.
  def consulted: Set[String] =
    liveMarks(result.tree).map(_._3.rule).toSet ++
      side.iterator.flatMap(_.keySet)

  // Columns before the edit keep the entries that read only text
  // before it (`position + examined <= from`: parsers read only
  // forward). Entries inside the replaced range are dropped — the
  // text they read is gone. Entries at or after `to` move wholesale:
  // everything stored is relative, so nothing inside them changes
  // and no subtree is touched.
  def edit(e: Edit): ParseSession = {
    val from = e.from.max(0).min(text.length)
    val to = e.to.max(from).min(text.length)
    val delta = e.inserted.length - (to - from)
    val newText = text.substring(0, from) + e.inserted + text.substring(to)
    val moved = side.take(from).zipWithIndex.map { case (column, i) =>
      column.filter { case (_, entry) =>
        i + entry.result.examined <= from }
    } ++ Vector.fill(e.inserted.length)(Map.empty[String, MemoEntry]) ++
      side.drop(to)
    val seed = mergeMarks(moved, newText.length,
      liveMarks(result.tree).collect {
        case (pos, node, mark) if pos + mark.examined <= from =>
          markEntry(pos, node, mark)
        case (pos, node, mark) if pos >= to =>
          markEntry(pos + delta, node, mark)
      })
    ParseSession.run(grammar, lexicon, newText, seed)
  }

  // A language change: `changed` names the rules whose definitions
  // differ. Results of those rules, and of any rule that called one,
  // are dropped; everything else is reused under the new grammar —
  // `migrate` rewrites node data a kept result carries (e.g. term tags
  // re-pinning to a newer definition version).
  def changeRules(newGrammar: Parser, changed: Set[String],
      migrate: NodeData => NodeData = identity): ParseSession = {
    val keep = (pos: Int, mark: ParseMark) =>
      !changed(mark.rule) && !mark.calls.exists(changed)
    val migrated = mapTreeData(migrate)(result.tree)
    val moved = side.map(_.collect { case (name, entry)
        if !changed(name) && !entry.calls.exists(changed) =>
      name -> entry.copy(result =
        entry.result.copy(tree = mapTreeData(migrate)(entry.result.tree)))
    })
    val seed = mergeMarks(moved, text.length,
      liveMarks(migrated).collect { case (pos, node, mark)
          if keep(pos, mark) => markEntry(pos, node, mark) })
    ParseSession.run(newGrammar, lexicon, text, seed)
  }

  // A node's mark claims the entry its rule stored — rebuilt exactly
  // as MemoTable.stored wrote it (stored.from = -applied).
  private def markEntry(
      pos: Int, node: PTree, mark: ParseMark): (String, Int, MemoEntry) =
    (mark.rule, pos, MemoEntry(
      POut(mark.consumed, node.copy(from = -mark.applied), mark.ok,
        mark.examined),
      mark.calls))

  // Fold mark-derived entries into the side-table columns.
  private def mergeMarks(
      columns: Vector[Map[String, MemoEntry]],
      textLength: Int,
      entries: Iterable[(String, Int, MemoEntry)]
  ): Vector[Map[String, MemoEntry]] = {
    val merged = Array.tabulate(textLength + 1)(i =>
      columns.lift(i).map(scala.collection.mutable.Map.from)
        .getOrElse(scala.collection.mutable.Map.empty[String, MemoEntry]))
    entries.foreach { case (name, pos, entry) =>
      if (pos >= 0 && pos <= textLength) merged(pos)(name) = entry }
    merged.iterator.map(_.toMap).toVector
  }
}

object ParseSession {
  def start(grammar: Parser, lexicon: Lexicon, text: String): ParseSession =
    run(grammar, lexicon, text, Vector.fill(text.length + 1)(Map.empty))

  private def run(
      grammar: Parser, lexicon: Lexicon, text: String,
      seed: Vector[Map[String, MemoEntry]]): ParseSession = {
    val table = MemoTable(seed, marking = true)
    val result = grammar(In(text, lexicon, table), 0)
    val claimed = liveMarks(result.tree).map((pos, _, mark) =>
      (mark.rule, pos)).toSet
    val side = table.snapshot.zipWithIndex.map { case (column, i) =>
      column.filterNot { case (name, _) => claimed((name, i)) } }
    ParseSession(grammar, lexicon, text, side, result, table.evaluations)
  }
}

// (application position, node, mark) for every mark still valid in
// the tree — a mark is valid while its node spans what it recorded.
// Absolute positions are sums down the path (spansOf's convention).
private[grammar] def liveMarks(t: PTree): List[(Int, PTree, ParseMark)] = {
  val found = List.newBuilder[(Int, PTree, ParseMark)]
  def go(node: PTree, base: Int): Unit = {
    val abs = base + node.from
    node.marks.foreach(m =>
      if (m.span == node.length) found += ((abs + m.applied, node, m)))
    node.kids.foreach(k => go(k, abs))
  }
  go(t, 0)
  found.result()
}
