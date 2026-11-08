package opelan.foundation.means.engine

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means._

// Compiling a non-operator rule (`(a)`, `|a|`, `pi`, `if c then a
// else b`) to a sequence over its pieces: literals match the next
// token's text, placeholders parse a full expression of the same
// language (`expression` — the rule set's own compiled parser, so
// brackets nest and keyword-led forms contain operators).
//
// A *closed* rule (literal at both edges) recovers like `parentheses`:
// once its opening literal matches it is committed. When a piece
// fails the rule scans forward for its closing literal — found,
// everything up to it is a gap (`(1 2)` keeps its parentheses as
// "(1 2)"); absent at the end of input with the middle intact, the
// bracket closes implicitly and the meaning still builds (`(1 +`
// stays add(1, gap)); anything else is a gap to the end. A
// non-closed rule (`if c then a else b`) stays all-or-nothing for
// now — partial keyword recovery is a later refinement.
//
// Rules are memoized by identity (`sequenceRuleName`) — the rule
// node's id in a `lang:` document, its pattern key in code — the
// name a language change reports to invalidate.
//
// `bound` holds this rule's `where`-constrained placeholders as
// `where` terms (compileRules' boundLexicals): a placeholder bound
// to a token-reading term reads its production's token rather than
// a full expression, so `ref:c,r where c, r: whole number` can't
// pick up a phrase. A placeholder bound to a check-only term
// (`value`) still parses a full expression, then the term's `check`
// runs on the bound tree — a violation fails the piece, so the
// rule's recovery gaps it like an unmatched literal.
//
// Glue is lexical adjacency: a glued piece must start exactly where
// the previous piece ended — when *both* pieces are lexical (a
// literal, or a bound placeholder reading one token). `-n` fuses the
// sign to the number's token, so `- 4` isn't this rule. Glue beside
// an expression-level placeholder isn't a lexical constraint — `a`
// in `(a)` can be a whole sub-expression, so `( 1 )` still parses.
def sequenceRule(
    theRule: Rule,
    expression: Parser,
    tag: String => NodeData,
    bound: Map[String, LexicalTerm] = Map.empty): Parser = {
  val pieces = theRule.pattern.pieces.toVector
  val closer = pieces.last match {
    case Piece.Literal(text, _) => Some(text)
    case _                      => None
  }
  def lexical(piece: Piece): Boolean = piece match {
    case Piece.Literal(_, _)        => true
    case Piece.Placeholder(name, _) =>
      bound.get(name).exists(_.parser.isDefined)
  }
  def tight(i: Int): Boolean =
    pieces(i).glued && lexical(pieces(i - 1)) && lexical(pieces(i))
  rule(sequenceRuleName(theRule)) { (in, pos) =>
    var seen = pos
    val first = scanAt(in, pos)
    seen = seen.max(first.examined)
    val open = first.token.filter(t => t.text == literalText(pieces(0)))
    open match {
      case None => failedGap(in, pos, seen)
      case Some(opened) =>
        val from = opened.from
        var cur = opened.to
        var bindings = Map.empty[String, ParseTree]
        var failedAt: Option[Int] = None
        var i = 1
        while (failedAt.isEmpty && i < pieces.length) {
          pieces(i) match {
            case Piece.Literal(text, _) =>
              val s = scanAt(in, cur)
              seen = seen.max(s.examined)
              s.token.filter(t => t.text == text &&
                (!tight(i) || t.from == cur)) match {
                case Some(t) => cur = t.to
                case None    => failedAt = Some(i)
              }
            case Piece.Placeholder(name, _) =>
              val term = bound.get(name)
              val r = term.flatMap(_.parser)
                .getOrElse(expression)(in, cur)
              seen = seen.max(r.examined)
              // A check-only `where` term constrains the bound tree
              // after the expression parse — a violation fails the
              // piece like an unmatched literal.
              val violated = r.applied && term.exists(t =>
                t.parser.isEmpty && !t.check(r.tree.data))
              if (violated || (tight(i) && r.applied && r.tree.from != cur))
                failedAt = Some(i)
              else {
                bindings += name -> r.tree
                cur = r.position
                if (!r.applied) failedAt = Some(i)
              }
          }
          i += 1
        }
        failedAt match {
          case None =>
            ParseResult(cur, applyMeaning(theRule.meaning, bindings, in, from,
              cur, tag), applied = true, seen)
          case Some(at) => closer match {
            case None => failedGap(in, pos, seen)
            case Some(text) =>
              val (end, s) = findLiteral(text, in, cur)
              seen = seen.max(s)
              // Everything before the closing literal matched.
              val middleIntact = at == pieces.length - 1
              end match {
                case Some(closeEnd) =>
                  ParseResult(closeEnd, gapNode(in, from, closeEnd), applied = true, seen)
                case None
                    if middleIntact &&
                       skipSpace(in.input, cur) >= in.input.length =>
                  ParseResult(cur, applyMeaning(theRule.meaning, bindings, in,
                    from, in.input.length, tag), applied = true, seen)
                case None =>
                  ParseResult(in.input.length,
                    gapNode(in, from, in.input.length), applied = true, seen)
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
    text: String, in: ParseInput, pos: Int): (Option[Int], Int) = {
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

// The local `emptyGap` (the grammar package's is private to it).
private def failedGap(in: ParseInput, pos: Int, examined: Int): ParseResult = {
  val at = skipSpace(in.input, pos)
  ParseResult(pos, ParseTree(NodeData.GapData(""), "", at, 0), applied = false,
    examined.max(at + 1))
}
