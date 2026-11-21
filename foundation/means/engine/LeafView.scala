package opelan.foundation.means.engine

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.view.ViewTree._

// How a bare leaf shows — dialect-neutral, the foundation default a
// language overrides only when it differs. In a derived view: ints and
// floats as data leaves, `ref:` references as text, gaps as gap chips,
// anything else as a gap showing its surface text.
def leafView(data: NodeData): DetachedNode[NodeData] = data match {
  case NodeData.IntData(v)         => il(v)
  case NodeData.FloatData(f)       => fl(f)
  case NodeData.InternalNodeRef(t) => txt(s"ref:$t")
  case NodeData.GapData(t)         => gap(t)
  case other                       => gap(dataText(other))
}

// The same leaf as surface text — what `printWith` emits for data no
// rule claims: ints and floats as written, `ref:` references, a gap
// verbatim (or `?` when empty, so it reparses). Strings show unquoted,
// so gap text reparses as the same unrecognized run.
def leafText(data: NodeData): String = data match {
  case NodeData.IntData(v)         => v.toString
  case NodeData.FloatData(f)       => f.toString
  case NodeData.InternalNodeRef(t) => s"ref:$t"
  case NodeData.GapData(t)         => if (t.isEmpty) "?" else t
  case other                       => dataText(other)
}

private def dataText(data: NodeData): String = data match {
  case NodeData.StringData(s) => s
  case other                  => NodeDataText.show(other)
}
