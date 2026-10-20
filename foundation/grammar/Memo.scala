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

case class MemoEntry(result: POut, calls: Set[String])

// The memo table of one parse. Mutable only within that parse: seeded
// from a session's surviving columns, read back as an immutable
// snapshot when the parse ends (see ParseSession).
final class MemoTable(seed: Vector[Map[String, MemoEntry]] = Vector.empty) {
  private val columns =
    mutable.ArrayBuffer.from(seed.map(m => mutable.Map.from(m)))
  private var callers = List.empty[mutable.Set[String]]
  private var evaluated = 0

  def apply(name: String, pos: Int, body: => POut): POut =
    column(pos).get(name) match {
      case Some(entry) =>
        noteCall(name, entry.calls)
        relocated(entry.result, pos)
      case None =>
        evaluated += 1
        val calls = mutable.Set.empty[String]
        callers = calls :: callers
        val r = try body finally callers = callers.tail
        column(pos)(name) = MemoEntry(stored(r, pos), calls.toSet)
        noteCall(name, calls.toSet)
        r
    }

  private def column(pos: Int): mutable.Map[String, MemoEntry] = {
    while (columns.length <= pos) columns += mutable.Map.empty
    columns(pos)
  }

  // A live result, positions rebased for storage at column `base`.
  private def stored(r: POut, base: Int): POut = r.copy(
    pos = r.pos - base, examined = r.examined - base,
    tree = r.tree.copy(from = r.tree.from - base))

  // A stored result, relocated to column `base`. Shallow: the tree's
  // children are already relative, so only its own `from` is rebased.
  private def relocated(r: POut, base: Int): POut = r.copy(
    pos = base + r.pos, examined = base + r.examined,
    tree = r.tree.copy(from = base + r.tree.from))

  private def noteCall(name: String, calls: Set[String]): Unit =
    callers.headOption.foreach { c => c += name; c ++= calls }

  def snapshot: Vector[Map[String, MemoEntry]] =
    columns.iterator.map(_.toMap).toVector
  // How many rule bodies this parse actually ran (memo misses).
  def evaluations: Int = evaluated
}

// A named rule application: memoized by name and position. The name is
// the rule's identity — a session invalidates by it when rules change.
def rule(name: String)(body: => Parser): Parser = {
  lazy val parser = body
  (in, pos) => in.memo(name, pos, parser(in, pos))
}
