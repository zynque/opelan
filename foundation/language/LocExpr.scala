package opelan.foundation.language

import opelan.foundation.document._

// An expression read out of a document with the source node id on every
// node — the located form the view renderers walk so each emitted piece
// carries its provenance.
enum LocExpr {
  def src: Int
  case LocLit(src: Int, value: Int)
  case LocFLit(src: Int, value: Double)
  case LocAdd(src: Int, left: LocExpr, right: LocExpr)
  case LocSub(src: Int, left: LocExpr, right: LocExpr)
  case LocMul(src: Int, left: LocExpr, right: LocExpr)
  case LocDiv(src: Int, left: LocExpr, right: LocExpr)
  // A ref node: `src` is the ref's own id, `target` the node it points at.
  case LocRef(src: Int, target: Int)
  case LocHole(src: Int, text: String)
}

// Read the subtree at nodeId as an expression, keeping node ids.
// Recovering like exprFromDocument: malformed nodes read as holes.
def readLocExpr(doc: Document[NodeData], nodeId: Int): LocExpr =
  doc.getNode(nodeId) match {
    case None => LocExpr.LocHole(nodeId, "missing")
    case Some(node) => node.data match {
      case NodeData.IntData(v) if node.childIds.isEmpty =>
        LocExpr.LocLit(nodeId, v)
      case NodeData.FloatData(f) if node.childIds.isEmpty =>
        LocExpr.LocFLit(nodeId, f)
      case NodeData.InternalNodeRef(id) if node.childIds.isEmpty =>
        LocExpr.LocRef(nodeId, id)
      case NodeData.GapData(t) =>
        LocExpr.LocHole(nodeId, t)
      case NodeData.StringData(tag) if binTags(tag) && node.childIds.length == 2 =>
        binNode(tag, nodeId,
          readLocExpr(doc, node.childIds(0)), readLocExpr(doc, node.childIds(1)))
      case other => LocExpr.LocHole(nodeId, holeText(other))
    }
  }

private val binTags =
  Set(Expr.AddTag, Expr.SubTag, Expr.MulTag, Expr.DivTag)

private def binNode(tag: String, id: Int, l: LocExpr, r: LocExpr): LocExpr =
  tag match {
    case Expr.AddTag => LocExpr.LocAdd(id, l, r)
    case Expr.SubTag => LocExpr.LocSub(id, l, r)
    case Expr.MulTag => LocExpr.LocMul(id, l, r)
    case Expr.DivTag => LocExpr.LocDiv(id, l, r)
  }
