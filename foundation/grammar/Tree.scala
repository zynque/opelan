package opelan.foundation.grammar

import opelan.foundation.document._

// Strip the spans: a PTree becomes ordinary detached document data.
def toDetached(t: PTree): DetachedNode[NodeData] =
  DetachedNode(t.data, t.kids.map(toDetached))

// Map every node's data — a kind-tag migration rewrites a kept parse
// tree without touching its spans. Identity-preserving: a subtree
// whose data all mapped to itself stays the same object, so reused
// parses keep their sharing.
def mapTreeData(f: NodeData => NodeData)(t: PTree): PTree = {
  val d = f(t.data)
  val kids = t.kids.map(mapTreeData(f))
  if ((d eq t.data) && kids.corresponds(t.kids)(_ eq _)) t
  else t.copy(data = d, kids = kids)
}

// A kid adopted into a parent whose span starts at `base`: its `from`,
// absolute while it stood alone, becomes an offset from `base`. Shallow
// — the kid's own children are already relative, so nothing below moves.
private[grammar] def adopted(kid: PTree, base: Int): PTree =
  kid.copy(from = kid.from - base)

// Path (child indexes from the root) → source span, for every node —
// the text-edit → node mapping substrate. The root's `from` is absolute;
// each level adds its own offset to its parent's start.
def spansOf(t: PTree): Map[List[Int], Span] = {
  def go(t: PTree, base: Int, path: List[Int]): List[(List[Int], Span)] = {
    val from = base + t.from
    (path -> Span(from, from + t.length)) ::
      t.kids.zipWithIndex.toList.flatMap { case (k, i) =>
        go(k, from, path :+ i) }
  }
  go(t, 0, Nil).toMap
}
