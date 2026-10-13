package opelan.examples

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.view.ViewTree
import opelan.foundation.view.ViewTree._

// View-tree renderings for the expression language ("print", "eval").
// Both walk a located read of the content subtree (LocExpr), so the
// emitted tree is fully tagged with SourceRef provenance: any part of a
// derived view can navigate to the document node that produced it.

// The "print" view: infix text as a provenance-tagged flow.
def printTree(e: LocExpr): DetachedNode[NodeData] = child(e, 0)

private def prec(e: LocExpr): Int = e match {
  case LocExpr.LocAdd(_, _, _) | LocExpr.LocSub(_, _, _) => 1
  case LocExpr.LocMul(_, _, _) | LocExpr.LocDiv(_, _, _) => 2
  case _                                               => 3
}

// `ctx` is the precedence a nested expression must meet to stand
// unparenthesized, as in Expr.print.
private def child(e: LocExpr, ctx: Int): DetachedNode[NodeData] =
  if (prec(e) < ctx) flow(txt("("), bare(e), txt(")")) else bare(e)

private def bare(e: LocExpr): DetachedNode[NodeData] = e match {
  case LocExpr.LocLit(id, v)     => from(id, il(v))
  case LocExpr.LocFLit(id, f)    => from(id, fl(f))
  case LocExpr.LocHole(id, t)    => from(id, hole(t))
  case LocExpr.LocRef(id, t)     => from(id, txt(s"ref:$t"))
  case LocExpr.LocAdd(id, l, r)  => op(id, l, " + ", r, 1)
  case LocExpr.LocSub(id, l, r)  => op(id, l, " - ", r, 1)
  case LocExpr.LocMul(id, l, r)  => op(id, l, " * ", r, 2)
  case LocExpr.LocDiv(id, l, r)  => op(id, l, " / ", r, 2)
}

private def op(
    id: Int, l: LocExpr, sym: String, r: LocExpr,
    p: Int): DetachedNode[NodeData] =
  flow(src(id), child(l, p), txt(sym), child(r, p + 1))

// The "eval" view: the value, or the hole evaluation is waiting on —
// tagged with the blocking node's id so the view points at the gap.
// Refs resolve through `doc`: a referenced cell's own subtree is
// evaluated, so formulas compose; the visiting set catches ref cycles.
def evalTree(doc: Document[NodeData], e: LocExpr): DetachedNode[NodeData] =
  evalLoc(e, docResolver(doc, Set(e.src))) match {
    case Right(n)        => from(e.src, txt(Num.show(n)))
    case Left((msg, id)) => from(id, hole(msg))
  }

// Left carries (message, source id) — the display text and the node to
// point at, which is not necessarily the node eval failed on: a blocked
// ref points at the hole inside its target.
private[examples] def evalLoc(
    e: LocExpr,
    resolve: Int => Either[(String, Int), Num]): Either[(String, Int), Num] =
  e match {
    case LocExpr.LocLit(_, v)      => Right(Num.I(v))
    case LocExpr.LocFLit(_, v)     => Right(Num.F(v))
    case LocExpr.LocHole(id, t)    => Left((t, id))
    case LocExpr.LocRef(_, target) => resolve(target)
    case LocExpr.LocAdd(id, l, r)  => binEval(l, r, id, resolve, Num.add)
    case LocExpr.LocSub(id, l, r)  => binEval(l, r, id, resolve, Num.sub)
    case LocExpr.LocMul(id, l, r)  => binEval(l, r, id, resolve, Num.mul)
    case LocExpr.LocDiv(id, l, r)  =>
      for {
        a <- evalLoc(l, resolve); b <- evalLoc(r, resolve)
        q <- Num.div(a, b).toRight(("division by zero", id))
      } yield q
  }

private def binEval(
    l: LocExpr, r: LocExpr, id: Int,
    resolve: Int => Either[(String, Int), Num],
    f: (Num, Num) => Num): Either[(String, Int), Num] =
  for { a <- evalLoc(l, resolve); b <- evalLoc(r, resolve) } yield f(a, b)

// Resolve a ref by evaluating the referenced node's subtree in the same
// document. `visiting` is the chain of nodes already on the stack —
// following a ref back into it reports a cycle instead of diverging.
def docResolver(
    doc: Document[NodeData],
    visiting: Set[Int]): Int => Either[(String, Int), Num] =
  target =>
    if (visiting(target)) Left(("cycle", target))
    else evalLoc(readLocExpr(doc, target), docResolver(doc, visiting + target))
