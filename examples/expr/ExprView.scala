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
