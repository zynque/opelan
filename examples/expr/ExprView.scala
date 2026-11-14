package opelan.examples.expr

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.view.ViewTree._

// A leaf's printed form for the derived views (`printViewWith`,
// `evalView`): ints and floats as data leaves, `ref:` references as
// text, gaps as gap chips, anything else as a gap showing its data.
def exprViewLeaf(data: NodeData): DetachedNode[NodeData] = data match {
  case NodeData.IntData(v)         => il(v)
  case NodeData.FloatData(f)       => fl(f)
  case NodeData.InternalNodeRef(t) => txt(s"ref:$t")
  case NodeData.GapData(t)         => gap(t)
  case other                       => gap(gapText(other))
}

// The same leaf spelled as surface text — what `printWith` emits for
// data no rule claims: ints and floats as written, `ref:` references,
// a gap verbatim (or `?` when empty, so it reparses).
def exprPrintLeaf(data: NodeData): String = data match {
  case NodeData.IntData(v)         => v.toString
  case NodeData.FloatData(f)       => f.toString
  case NodeData.InternalNodeRef(t) => s"ref:$t"
  case NodeData.GapData(t)         => if (t.isEmpty) "?" else t
  case other                       => gapText(other)
}
