package opelan.examples

import opelan.foundation.document._
import Expr._

// The document<->AST codec for expressions: the subtree conventions of
// Expr's content encoding (see Expr object).

def exprToDetached(e: Expr): DetachedNode[NodeData] = e match {
  case Lit(v)    => DetachedNode.leaf(NodeData.IntData(v))
  case FLit(v)   => DetachedNode.leaf(NodeData.FloatData(v))
  case Ref(id)   => DetachedNode.leaf(NodeData.InternalNodeRef(id))
  case Hole(t)   => DetachedNode.leaf(NodeData.GapData(t))
  case Add(l, r) => opNode(Expr.AddTag, l, r)
  case Sub(l, r) => opNode(Expr.SubTag, l, r)
  case Mul(l, r) => opNode(Expr.MulTag, l, r)
  case Div(l, r) => opNode(Expr.DivTag, l, r)
}

private def opNode(tag: String, l: Expr, r: Expr): DetachedNode[NodeData] =
  DetachedNode(NodeData.StringData(tag), List(exprToDetached(l), exprToDetached(r)))

// Interpret the subtree rooted at nodeId as an expression. The read is
// recovering: nodes that are not valid expression syntax (junk data,
// wrong arity, literals with children) read as holes, so malformed
// subtrees surface as gaps rather than errors — the same totality the
// text parser gives typed input.
def exprFromDocument(doc: Document[NodeData], nodeId: Int): Either[String, Expr] =
  doc.getNode(nodeId) match {
    case None => Left(s"Expr: node id $nodeId does not exist in document")
    case Some(node) =>
      node.data match {
        case NodeData.IntData(v) if node.childIds.isEmpty => Right(Lit(v))
        case NodeData.FloatData(f) if node.childIds.isEmpty => Right(FLit(f))
        case NodeData.InternalNodeRef(id) if node.childIds.isEmpty => Right(Ref(id))
        case NodeData.GapData(t) => Right(Hole(t))
        case NodeData.StringData(Expr.AddTag) if node.childIds.length == 2 =>
          binOp(node.childIds, doc, Add.apply)
        case NodeData.StringData(Expr.SubTag) if node.childIds.length == 2 =>
          binOp(node.childIds, doc, Sub.apply)
        case NodeData.StringData(Expr.MulTag) if node.childIds.length == 2 =>
          binOp(node.childIds, doc, Mul.apply)
        case NodeData.StringData(Expr.DivTag) if node.childIds.length == 2 =>
          binOp(node.childIds, doc, Div.apply)
        case other => Right(Hole(holeText(other)))
      }
  }

// The detached-tree counterpart of exprFromDocument — same recovery:
// anything that is not valid expression syntax reads as a hole.
def exprFromDetached(t: DetachedNode[NodeData]): Expr = t.data match {
  case NodeData.IntData(v) if t.children.isEmpty   => Lit(v)
  case NodeData.FloatData(v) if t.children.isEmpty => FLit(v)
  case NodeData.InternalNodeRef(id) if t.children.isEmpty => Ref(id)
  case NodeData.GapData(x) => Hole(x)
  case NodeData.StringData(Expr.AddTag) if t.children.length == 2 =>
    bin(t.children, Add.apply)
  case NodeData.StringData(Expr.SubTag) if t.children.length == 2 =>
    bin(t.children, Sub.apply)
  case NodeData.StringData(Expr.MulTag) if t.children.length == 2 =>
    bin(t.children, Mul.apply)
  case NodeData.StringData(Expr.DivTag) if t.children.length == 2 =>
    bin(t.children, Div.apply)
  case other => Hole(holeText(other))
}

private def bin(
    children: List[DetachedNode[NodeData]],
    op: (Expr, Expr) => Expr): Expr =
  op(exprFromDetached(children(0)), exprFromDetached(children(1)))

// What a malformed node contributes to a hole: its surface text —
// strings unquoted, so hole text reparses as the same junk.
private[examples] def holeText(data: NodeData): String = data match {
  case NodeData.StringData(s) => s
  case other                  => NodeDataText.show(other)
}

// Arity is guaranteed by the caller's case guard.
private def binOp(
    childIds: List[Int],
    doc: Document[NodeData],
    op: (Expr, Expr) => Expr): Either[String, Expr] =
  for {
    left <- exprFromDocument(doc, childIds(0))
    right <- exprFromDocument(doc, childIds(1))
  } yield op(left, right)
