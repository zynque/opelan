package opelan.foundation.document

// "Detached" trees: nodes not yet assigned ids and without history, arranged
// as an actual tree structure rather than the document's indexed node store.
// Used to construct new subtrees before inserting them into a document.
case class DetachedNode[A](data: A, children: List[DetachedNode[A]] = Nil)

object DetachedNode {
  def leaf[A](data: A): DetachedNode[A] = DetachedNode(data, Nil)
}

// Compact syntax for creating detached document trees/subtrees:
//
//   import Detached._
//   n(s("a"), sl("b"), il(2), n(s("c"), sl("d"), il(3)))
//
// generates the tree:      a
//                        / |  \
//                       b  2   c
//                             / \
//                            d   3
object Detached {
  def n[A](data: A, children: DetachedNode[A]*): DetachedNode[A] =
    DetachedNode(data, children.toList)

  def s(value: String): NodeData = NodeData.StringData(value)
  def i(value: Int): NodeData = NodeData.IntData(value)
  def f(value: Double): NodeData = NodeData.FloatData(value)

  def sl(value: String): DetachedNode[NodeData] = DetachedNode.leaf(s(value))
  def il(value: Int): DetachedNode[NodeData] = DetachedNode.leaf(i(value))
  def fl(value: Double): DetachedNode[NodeData] = DetachedNode.leaf(f(value))
}
