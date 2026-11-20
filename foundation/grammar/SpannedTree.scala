package opelan.foundation.grammar

import opelan.foundation.document._

// A detached node carrying the source span it covers — a parsed node
// and its position are one value, so a span can't drift from the node
// it describes (foundation/language/TextProvenance).
case class SpannedNode[A](data: A, span: Span)

// A parse of a text surface: the content tree where every node knows
// where it came from.
type SpannedTree[A] = DetachedNode[SpannedNode[A]]

// The parse tree as a spanned tree — `toDetached` keeping the spans.
// The root's `from` is absolute; a child's is relative to its parent,
// so the base accumulates on the way down.
def spanned(t: ParseTree): SpannedTree[NodeData] = {
  def go(t: ParseTree, base: Int): SpannedTree[NodeData] = {
    val from = base + t.from
    DetachedNode(SpannedNode(t.data, Span(from, from + t.length)),
      t.children.map(go(_, from)))
  }
  go(t, 0)
}

// Strip the spans — the shape `commitContent` reconciles.
def unspanned[A](t: SpannedTree[A]): DetachedNode[A] =
  DetachedNode(t.data.data, t.children.map(unspanned))
