package opelan.foundation.grammar

import scala.collection.mutable

// Packrat memoization with dependency tracking — the mechanism behind
// incremental parsing (Dubroy & Warth, *Incremental Packrat Parsing*).
// Every named rule application is remembered by (rule, position) with
// its full result — failures included, since an edit can make a failed
// alternative succeed — and the set of rules it called, directly or not.
//
// The table is a column per input position; column `i` holds the
// results of rules applied at `i`. An entry's positions are relative
// to its column (`pos` is the consumed length, `examined` the examined
// length, the tree's `from` an offset from the column) — so when an
// edit moves a column, nothing inside its entries changes.

// A named rule's identity for memoization and change invalidation —
// `means:#N` for document-authored rules, the lexicon's name or a
// pattern key in code. A value class so memo names can't silently mix
// with the other strings a parse carries (token kinds, pattern keys).
case class RuleName(id: String) extends AnyVal

object RuleName:
  given Conversion[String, RuleName] = RuleName(_)

case class MemoEntry(result: ParseResult, calls: Set[RuleName])

// A memo entry that lives on the parse-tree node its rule produced.
// Marks plus the session's side table are the whole memo (DESIGN.md,
// *Incremental parsing*): the document never sees them — a mark dies with the
// parse that owns it. Everything is relative, so a mark survives
// relocation, adoption, and copies without adjustment:
//
//   appliedAt application position minus the node's own start
//             (0 for rules whose result starts where they applied)
//   consumed  result end minus the application position
//   examined  examined end minus the application position
//   span      the node's length at stamping — a re-spanned node can
//             no longer stand in for the result its mark recorded
//
// From a node at absolute position `A` the mark claims an entry
// keyed `(rule, A + appliedAt)`, reconstructible exactly:
// `stored(pos, examined, tree.from)` = `(consumed, examined, -appliedAt)`.
case class ParseMark(
    rule: RuleName, appliedAt: Int, consumed: Int, examined: Int,
    applied: Boolean, span: Int, calls: Set[RuleName])

// The memo table of one parse. Mutable only within that parse: seeded
// from a session's surviving columns, read back as an immutable
// snapshot when the parse ends (see ParseSession). `marking` stamps a
// fresh result's root with its ParseMark — sessions set it so the
// result tree carries the memo; one-off parses (gaps, `reparse`
// sub-parses) leave it off so no foreign marks leak in.
final class MemoTable(
    seed: Vector[Map[RuleName, MemoEntry]] = Vector.empty,
    marking: Boolean = false) {
  private val columns =
    mutable.ArrayBuffer.from(seed.map(m => mutable.Map.from(m)))
  private var callers = List.empty[mutable.Set[RuleName]]
  private var evaluated = 0

  def apply(
      name: RuleName, pos: Int, body: => ParseResult): ParseResult =
    column(pos).get(name) match {
      case Some(entry) =>
        noteCall(name, entry.calls)
        relocated(entry.result, pos)
      case None =>
        evaluated += 1
        val calls = mutable.Set.empty[RuleName]
        callers = calls :: callers
        val r = try body finally callers = callers.tail
        val marked =
          if (!marking) r
          else r.copy(tree = r.tree.copy(marks = ParseMark(
            name, pos - r.tree.from, r.position - pos, r.examined - pos,
            r.applied, r.tree.length, calls.toSet) :: r.tree.marks))
        column(pos)(name) = MemoEntry(stored(marked, pos), calls.toSet)
        noteCall(name, calls.toSet)
        marked
    }

  private def column(pos: Int): mutable.Map[RuleName, MemoEntry] = {
    while (columns.length <= pos) columns += mutable.Map.empty
    columns(pos)
  }

  // A live result, positions rebased for storage at column `base`.
  private def stored(r: ParseResult, base: Int): ParseResult = r.copy(
    position = r.position - base, examined = r.examined - base,
    tree = r.tree.copy(from = r.tree.from - base))

  // A stored result, relocated to column `base`. Shallow: the tree's
  // children are already relative, so only its own `from` is rebased.
  private def relocated(r: ParseResult, base: Int): ParseResult = r.copy(
    position = base + r.position, examined = base + r.examined,
    tree = r.tree.copy(from = base + r.tree.from))

  private def noteCall(name: RuleName, calls: Set[RuleName]): Unit =
    callers.headOption.foreach { c => c += name; c ++= calls }

  def snapshot: Vector[Map[RuleName, MemoEntry]] =
    columns.iterator.map(_.toMap).toVector
  // How many rule bodies this parse actually ran (memo misses).
  def evaluations: Int = evaluated
}

// A named rule application: memoized by name and position. The name is
// the rule's identity — a session invalidates by it when rules change.
def rule(name: RuleName)(body: => Parser): Parser = {
  lazy val parser = body
  (in, pos) => in.memo(name, pos, parser(in, pos))
}
