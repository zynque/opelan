package opelan.foundation.grammar

import opelan.foundation.document._

// Combinators for infix structure — `precedenceTable` folds `chainLeft`
// over operator levels, the general shape behind infix-expression
// grammars. A precedence table is thus just one composition, not a
// special format feature.

// A left-associative operator level: atom (op atom)* where op is a
// token of `opKind` ("op" by convention) whose text is in the
// symbol → tag table; each step builds tag(l, r) spanning both
// operands. An operand that doesn't apply (an op or ")" or EOF where a
// value was expected) still folds as its empty hole — then the level
// ends and the caller's `phrase`/`parens` handles the boundary.
def chainLeft(
    ops: Map[String, String],
    opKind: String = "op")(atom: Parser): Parser =
  (in, pos) => {
    val first = atom(in, pos)
    if (!first.ok) first
    else {
      var acc = first.tree
      var cur = first.pos
      var more = true
      while (more)
        in.toks.lift(cur) match {
          case Some(op) if op.kind == opKind && ops.contains(op.text) =>
            val r = atom(in, cur + 1)
            acc = PTree(
              NodeData.StringData(ops(op.text)),
              in.input.substring(acc.from, r.tree.to),
              acc.from, r.tree.to,
              List(acc, r.tree))
            cur = r.pos
            more = r.ok
          case _ => more = false
        }
      POut(cur, acc, ok = true)
    }
  }

// The meta-combinator: fold the levels — TIGHTEST binding first — around
// the atom; each later level chains its ops over the level below, so the
// last level is the loosest (outermost) binding.
def precedenceTable(
    atom: Parser,
    levels: List[Map[String, String]],
    opKind: String = "op"): Parser =
  levels.foldLeft(atom)((lower, ops) => chainLeft(ops, opKind)(lower))

// "(" inner ")" with recovery: a missing ")" closes the group at EOF,
// and an inner that degenerated to a hole extends the hole's text over
// the parens ("(1 2)" stays "(1 2)", not "1 2").
def parens(inner: => Parser): Parser =
  (in, pos) => in.toks.lift(pos) match {
    case Some(t) if t.kind == "lpar" =>
      val r = phrase(inner, Set("rpar"))(in, pos + 1)
      val close = in.toks.lift(r.pos).filter(_.kind == "rpar")
      val end = close.map(_.to).getOrElse(in.input.length)
      val endPos = close.map(_ => r.pos + 1).getOrElse(r.pos)
      val text = in.input.substring(t.from, end)
      r.tree.data match {
        case NodeData.GapData(_) =>
          POut(endPos, PTree(NodeData.GapData(text), text, t.from, end), ok = true)
        case _ =>
          POut(endPos, r.tree.copy(text = text, from = t.from, to = end), ok = true)
      }
    case _ =>
      val at = in.toks.lift(pos).map(_.from).getOrElse(in.input.length)
      POut(pos, PTree(NodeData.GapData(""), "", at, at), ok = false)
  }

// The top-level cleanup: if tokens remain that `p` did not place — and
// the next token isn't a stop kind (")" inside a group) — the whole
// level merges into one hole covering its text.
def phrase(p: Parser, stops: Set[String]): Parser =
  (in, pos) => {
    val r = p(in, pos)
    val atStop = in.toks.lift(r.pos).exists(t => stops(t.kind))
    if (r.pos >= in.toks.length || atStop) r
    else {
      var end = r.pos
      while (end < in.toks.length && !stops(in.toks(end).kind)) end += 1
      val to = in.toks(end - 1).to
      val text = in.input.substring(r.tree.from, to)
      POut(end, PTree(NodeData.GapData(text), text, r.tree.from, to), r.ok)
    }
  }
