package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means.text._

// Spanning the term-shaped parts of a `definition` encoding —
// `constraint` leaves and the meaning term — against the rule-grammar
// tree (ruleSpannedChildren).

// `constraint(name*, `: term`)` — the name leaves get their tokens;
// the term leaf covers the typed tail's tokens.
private[langdef] def spannedConstraint(
    c: DetachedNode[NodeData], group: ParseTree, base: Int)
    : SpannedTree[NodeData] = {
  val s = span(group, base)
  val inner = base + group.from
  val names = group.children.headOption.toList
    .flatMap(tokens(_, inner)).filterNot(_._2 == ",")
  val terms = group.children.lift(1).filter(ruleTag(_) == "typed").toList
    .flatMap(t => t.children.lift(1).toList
      .flatMap(tokens(_, inner + t.from)))
  val kids = c.children match {
    case init :+ termLeaf =>
      (init.zip(names).map { case (leaf, (ls, _)) => leafNode(leaf, ls) } ++
        init.drop(names.length).map(unpositioned(_, s))) :+
        (terms.headOption.map { case (first, _) =>
          leafNode(termLeaf, Span(first.from, terms.last._1.to))
        }.getOrElse(unpositioned(termLeaf, s)))
    case _ => c.children.map(unpositioned(_, s))
  }
  DetachedNode(SpannedNode(c.data, s), kids)
}

// The encoding of the meaning's single term — `build`'s arguments
// pair positionally with the detached children (nameOf/readMeaning
// preserve them); a `use` leaf gets its word token; anything the
// encoding can't place inherits the node's span.
private[langdef] def spannedTerm(
    term: DetachedNode[NodeData], mt: ParseTree, base: Int)
    : SpannedTree[NodeData] = {
  val s = span(mt, base)
  val kids = ruleTag(mt) match {
    case "build" =>
      val args = argumentTrees(mt, base)
      term.children.zip(args).map {
        case (child, (arg, argBase)) =>
          if (child.children.isEmpty) leafNode(child, span(arg, argBase))
          else spannedTerm(child, arg, argBase)
      } ++ term.children.drop(args.length).map(unpositioned(_, s))
    case "use" =>
      term.children.zipWithIndex.map {
        case (leaf, 0) =>
          mt.children.headOption.fold(unpositioned(leaf, s))(tok =>
            leafNode(leaf, span(tok, base + mt.from)))
        case (leaf, _) => unpositioned(leaf, s)
      }
    // `phrase` — a quoted subtree's positions were relative to
    // another parse; its nodes inherit the phrase's span.
    case _ => term.children.map(unpositioned(_, s))
  }
  DetachedNode(SpannedNode(term.data, s), kids)
}

// A build's argument trees — `arguments`' first `argument`, then the
// `argument` inside each of `more`'s `next` nodes (readMeaning's
// shape), or the single `argument`/`term` node when one suffices —
// each with the base its `from` is relative to.
private def argumentTrees(
    t: ParseTree, base: Int): List[(ParseTree, Int)] = {
  val inner = base + t.from
  t.children.lift(2).toList.flatMap(args => ruleTag(args) match {
    case "arguments" =>
      val argsBase = inner + args.from
      args.children.headOption.toList.map(_ -> argsBase) ++
        args.children.lift(1).toList.flatMap(more =>
          more.children.flatMap(next =>
            next.children.lift(1).toList
              .map(_ -> (argsBase + more.from + next.from))))
    case "argument" | "term" => List(args -> inner)
    case _                   => Nil
  })
}
