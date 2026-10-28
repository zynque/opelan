package opelan.examples

import opelan.foundation.document._
import Expr._

// The document<->AST codec for expressions: the subtree conventions of
// Expr's content encoding (see Expr object). Operations are constructs
// used by reference — a node whose data names lang: expression's `add`
// vocabulary entry, never the string "add".

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
  DetachedNode(exprTag(tag), List(exprToDetached(l), exprToDetached(r)))

private[examples] val binTags =
  Set(Expr.AddTag, Expr.SubTag, Expr.MulTag, Expr.DivTag)

private def binOf(tag: String): (Expr, Expr) => Expr = tag match {
  case Expr.AddTag => Add.apply
  case Expr.SubTag => Sub.apply
  case Expr.MulTag => Mul.apply
  case _           => Div.apply
}

// Interpret the subtree rooted at nodeId as an expression. The read is
// recovering: nodes that are not valid expression syntax (unrecognized
// data, foreign constructs, wrong arity, literals with children) read as
// holes, so malformed subtrees surface as gaps rather than errors — the
// same totality the text parser gives typed input.
def exprFromDocument(doc: Document[NodeData], nodeId: Int): Either[String, Expr] =
  doc.getNode(nodeId) match {
    case None => Left(s"Expr: node id $nodeId does not exist in document")
    case Some(node) => termOf(exprTerms, doc, nodeId) match {
      case Some(tag) if binTags(tag) && node.childIds.length == 2 =>
        for {
          left <- exprFromDocument(doc, node.childIds(0))
          right <- exprFromDocument(doc, node.childIds(1))
        } yield binOf(tag)(left, right)
      case _ if externalRefOf(doc, nodeId).isDefined => Right(Hole(holeText(node.data)))
      case _ => Right(leafExpr(node.data, node.childIds.isEmpty))
    }
  }

// The detached-tree counterpart of exprFromDocument — same recovery:
// anything that is not valid expression syntax reads as a hole.
def exprFromDetached(t: DetachedNode[NodeData]): Expr =
  termOfData(exprTerms, t.data) match {
    case Some(tag) if binTags(tag) && t.children.length == 2 =>
      binOf(tag)(exprFromDetached(t.children(0)), exprFromDetached(t.children(1)))
    case _ => leafExpr(t.data, t.children.isEmpty)
  }

private def leafExpr(data: NodeData, isLeaf: Boolean): Expr = data match {
  case NodeData.IntData(v) if isLeaf         => Lit(v)
  case NodeData.FloatData(f) if isLeaf       => FLit(f)
  case NodeData.InternalNodeRef(id) if isLeaf => Ref(id)
  case NodeData.GapData(t)                   => Hole(t)
  case other                                 => Hole(holeText(other))
}

// What a malformed node contributes to a hole: its surface text —
// strings unquoted, so hole text reparses as the same unrecognized run.
private[examples] def holeText(data: NodeData): String = data match {
  case NodeData.StringData(s) => s
  case other                  => NodeDataText.show(other)
}
