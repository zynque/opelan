package opelan.foundation.grammar

import opelan.foundation.document._

// Strip the spans: a PTree becomes ordinary detached document data.
def toDetached(t: PTree): DetachedNode[NodeData] =
  DetachedNode(t.data, t.children.map(toDetached))

// Map every node's data — a term-tag migration rewrites a kept parse
// tree without touching its spans. Identity-preserving: a subtree
// whose data all mapped to itself stays the same object, so reused
// parses keep their sharing.
def mapTreeData(f: NodeData => NodeData)(t: PTree): PTree = {
  val d = f(t.data)
  val children = t.children.map(mapTreeData(f))
  if ((d eq t.data) && children.corresponds(t.children)(_ eq _)) t
  else t.copy(data = d, children = children)
}

// A child adopted into a parent whose span starts at `base`: its `from`,
// absolute while it stood alone, becomes an offset from `base`. Shallow
// — the child's own children are already relative, so nothing below moves.
private[grammar] def adopted(child: PTree, base: Int): PTree =
  child.copy(from = child.from - base)

// Path (child indexes from the root) → source span, for every node —
// the text-edit → node mapping substrate. The root's `from` is absolute;
// each level adds its own offset to its parent's start.
def spansOf(t: PTree): Map[List[Int], Span] = {
  def go(t: PTree, base: Int, path: List[Int]): List[(List[Int], Span)] = {
    val from = base + t.from
    (path -> Span(from, from + t.length)) ::
      t.children.zipWithIndex.toList.flatMap { case (k, i) =>
        go(k, from, path :+ i) }
  }
  go(t, 0, Nil).toMap
}
