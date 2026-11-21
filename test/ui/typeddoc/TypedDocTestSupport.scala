package opelan.ui.typeddoc

import opelan.examples.expr.exprTag
import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.document.Detached._
import opelan.ui.editor.EditorInput

// Fixtures shared by the TypedDoc suites.
def init: TypedDocModel = TypedDoc.init

// A node's committed shape for cross-document comparison: construct
// tags resolved through their import entries (each doc's ids differ),
// leaves as their data.
def shapeOf(d: Document[NodeData], id: Int): DetachedNode[NodeData] =
  DetachedNode(
    externalRefOf(d, id).map(NodeData.ExternalNodeRef(_))
      .getOrElse(d.getNode(id)
        .getOrElse(throw new NoSuchElementException(s"missing node $id"))
        .data),
    d.childrenOf(id).map(shapeOf(d, _)))

def exprBin(tag: String, children: DetachedNode[NodeData]*) =
  n(exprTag(tag), children*)

def exprGap(t: String): DetachedNode[NodeData] =
  DetachedNode.leaf(NodeData.GapData(t))

def cellId(doc: Document[NodeData], r: Int, c: Int): Int = {
  val sheet = contentId(doc)
    .getOrElse(throw new NoSuchElementException("no content node"))
  doc.childrenOf(doc.childrenOf(sheet)(r))(c)
}

def editorInput(m: TypedDocModel): EditorInput =
  TypedDoc.children(m).head.input.asInstanceOf[EditorInput]
