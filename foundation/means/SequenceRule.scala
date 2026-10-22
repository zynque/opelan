package opelan.foundation.means

import opelan.foundation.document._
import opelan.foundation.grammar._

// Compiling a non-operator rule (`(a)`, `|a|`, `pi`, `if c then a
// else b`) to a sequence over its pieces: literals match the next
// token's text, placeholders parse a full expression of the same
// language (`expression` — the rule set's own compiled parser, so
// brackets nest and keyword-led forms contain operators).
//
// A *closed* rule (literal at both edges) recovers like `parens`:
// once its opening literal matches it is committed. When a piece
// fails the rule scans forward for its closing literal — found,
// everything up to it is a hole (`(1 2)` keeps its parens as
// "(1 2)"); absent at the end of input with the middle intact, the
// bracket closes implicitly and the meaning still builds (`(1 +`
// stays add(1, hole)); anything else is a hole to the end. A
// non-closed rule (`if c then a else b`) stays all-or-nothing for
// now — partial keyword recovery is a later refinement.
//
// Rules are memoized by pattern key — the rule's name for
// incremental invalidation until rules carry references.
def sequenceRule(
    theRule: Rule,
    expression: Parser,
    tag: String => NodeData): Parser = {
  val pieces = theRule.pattern.pieces.toVector
  val closer = pieces.last match {
    case Piece.Literal(text, _) => Some(text)
    case _                      => None
  }
  rule(sequenceRuleName(theRule.pattern)) { (in, pos) =>
    var seen = pos
    val first = scanAt(in, pos)
    seen = seen.max(first.examined)
    val open = first.token.filter(t => t.text == literalText(pieces(0)))
    open match {
      case None => failedHole(in, pos, seen)
      case Some(opened) =>
        val from = opened.from
        var cur = opened.to
        var bindings = Map.empty[String, PTree]
        var failedAt: Option[Int] = None
        var i = 1
        while (failedAt.isEmpty && i < pieces.length) {
          pieces(i) match {
            case Piece.Literal(text, _) =>
              val s = scanAt(in, cur)
              seen = seen.max(s.examined)
              s.token.filter(_.text == text) match {
                case Some(t) => cur = t.to
                case None    => failedAt = Some(i)
              }
            case Piece.Placeholder(name, _) =>
              val r = expression(in, cur)
              seen = seen.max(r.examined)
              bindings += name -> r.tree
              cur = r.pos
              if (!r.ok) failedAt = Some(i)
          }
          i += 1
        }
        failedAt match {
          case None =>
            POut(cur, applyMeaning(theRule.meaning, bindings, in, from,
              cur, expression, tag), ok = true, seen)
          case Some(at) => closer match {
            case None => failedHole(in, pos, seen)
            case Some(text) =>
              val (end, s) = findLiteral(text, in, cur)
              seen = seen.max(s)
              // Everything before the closing literal matched.
              val middleIntact = at == pieces.length - 1
              end match {
                case Some(closeEnd) =>
                  POut(closeEnd, gapNode(in, from, closeEnd), ok = true, seen)
                case None
                    if middleIntact &&
                       skipSpace(in.input, cur) >= in.input.length =>
                  POut(cur, applyMeaning(theRule.meaning, bindings, in,
                    from, in.input.length, expression, tag), ok = true, seen)
                case None =>
                  POut(in.input.length,
                    gapNode(in, from, in.input.length), ok = true, seen)
              }
          }
        }
    }
  }
}

private def literalText(piece: Piece): String = piece match {
  case Piece.Literal(text, _) => text
  case _                      => ""
}

// Scan forward for a token whose text is `text` — its end position,
// or None at the end of input — plus how far the scan read.
private def findLiteral(
    text: String, in: In, pos: Int): (Option[Int], Int) = {
  var cur = pos
  var seen = pos
  var found: Option[Int] = None
  var more = true
  while (more && found.isEmpty) {
    val s = scanAt(in, cur)
    seen = seen.max(s.examined)
    s.token match {
      case Some(t) if t.text == text => found = Some(t.to)
      case Some(t) => cur = t.to
      case None => more = false
    }
  }
  (found, seen)
}

// The local `emptyHole` (the grammar package's is private to it).
private def failedHole(in: In, pos: Int, examined: Int): POut = {
  val at = skipSpace(in.input, pos)
  POut(pos, PTree(NodeData.GapData(""), "", at, 0), ok = false,
    examined.max(at + 1))
}
