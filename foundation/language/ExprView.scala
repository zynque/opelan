package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.view.ViewTree
import opelan.foundation.view.ViewTree._

// View-tree renderings for the expression language ("print", "eval").
// Both walk a *located* read of the content subtree — every node carries
// the id it was read from — so the emitted tree is fully tagged with
// SourceRef provenance: any part of the derived view can navigate to the
// document node that produced it.

// An expression read out of a document with the source node id on every
// node.
enum LocExpr {
  def src: Int
  case LocLit(src: Int, value: Int)
  case LocAdd(src: Int, left: LocExpr, right: LocExpr)
  case LocSub(src: Int, left: LocExpr, right: LocExpr)
  case LocHole(src: Int, text: String)
}

// Read the subtree at nodeId as an expression, keeping node ids.
// Recovering like Expr.fromDocument: malformed nodes read as holes.
def readLocExpr(doc: Document[NodeData], nodeId: Int): LocExpr =
  doc.getNode(nodeId) match {
    case None => LocExpr.LocHole(nodeId, "missing")
    case Some(node) => node.data match {
      case NodeData.IntData(v) if node.childIds.isEmpty =>
        LocExpr.LocLit(nodeId, v)
      case NodeData.GapData(t) =>
        LocExpr.LocHole(nodeId, t)
      case NodeData.StringData(Expr.AddTag) if node.childIds.length == 2 =>
        LocExpr.LocAdd(nodeId,
          readLocExpr(doc, node.childIds(0)), readLocExpr(doc, node.childIds(1)))
      case NodeData.StringData(Expr.SubTag) if node.childIds.length == 2 =>
        LocExpr.LocSub(nodeId,
          readLocExpr(doc, node.childIds(0)), readLocExpr(doc, node.childIds(1)))
      case other => LocExpr.LocHole(nodeId, Expr.holeText(other))
    }
  }

// The "print" view: infix text as a provenance-tagged flow.
def printTree(e: LocExpr): DetachedNode[NodeData] = e match {
  case LocExpr.LocLit(id, v)  => from(id, il(v))
  case LocExpr.LocHole(id, t) => from(id, hole(t))
  case LocExpr.LocAdd(id, l, r) =>
    flow(src(id), printTree(l), txt(" + "), printRight(r))
  case LocExpr.LocSub(id, l, r) =>
    flow(src(id), printTree(l), txt(" - "), printRight(r))
}

private def printRight(e: LocExpr): DetachedNode[NodeData] = e match {
  case _: (LocExpr.LocAdd | LocExpr.LocSub) =>
    flow(txt("("), printTree(e), txt(")"))
  case _ => printTree(e)
}

// The "eval" view: the value, or the hole evaluation is waiting on —
// tagged with the blocking node's id so the view points at the gap.
def evalTree(e: LocExpr): DetachedNode[NodeData] = evalLoc(e) match {
  case Right(v) => from(e.src, il(v))
  case Left(h)  => from(h.src, hole(h.text))
}

// Left carries the blocking hole itself — with its provenance.
private def evalLoc(e: LocExpr): Either[LocExpr.LocHole, Int] = e match {
  case LocExpr.LocLit(_, v)    => Right(v)
  case h: LocExpr.LocHole      => Left(h)
  case LocExpr.LocAdd(_, l, r) =>
    for { a <- evalLoc(l); b <- evalLoc(r) } yield a + b
  case LocExpr.LocSub(_, l, r) =>
    for { a <- evalLoc(l); b <- evalLoc(r) } yield a - b
}
