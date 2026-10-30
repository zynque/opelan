package opelan.foundation.means.engine

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means._

// What a matched rule builds: its meaning, evaluated against the trees
// its placeholders bound (DESIGN.md §4, *A meaning is a term,
// evaluated*):
//
//   use      the bound tree itself, grown to cover the whole match —
//            `(a)` keeps the operand's node, parens included in its
//            span, exactly as the `parens` combinator does
//   source   the bound source text, as a leaf
//   quote    a desugared phrase's concrete subtree — the stored tree,
//            re-spanned onto the match
//   literal  the operand term evaluated to leaf data — `literal(neg(n))`
//            turns a bound `number` into its negation; the leaf covers
//            the whole match like `source` does
//   builtin  not yet — evaluation-time meanings are Phase D; an
//            unknown builtin is a hole, Never Broken
//   other    a construct: the meaning's `reference` when it carries
//            one, else `tag(name)` maps the term name to node data
//            (DefLanguage.termTag)
//
// Malformed input stays Never Broken: an unbound placeholder or a
// degenerated operand becomes a hole covering the match.
def applyMeaning(
    meaning: Meaning,
    bindings: Map[String, PTree],
    in: In, from: Int, to: Int,
    tag: String => NodeData): PTree = meaning.tag match {
  case "use" => bound(meaning, bindings) match {
      // A bound tree that degenerated to a hole lets the hole grow
      // over the match's literals — `parens` does the same.
      case Some(t) if t.data.isInstanceOf[NodeData.GapData] =>
        gapNode(in, from, to)
      case Some(t) => grown(t, in, from, to)
      case None    => gapNode(in, from, to)
    }
  case "source" =>
    bound(meaning, bindings) match {
      case Some(t) => PTree(NodeData.StringData(t.text),
          in.input.substring(from, to), from, to - from)
      case None    => gapNode(in, from, to)
    }
  case "quote" => meaning.quoted match {
      case Some(q) => q.copy(text = in.input.substring(from, to),
          from = from, length = to - from)
      case None    => gapNode(in, from, to)
    }
  case "literal" =>
    meaning.arguments.headOption.flatMap(valueTerm(_, bindings)) match {
      case Some(data) =>
        PTree(data, in.input.substring(from, to), from, to - from)
      case None       => gapNode(in, from, to)
    }
  case "builtin" => gapNode(in, from, to)
  case construct if Meaning.isConstruct(meaning) =>
    PTree(
      meaning.reference.map(NodeData.ExternalNodeRef(_)).getOrElse(tag(construct)),
      in.input.substring(from, to), from, to - from,
      meaning.arguments.map(a => argumentTree(a, bindings, in, to, tag))
        .map(rebased(_, from)))
  // Any other builtin tag — the meaning language grows a word this
  // build doesn't know — is a hole, not a node named after it.
  case _ => gapNode(in, from, to)
}

// The tree a `use` term's operand name binds — None unbound.
private def bound(
    meaning: Meaning, bindings: Map[String, PTree]): Option[PTree] =
  meaning.arguments.headOption.flatMap(Meaning.nameOf).flatMap(bindings.get)

// A `literal` operand evaluated to leaf data: a bound placeholder's
// name is its node's data, `neg(t)` negates a numeric value. Value
// terms spell like constructs but never materialize a node — the first
// step of meanings evaluating (DESIGN §4, *A meaning is a term,
// evaluated*), scoped to leaves at parse time.
private def valueTerm(
    m: Meaning, bindings: Map[String, PTree]): Option[NodeData] =
  Meaning.nameOf(m).flatMap(bindings.get).map(_.data).orElse(m.tag match {
    case "neg" =>
      m.arguments.headOption.flatMap(valueTerm(_, bindings)).flatMap {
        case NodeData.IntData(v)   => Some(NodeData.IntData(-v))
        case NodeData.FloatData(v) => Some(NodeData.FloatData(-v))
        case _                     => None
      }
    case "use"    => bound(m, bindings).map(_.data)
    case "source" => bound(m, bindings).map(t => NodeData.StringData(t.text))
    case _        => None
  })

// A nested meaning (`literal(neg(n))`) has no source of its own: it
// gets a zero-width span at the match's end. A bound placeholder keeps
// its real tree — negative relative offsets place it correctly.
private def argumentTree(
    meaning: Meaning,
    bindings: Map[String, PTree],
    in: In, at: Int,
    tag: String => NodeData): PTree = meaning.tag match {
  case "use" => bound(meaning, bindings).getOrElse(gapNode(in, at, at))
  case _     => applyMeaning(meaning, bindings, in, at, at, tag)
}

// The tree that covers `from`..`to` — a verbatim hole.
private[means] def gapNode(in: In, from: Int, to: Int): PTree = {
  val text = in.input.substring(from, to)
  PTree(NodeData.GapData(text), text, from, to - from)
}

// A tree grown to cover `from`..`to`: start moves back (or forward),
// so its kids' relative offsets shift by the same amount — the same
// re-span `parens` performs. The root's marks go stale: the grown
// node no longer spans what the marked rule recorded, so the rule's
// memo entry stays in the side table.
private[means] def grown(t: PTree, in: In, from: Int, to: Int): PTree = {
  val shift = t.from - from
  t.copy(text = in.input.substring(from, to), from = from,
    length = to - from, marks = Nil,
    kids = t.kids.map(k => k.copy(from = k.from + shift)))
}

// A kid's absolute `from` rebased to an offset from `base` — the local
// `adopted` (that one is private to the grammar package).
private[means] def rebased(kid: PTree, base: Int): PTree =
  kid.copy(from = kid.from - base)
