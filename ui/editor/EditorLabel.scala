package opelan.ui.editor

import opelan.foundation.document._
import opelan.ui.fp._
import opelan.ui.fp.Dsl._

// A row's data display. References are shown projectionally: the
// referenced definition's current name (a construct's, an import's
// target), read from the store at display time, with the raw ref beside
// it — the document stores only the ref, so a rename shows everywhere.
private[editor] def nodeLabel(
    m: EditorModel, id: Int, node: Node[NodeData]): View[EditorInput] =
  node.data match {
    case _: NodeData.InternalNodeRef | _: NodeData.ExternalNodeRef =>
      refName(m.store, m.doc, id).map(refLabel(_, node)).getOrElse(dataLabel(node))
    case _ => dataLabel(node)
  }

private def refLabel(name: String, node: Node[NodeData]): View[EditorInput] =
  el("span")(
    el("span", style("color: #7b2cbf; font-weight: bold;"))(text(name)),
    el("span", style("color: #bbb; margin-left: 6px; font-size: 11px;"))(
      text(DocumentEditor.displayData(node.data))))

private def dataLabel(node: Node[NodeData]): View[EditorInput] = {
  val css = node.data match {
    case _: NodeData.InternalNodeRef | _: NodeData.ExternalNodeRef => "color: #7b2cbf;"
    case _: NodeData.GapData => "color: #b58900; background: #fdf6e3;"
    case _ => ""
  }
  el("span", style(css))(text(DocumentEditor.displayData(node.data)))
}
