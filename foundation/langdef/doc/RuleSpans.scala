package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means.text._

// The `definition` encoding's children, each stamped with the span of
// the rule-grammar tree part that produced it — a pattern piece or
// meaning argument tracks its identity at token granularity
// (foundation/language/TextProvenance, DocMeansLine). Walks the
// encoding against the `rule` tree — the same two structures
// `readRuleTree`+`ruleNode` relate — so the correspondence is named
// once, here; where they disagree (a malformed rule) a node inherits
// its parent's span. `base` is the tree's absolute start; `self` the
// enclosing node's own span, the fallback.
private[langdef] def ruleSpannedChildren(
    definition: DetachedNode[NodeData], tree: ParseTree,
    base: Int, self: Span): List[SpannedTree[NodeData]] = {
  val tb = base + tree.from
  val (patternPart, _, meaningPart, wherePart) = ruleParts(tree)
  definition.children match {
    case pattern :: meaning :: rest =>
      List(spannedPattern(pattern, patternPart, tb),
        spannedMeaning(meaning, meaningPart, tb)) ++
        rest.headOption.map(spannedWhere(_, wherePart, tb)).toList
    case _ => definition.children.map(unpositioned(_, self))
  }
}

// A node the walk can't place inherits its parent's span — it is
// somewhere in that text, owning no characters of its own.
private[langdef] def unpositioned(
    d: DetachedNode[NodeData], parent: Span): SpannedTree[NodeData] =
  DetachedNode(SpannedNode(d.data, parent),
    d.children.map(unpositioned(_, parent)))

private[langdef] def leafNode(d: DetachedNode[NodeData], s: Span)
    : SpannedTree[NodeData] =
  DetachedNode(SpannedNode(d.data, s), d.children.map(unpositioned(_, s)))

private def spannedPattern(
    pattern: DetachedNode[NodeData], part: ParseTree, base: Int)
    : SpannedTree[NodeData] = {
  val s = span(part, base)
  val kids = pattern.children.zip(tokens(part, base)).map {
    case (piece, (ps, _)) =>
      DetachedNode(SpannedNode(piece.data, ps), piece.children match {
        case leaf :: rest => leafNode(leaf, ps) :: rest.map(unpositioned(_, ps))
        case other        => other.map(unpositioned(_, ps))
      })
  }
  DetachedNode(SpannedNode(pattern.data, s),
    kids ++ pattern.children.drop(kids.length).map(unpositioned(_, s)))
}

private def spannedMeaning(
    meaning: DetachedNode[NodeData], part: ParseTree, base: Int)
    : SpannedTree[NodeData] = {
  val s = span(part, base)
  val kids = meaning.children match {
    case List(term) => List(spannedTerm(term, part, base))
    case _          => meaning.children.map(unpositioned(_, s))
  }
  DetachedNode(SpannedNode(meaning.data, s), kids)
}

private def spannedWhere(
    where: DetachedNode[NodeData], part: ParseTree, base: Int)
    : SpannedTree[NodeData] = {
  val s = span(part, base)
  val inner = base + part.from
  val groups = part.children.lift(1).toList.map(_ -> inner) ++
    part.children.lift(2).toList.flatMap { more =>
      more.children.flatMap { next =>
        next.children.lift(1).toList
          .map(_ -> (inner + more.from + next.from))
      }
    }
  val kids = where.children.zip(groups).map {
    case (c, (g, gb)) => spannedConstraint(c, g, gb)
  }
  DetachedNode(SpannedNode(where.data, s),
    kids ++ where.children.drop(kids.length).map(unpositioned(_, s)))
}

private[langdef] def tokens(t: ParseTree, base: Int): List[(Span, String)] =
  if (isRuleToken(t)) List((span(t, base), t.text))
  else t.children.flatMap(k => tokens(k, base + t.from))

private[langdef] def span(t: ParseTree, base: Int): Span = {
  val from = base + t.from
  Span(from, from + t.length)
}
