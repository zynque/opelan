package opelan.foundation.grammar

import opelan.foundation.document._

// Recovering parser combinators over characters — the Hazel-style answer
// to combinator parsing: parsers are *total* (they never fail or throw);
// `applied = false` means the production did not apply and the returned node
// is a gap covering what could not be placed. Recovery replaces
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
case class ParseInput(input: String, lexicon: Lexicon, memo: MemoTable = MemoTable())

// A parse-tree node: its node data, the source slice it came from, and
// its children. Positions are relative so a subtree never changes when
// text before it does: `from` is an offset from the parent's start, and
// `length` is the node's span. While a node stands alone during a parse
// (not yet adopted into a parent), its `from` is absolute — that is
// when combinators read it. `adopted` (Tree.scala) rebases a child when a
// parent claims it.
case class ParseTree(
    data: NodeData,
    text: String,
    from: Int,
    length: Int,
    children: List[ParseTree] = Nil,
    // Parse bookkeeping stamped by the memoized rule applications that
    // produced this node — the memo entry, living on the tree (Memo.scala).
    marks: List[ParseMark] = Nil) {
  // The end offset, in the same coordinate as `from`.
  def to: Int = from + length
}

// A parse result: where parsing stopped, the produced tree, whether the
// production genuinely applied, and how far it read.
case class ParseResult(
    position: Int, tree: ParseTree, applied: Boolean, examined: Int)

// A combinator: input + character position → result.
type Parser = (ParseInput, Int) => ParseResult

// The zero-length gap produced where a production should have applied,
// placed after any whitespace.
private[grammar] def emptyGap(in: ParseInput, pos: Int, examined: Int): ParseResult = {
  val at = skipSpace(in.input, pos)
  ParseResult(pos, ParseTree(NodeData.GapData(""), "", at, 0), applied = false,
    examined.max(at + 1))
}

// A single token of `kind`, mapped to node data.
def token(kind: String)(f: Token => NodeData): Parser =
  tokenWhere(kind)(_ => true)(f)

// A token of `kind` satisfying `accept`.
def tokenWhere(kind: String)(accept: Token => Boolean)(f: Token => NodeData): Parser =
  (in, pos) => {
    val scanned = scanAt(in, pos)
    scanned.token match {
      case Some(t) if t.kind == kind && accept(t) =>
        ParseResult(t.to, ParseTree(f(t), t.text, t.from, t.text.length), applied = true,
          scanned.examined)
      case _ => emptyGap(in, pos, scanned.examined)
    }
  }

// A production that never applies — the parse-tree gap for a place
// where a grammar couldn't provide one.
def gapHere: Parser = (in, pos) => emptyGap(in, pos, pos)

// First alternative that applies; if none do, an empty gap.
def choice(ps: Parser*): Parser =
  (in, pos) => {
    var seen = pos
    ps.iterator.map { p =>
      val r = p(in, pos)
      seen = seen.max(r.examined)
      r
    }.find(_.applied).map(_.copy(examined = seen))
      .getOrElse(emptyGap(in, pos, seen))
  }

// Two productions in sequence, combined by `f`; if either fails the
// whole sequence backtracks to the start.
def andThen(a: Parser, b: Parser)(f: (ParseTree, ParseTree) => ParseTree): Parser =
  (in, pos) => {
    val ra = a(in, pos)
    if (!ra.applied) ParseResult(pos, ra.tree, applied = false, ra.examined)
    else {
      val rb = b(in, ra.position)
      val seen = ra.examined.max(rb.examined)
      if (!rb.applied) emptyGap(in, pos, seen)
      else ParseResult(rb.position, f(ra.tree, rb.tree), applied = true, seen)
    }
  }
