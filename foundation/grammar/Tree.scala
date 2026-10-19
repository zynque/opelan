package opelan.foundation.grammar

import opelan.foundation.document._

// Strip the spans: a PTree becomes ordinary detached document data.
def toDetached(t: PTree): DetachedNode[NodeData] =
  DetachedNode(t.data, t.kids.map(toDetached))

// Path (child indexes from the root) → source span, for every node —
// the text-edit → node mapping substrate.
def spansOf(t: PTree): Map[List[Int], Span] = {
  def go(t: PTree, path: List[Int]): List[(List[Int], Span)] =
    (path -> Span(t.from, t.to)) ::
      t.kids.zipWithIndex.toList.flatMap { case (k, i) => go(k, path :+ i) }
  go(t, Nil).toMap
}
