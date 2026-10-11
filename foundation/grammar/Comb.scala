package opelan.foundation.grammar

import opelan.foundation.document._

// Recovering parser combinators over a token stream — the Hazel-style
// answer to combinator parsing: parsers are *total* (they never fail or
// throw); `ok = false` means the production did not apply and the
// returned node is a hole covering what could not be placed. Recovery
// replaces backtracking.

// The parse input: the source text plus its tokens.
case class In(input: String, toks: Vector[GTok])

// A parse-tree node: its node data, the source slice it came from, and
// its children.
case class PTree(
    data: NodeData,
    text: String,
    from: Int,
    to: Int,
    kids: List[PTree] = Nil)

// A parse result: where parsing stopped, the produced tree, and whether
// the production genuinely applied.
case class POut(pos: Int, tree: PTree, ok: Boolean)

// A combinator: input + token position → result.
type Parser = (In, Int) => POut

// The zero-length hole produced where a production should have applied.
private def emptyHole(in: In, pos: Int): POut = {
  val at = in.toks.lift(pos).map(_.from).getOrElse(in.input.length)
  POut(pos, PTree(NodeData.GapData(""), "", at, at), ok = false)
}

// A single token of `kind`, mapped to node data.
def tok(kind: String)(f: GTok => NodeData): Parser =
  tokWhere(kind)(_ => true)(f)

// A token of `kind` satisfying `p`.
def tokWhere(kind: String)(p: GTok => Boolean)(f: GTok => NodeData): Parser =
  (in, pos) => in.toks.lift(pos) match {
    case Some(t) if t.kind == kind && p(t) =>
      POut(pos + 1, PTree(f(t), t.text, t.from, t.to), ok = true)
    case _ => emptyHole(in, pos)
  }

// First alternative that applies; if none do, an empty hole.
def choice(ps: Parser*): Parser =
  (in, pos) =>
    ps.iterator.map(p => p(in, pos)).find(_.ok)
      .getOrElse(emptyHole(in, pos))

// Two productions in sequence, combined by `f`; if either fails the
// whole seq backtracks to the start.
def seq(a: Parser, b: Parser)(f: (PTree, PTree) => PTree): Parser =
  (in, pos) => {
    val ra = a(in, pos)
    if (!ra.ok) POut(pos, ra.tree, ok = false)
    else {
      val rb = b(in, ra.pos)
      if (!rb.ok) emptyHole(in, pos)
      else POut(rb.pos, f(ra.tree, rb.tree), ok = true)
    }
  }

// The fallback hole: applies only when the token at `pos` can start
// junk (its kind is in `start` — a boundary like ")" never begins a
// hole), then covers the maximal run extended by kinds in `run`. At EOF
// it produces the empty hole — "operand expected" — and still applies.
def junk(start: Set[String], run: Set[String]): Parser =
  (in, pos) => in.toks.lift(pos) match {
    case None => POut(pos, emptyHole(in, pos).tree, ok = true)
    case Some(t0) if start(t0.kind) =>
      var end = pos + 1
      while (in.toks.lift(end).exists(t => run(t.kind))) end += 1
      val to = in.toks(end - 1).to
      val text = in.input.substring(t0.from, to)
      POut(end, PTree(NodeData.GapData(text), text, t0.from, to), ok = true)
    case _ => emptyHole(in, pos)
  }

// Strip the spans: a PTree becomes ordinary detached document data.
def toDetached(t: PTree): DetachedNode[NodeData] =
  DetachedNode(t.data, t.kids.map(toDetached))

// Path (child indexes from the root) → source span, for every node —
// the text-edit → node mapping substrate.
def spansOf(t: PTree): Map[List[Int], Span] = {
  def go(t: PTree, path: List[Int]): List[(List[Int], Span)] =
    (path -> Span(t.from, t.to)) ::
      t.kids.zipWithIndex.toList.flatMap { case (k, i) => go(k, path :+ i) }
  go(t, Nil).toMap
}
