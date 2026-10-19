package opelan.foundation.grammar

import opelan.foundation.document._

// Combinators for infix structure — `precedenceTable` folds `chainLeft`
// over operator levels, the general shape behind infix-expression
// grammars. A precedence table is thus just one composition, not a
// special format feature.

// A left-associative operator level: atom (op atom)* where op is a
// token of `opKind` ("op" by convention) whose text is in the
// symbol → node-data table (the construct the operator builds, by ref);
// each step builds tag(l, r) spanning both operands. An operand that
// doesn't apply (an op or ")" or the end where a value was expected)
// still folds as its empty hole — then the level ends and the caller's
// `phrase`/`parens` handles the boundary.
def chainLeft(
    ops: Map[String, NodeData],
    opKind: String = "op")(atom: Parser): Parser =
  (in, pos) => {
    val first = atom(in, pos)
    if (!first.ok) first
    else {
      var acc = first.tree
      var cur = first.pos
      var seen = first.examined
      var more = true
      while (more) {
        val next = scanAt(in, cur)
        seen = seen.max(next.examined)
        next.token match {
          case Some(op) if op.kind == opKind && ops.contains(op.text) =>
            val r = atom(in, op.to)
            seen = seen.max(r.examined)
            acc = PTree(
              ops(op.text),
              in.input.substring(acc.from, r.tree.to),
              acc.from, r.tree.to,
              List(acc, r.tree))
            cur = r.pos
            more = r.ok
          case _ => more = false
        }
      }
      POut(cur, acc, ok = true, seen)
    }
  }

// The meta-combinator: fold the levels — TIGHTEST binding first — around
// the atom; each later level chains its ops over the level below, so the
// last level is the loosest (outermost) binding.
def precedenceTable(
    atom: Parser,
    levels: List[Map[String, NodeData]],
    opKind: String = "op"): Parser =
  levels.foldLeft(atom)((lower, ops) => chainLeft(ops, opKind)(lower))

// "(" inner ")" with recovery: a missing ")" closes the group at the end
// of input, and an inner that degenerated to a hole extends the hole's
// text over the parens ("(1 2)" stays "(1 2)", not "1 2").
def parens(inner: => Parser): Parser =
  (in, pos) => {
    val open = scanAt(in, pos)
    open.token match {
      case Some(t) if t.kind == "lpar" =>
        val r = phrase(inner, Set("rpar"))(in, t.to)
        val after = scanAt(in, r.pos)
        val seen = open.examined.max(r.examined).max(after.examined)
        val close = after.token.filter(_.kind == "rpar")
        val end = close.map(_.to).getOrElse(in.input.length)
        val endPos = close.map(_.to).getOrElse(r.pos)
        r.tree.data match {
          case NodeData.GapData(_) =>
            hole(in, t.from, end, endPos, ok = true, seen)
          case _ =>
            val text = in.input.substring(t.from, end)
            POut(endPos, r.tree.copy(text = text, from = t.from, to = end),
              ok = true, seen)
        }
      case _ => emptyHole(in, pos, open.examined)
    }
  }
