package opelan.foundation.grammar

import opelan.foundation.document._

// Recovering parser combinators over characters — the Hazel-style answer
// to combinator parsing: parsers are *total* (they never fail or throw);
// `ok = false` means the production did not apply and the returned node
// is a hole covering what could not be placed. Recovery replaces
// backtracking.
//
// Positions are character offsets. Token-level combinators skip
// whitespace before what they match (syntactic sequencing); lexical
// rules never do. Every result reports `examined`: the exclusive bound
// of characters read to produce it (input.length + 1 when the end of
// input was observed) — what incremental reparsing will use to decide
// which results an edit invalidates.

// The parse input: the source text, the lexicon its tokens come from,
// and the memo table of this parse (fresh unless a session seeds it).
case class In(input: String, lexicon: Lexicon, memo: MemoTable = MemoTable())

// A parse-tree node: its node data, the source slice it came from, and
// its children. Positions are relative so a subtree never changes when
// text before it does: `from` is an offset from the parent's start, and
// `length` is the node's span. While a node stands alone during a parse
// (not yet adopted into a parent), its `from` is absolute — that is
// when combinators read it. `adopted` (Tree.scala) rebases a kid when a
// parent claims it.
case class PTree(
    data: NodeData,
    text: String,
    from: Int,
    length: Int,
    kids: List[PTree] = Nil) {
  // The end offset, in the same coordinate as `from`.
  def to: Int = from + length
}

// A parse result: where parsing stopped, the produced tree, whether the
// production genuinely applied, and how far it read.
case class POut(pos: Int, tree: PTree, ok: Boolean, examined: Int)

// A combinator: input + character position → result.
type Parser = (In, Int) => POut

// The zero-length hole produced where a production should have applied,
// placed after any whitespace.
private[grammar] def emptyHole(in: In, pos: Int, examined: Int): POut = {
  val at = skipSpace(in.input, pos)
  POut(pos, PTree(NodeData.GapData(""), "", at, 0), ok = false,
    examined.max(at + 1))
}

// A single token of `kind`, mapped to node data.
def tok(kind: String)(f: Token => NodeData): Parser =
  tokWhere(kind)(_ => true)(f)

// A token of `kind` satisfying `accept`.
def tokWhere(kind: String)(accept: Token => Boolean)(f: Token => NodeData): Parser =
  (in, pos) => {
    val scanned = scanAt(in, pos)
    scanned.token match {
      case Some(t) if t.kind == kind && accept(t) =>
        POut(t.to, PTree(f(t), t.text, t.from, t.text.length), ok = true,
          scanned.examined)
      case _ => emptyHole(in, pos, scanned.examined)
    }
  }

// A production that never applies — the parse-tree hole for a place
// where a grammar couldn't provide one.
def holeHere: Parser = (in, pos) => emptyHole(in, pos, pos)

// First alternative that applies; if none do, an empty hole.
def choice(ps: Parser*): Parser =
  (in, pos) => {
    var seen = pos
    ps.iterator.map { p =>
      val r = p(in, pos)
      seen = seen.max(r.examined)
      r
    }.find(_.ok).map(_.copy(examined = seen))
      .getOrElse(emptyHole(in, pos, seen))
  }

// Two productions in sequence, combined by `f`; if either fails the
// whole seq backtracks to the start.
def seq(a: Parser, b: Parser)(f: (PTree, PTree) => PTree): Parser =
  (in, pos) => {
    val ra = a(in, pos)
    if (!ra.ok) POut(pos, ra.tree, ok = false, ra.examined)
    else {
      val rb = b(in, ra.pos)
      val seen = ra.examined.max(rb.examined)
      if (!rb.ok) emptyHole(in, pos, seen)
      else POut(rb.pos, f(ra.tree, rb.tree), ok = true, seen)
    }
  }
