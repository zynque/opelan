package opelan.foundation.grammar

import opelan.foundation.document._

// The recovering productions: where text fits no production it becomes a
// gap holding the verbatim text, so input is never lost (*Never broken*).

// The fallback gap: applies only when the token at `pos` can start an
// unrecognized run (its kind is in `start` — a boundary like ")" never
// begins a gap), then covers the maximal run of following tokens whose
// kinds are in `run`. At the end of input it produces the empty gap —
// "operand expected" — and still applies.
def unrecognized(start: Set[String], run: Set[String]): Parser =
  (in, pos) => {
    val first = scanAt(in, pos)
    first.token match {
      case None =>
        emptyGap(in, pos, first.examined).copy(ok = true)
      case Some(t0) if start(t0.kind) =>
        var end = t0.to
        var seen = first.examined
        var more = true
        while (more) {
          val next = scanAt(in, end)
          seen = seen.max(next.examined)
          next.token match {
            case Some(t) if run(t.kind) => end = t.to
            case _                      => more = false
          }
        }
        gap(in, t0.from, end, end, ok = true, seen)
      case _ => emptyGap(in, pos, first.examined)
    }
  }

// A gap covering all remaining text — for when a grammar has no
// applicable production at all (GrammarDef.Gap).
def restGap: Parser = (in, pos) => {
  val text = in.input
  val start = skipSpace(text, pos)
  var end = text.length
  while (end > start && text.charAt(end - 1).isWhitespace) end -= 1
  gap(in, start, end, text.length, ok = true, text.length + 1)
}

// The level cleanup: if text remains that `p` did not place — and the
// next token isn't a stop kind (")" inside a group) — the whole level
// merges into one gap covering its text.
def phrase(p: Parser, stops: Set[String]): Parser =
  (in, pos) => {
    val r = p(in, pos)
    var next = scanAt(in, r.pos)
    var seen = r.examined.max(next.examined)
    def atBoundary = next.token.forall(t => stops(t.kind))
    if (atBoundary) r.copy(examined = seen)
    else {
      var end = r.pos
      while (!atBoundary) {
        end = next.token.get.to
        next = scanAt(in, end)
        seen = seen.max(next.examined)
      }
      gap(in, r.tree.from, end, end, r.ok, seen)
    }
  }

// A sign fused with a numeric literal: "+4" / "-4" — sign and number
// must be adjacent, one lexical piece. The `means` spelling of the
// same production: `-n means literal(neg(n)) where n: number` (the
// placeholder's glue is the adjacency — SequenceRule).
def signum: Parser =
  (in, pos) => {
    val first = scanAt(in, pos)
    first.token
      .filter(t => t.kind == "op" && (t.text == "+" || t.text == "-"))
      .fold(emptyGap(in, pos, first.examined)) { op =>
        val next = scanAt(in, op.to)
        val seen = first.examined.max(next.examined)
        next.token
          .filter(t => t.from == op.to &&
            (t.kind == "int" || t.kind == "float"))
          .fold(emptyGap(in, pos, seen)) { num =>
            val negative = op.text == "-"
            val data = (if (num.kind == "int")
                          num.text.toIntOption.map(NodeData.IntData(_))
                        else num.text.toDoubleOption.map(NodeData.FloatData(_)))
              .getOrElse(NodeData.GapData(num.text)) match {
                case NodeData.IntData(v) =>
                  NodeData.IntData(if (negative) -v else v)
                case NodeData.FloatData(v) =>
                  NodeData.FloatData(if (negative) -v else v)
                case d => d
              }
            POut(num.to, PTree(data, in.input.substring(op.from, num.to),
              op.from, num.to - op.from), ok = true, seen)
          }
      }
  }

private[grammar] def gap(
    in: In, from: Int, to: Int, pos: Int, ok: Boolean, examined: Int): POut = {
  val text = in.input.substring(from, to)
  POut(pos, PTree(NodeData.GapData(text), text, from, to - from), ok, examined)
}
