package opelan.ui.editor

import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.typed.externalRefOf
import opelan.foundation.langdef.ambientTermOf
import opelan.ui.fp._
import opelan.ui.fp.Dsl._

// A row's data display. References are shown projectionally: the
// referenced definition's current name (a construct's, an import's
// target), read from the store at display time, with the raw ref beside
// it — the document stores only the ref, so a rename shows everywhere.
private[editor] def nodeLabel(
    m: EditorModel, id: Int, node: Node[NodeData]): View[EditorInput] =
  node.data match {
    case _: NodeData.InternalNodeRef | _: NodeData.ExternalNodeRef if !m.rawLabels =>
      refName(m.store, m.doc, id, ambientName)
        .map(refLabel(_, node, m.doc, id)).getOrElse(dataLabel(node))
    case _ => dataLabel(node)
  }

// Ambient refs name themselves outside the document layer — a
// foundation primitive or placeholder has no stored document.
private def ambientName(r: ExternalNodeReference): Option[String] =
  ambientTermOf(r).map(_.name)

private def refLabel(name: String, node: Node[NodeData],
    doc: Document[NodeData], id: Int): View[EditorInput] =
  el("span")(
    el("span", style("color: #7b2cbf; font-weight: bold;"))(text(name)),
    el("span", style("color: #bbb; margin-left: 6px; font-size: 11px;"))(
      // The path hint: the resolved external ref when the use is
      // external (inline or via an `&` import entry), else the stored
      // internal ref.
      text(externalRefOf(doc, id)
        .map(r => DocumentEditor.displayData(NodeData.ExternalNodeRef(r)))
        .getOrElse(DocumentEditor.displayData(node.data)))))

private def dataLabel(node: Node[NodeData]): View[EditorInput] = {
  val css = node.data match {
    case _: NodeData.InternalNodeRef | _: NodeData.ExternalNodeRef => "color: #7b2cbf;"
    case _: NodeData.GapData => "color: #b58900; background: #fdf6e3;"
    case _ => ""
  }
  el("span", style(css))(text(DocumentEditor.displayData(node.data)))
}
