package opelan.foundation.grammar

import opelan.foundation.document._

// Structural combinators: sequences, repetition, options, and lookahead.
// Unlike the recovering productions they report failure plainly
// (`ok = false`) and leave recovery to the grammar using them.

// Succeeds without consuming anything.
def nothing(data: NodeData = NodeData.GapData("")): Parser =
  (_, pos) => ParseResult(pos, ParseTree(data, "", pos, 0), ok = true, pos)

// All of `parts` in order, as one node whose children are their trees.
def sequence(data: NodeData)(parts: Parser*): Parser =
  (in, pos) => {
    var cur = pos
    var seen = pos
    val children = List.newBuilder[ParseTree]
    val ok = parts.forall { p =>
      val r = p(in, cur)
      seen = seen.max(r.examined)
      if (r.ok) { children += r.tree; cur = r.position }
      r.ok
    }
    if (ok) ParseResult(cur, node(in, data, children.result(), pos), ok = true, seen)
    else emptyGap(in, pos, seen)
  }

// Zero or more `p`, as one node; stops when `p` fails or stops consuming.
def many(data: NodeData)(p: Parser): Parser =
  (in, pos) => {
    var cur = pos
    var seen = pos
    val children = List.newBuilder[ParseTree]
    var more = true
    while (more) {
      val r = p(in, cur)
      seen = seen.max(r.examined)
      if (r.ok && r.position > cur) { children += r.tree; cur = r.position }
      else more = false
    }
    ParseResult(cur, node(in, data, children.result(), pos), ok = true, seen)
  }

// `p`, or nothing.
def optional(p: Parser): Parser = choice(p, nothing())

// Lookahead: succeeds without consuming when `p` would apply.
def followedBy(p: Parser): Parser = (in, pos) => {
  val r = p(in, pos)
  ParseResult(pos, ParseTree(NodeData.GapData(""), "", pos, 0), r.ok, r.examined)
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
  ParseResult(pos, ParseTree(NodeData.GapData(""), "", start, 0), ok,
    in.input.length.min(start) + 1)
}

// Whatever token starts here, tagged with its kind.
def anyToken: Parser = (in, pos) => {
  val scanned = scanAt(in, pos)
  scanned.token match {
    case Some(t) => ParseResult(t.to,
      ParseTree(NodeData.StringData(t.kind), t.text, t.from, t.text.length),
      ok = true, scanned.examined)
    case None => emptyGap(in, pos, scanned.examined)
  }
}

// A node spanning its children (or the empty range at `pos`). The children,
// which still carry absolute `from`s, are adopted: rebased to offsets
// from this node's start.
private def node(
    in: ParseInput, data: NodeData, children: List[ParseTree], pos: Int): ParseTree = {
  val from = children.headOption.map(_.from).getOrElse(pos)
  val to = children.lastOption.map(_.to).getOrElse(pos)
  ParseTree(data, in.input.substring(from, to), from, to - from,
    children.map(adopted(_, from)))
}
