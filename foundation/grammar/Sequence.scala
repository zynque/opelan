package opelan.foundation.grammar

import opelan.foundation.document._

// Structural combinators: sequences, repetition, options, and lookahead.
// Unlike the recovering productions they report failure plainly
// (`ok = false`) and leave recovery to the grammar using them.

// Succeeds without consuming anything.
def nothing(data: NodeData = NodeData.GapData("")): Parser =
  (_, pos) => POut(pos, PTree(data, "", pos, pos), ok = true, pos)

// All of `parts` in order, as one node whose children are their trees.
def sequence(data: NodeData)(parts: Parser*): Parser =
  (in, pos) => {
    var cur = pos
    var seen = pos
    val kids = List.newBuilder[PTree]
    val ok = parts.forall { p =>
      val r = p(in, cur)
      seen = seen.max(r.examined)
      if (r.ok) { kids += r.tree; cur = r.pos }
      r.ok
    }
    if (ok) POut(cur, node(in, data, kids.result(), pos), ok = true, seen)
    else emptyHole(in, pos, seen)
  }

// Zero or more `p`, as one node; stops when `p` fails or stops consuming.
def many(data: NodeData)(p: Parser): Parser =
  (in, pos) => {
    var cur = pos
    var seen = pos
    val kids = List.newBuilder[PTree]
    var more = true
    while (more) {
      val r = p(in, cur)
      seen = seen.max(r.examined)
      if (r.ok && r.pos > cur) { kids += r.tree; cur = r.pos }
      else more = false
    }
    POut(cur, node(in, data, kids.result(), pos), ok = true, seen)
  }

// `p`, or nothing.
def optional(p: Parser): Parser = choice(p, nothing())

// Lookahead: succeeds without consuming when `p` would apply.
def followedBy(p: Parser): Parser = (in, pos) => {
  val r = p(in, pos)
  POut(pos, PTree(NodeData.GapData(""), "", pos, pos), r.ok, r.examined)
}

// Negative lookahead: succeeds without consuming when `p` would not apply.
def notFollowedBy(p: Parser): Parser = (in, pos) => {
  val r = followedBy(p)(in, pos)
  r.copy(ok = !r.ok)
}

// Only whitespace remains.
def endOfInput: Parser = (in, pos) => {
  val start = skipSpace(in.input, pos)
  val ok = start >= in.input.length
  POut(pos, PTree(NodeData.GapData(""), "", start, start), ok,
    in.input.length.min(start) + 1)
}

// Whatever token starts here, tagged with its kind.
def anyToken: Parser = (in, pos) => {
  val scanned = scanAt(in, pos)
  scanned.token match {
    case Some(t) => POut(t.to,
      PTree(NodeData.StringData(t.kind), t.text, t.from, t.to), ok = true,
      scanned.examined)
    case None => emptyHole(in, pos, scanned.examined)
  }
}

// A node spanning its children (or the empty range at `pos`).
private def node(
    in: In, data: NodeData, kids: List[PTree], pos: Int): PTree = {
  val from = kids.headOption.map(_.from).getOrElse(pos)
  val to = kids.lastOption.map(_.to).getOrElse(pos)
  PTree(data, in.input.substring(from, to), from, to, kids)
}
