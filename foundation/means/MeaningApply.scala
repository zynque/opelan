package opelan.foundation.means

import opelan.foundation.document._
import opelan.foundation.grammar._

// What a matched rule builds: its meaning, evaluated against the trees
// its placeholders bound (DESIGN.md §4, *A meaning is a term,
// evaluated*):
//
//   use      the bound tree itself, grown to cover the whole match —
//            `(a)` keeps the operand's node, parens included in its
//            span, exactly as the `parens` combinator does
//   source   the bound source text, as a leaf
//   reparse  a phrase re-read with the language's own rules:
//            placeholder names splice in their bound source text and
//            the result is reparsed — `a % means a / 100` on `5 %`
//            builds div(5, 100). Transitional (DESIGN §4, *Phrases
//            desugar at entry*): a phrase entered through a
//            language-aware input lands as term nodes — `div(use(a),
//            quoted 100)` — and this apply-time text splice, which
//            loses the bound node's identity, goes away with it.
//   builtin  not yet — evaluation-time meanings are Phase D; an
//            unknown builtin is a hole, Never Broken
//   other    a construct: the meaning's `reference` when it carries
//            one, else `tag(name)` maps the kind name to node data
//            (DefLanguage.kindTag)
//
// Malformed input stays Never Broken: an unbound placeholder or a
// degenerated operand becomes a hole covering the match.
def applyMeaning(
    meaning: Meaning,
    bindings: Map[String, PTree],
    in: In, from: Int, to: Int,
    expression: Parser,
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
  case "reparse" =>
    val text = meaning.phrase.toList.flatMap(_.pieces).map {
      case Piece.Placeholder(name, _) =>
        bindings.get(name).map(_.text).getOrElse(name)
      case Piece.Literal(literal, _) => literal
    }.mkString(" ")
    val r = expression(In(text, in.lexicon), 0)
    r.tree.copy(text = in.input.substring(from, to),
      from = from, length = to - from)
  case "builtin" => gapNode(in, from, to)
  case construct if Meaning.isConstruct(meaning) =>
    PTree(
      meaning.reference.map(NodeData.ExternalNodeRef(_)).getOrElse(tag(construct)),
      in.input.substring(from, to), from, to - from,
      meaning.arguments.map(a => argumentTree(a, bindings, in, to, expression, tag))
        .map(rebased(_, from)))
  // Any other builtin tag — the meaning language grows a word this
  // build doesn't know — is a hole, not a node named after it.
  case _ => gapNode(in, from, to)
}

// The tree a `use` term's operand name binds — None unbound.
private def bound(
    meaning: Meaning, bindings: Map[String, PTree]): Option[PTree] =
  meaning.arguments.headOption.flatMap(Meaning.nameOf).flatMap(bindings.get)

// A nested meaning (`literal(neg(n))`) has no source of its own: it
// gets a zero-width span at the match's end. A bound placeholder keeps
// its real tree — negative relative offsets place it correctly.
private def argumentTree(
    meaning: Meaning,
    bindings: Map[String, PTree],
    in: In, at: Int,
    expression: Parser,
    tag: String => NodeData): PTree = meaning.tag match {
  case "use" => bound(meaning, bindings).getOrElse(gapNode(in, at, at))
  case _     => applyMeaning(meaning, bindings, in, at, at, expression, tag)
}

// The tree that covers `from`..`to` — a verbatim hole.
private[means] def gapNode(in: In, from: Int, to: Int): PTree = {
  val text = in.input.substring(from, to)
  PTree(NodeData.GapData(text), text, from, to - from)
}

// A tree grown to cover `from`..`to`: start moves back (or forward),
// so its kids' relative offsets shift by the same amount — the same
// re-span `parens` performs.
private[means] def grown(t: PTree, in: In, from: Int, to: Int): PTree = {
  val shift = t.from - from
  t.copy(text = in.input.substring(from, to), from = from,
    length = to - from,
    kids = t.kids.map(k => k.copy(from = k.from + shift)))
}

// A kid's absolute `from` rebased to an offset from `base` — the local
// `adopted` (that one is private to the grammar package).
private[means] def rebased(kid: PTree, base: Int): PTree =
  kid.copy(from = kid.from - base)
