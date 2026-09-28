package opelan.ui.editor

import opelan.foundation.document._

class DocumentEditorSuite extends munit.FunSuite {

  test("parseNodeData parses strings") {
    assertEquals(DocumentEditor.parseNodeData("hello"), NodeData.StringData("hello"))
    assertEquals(DocumentEditor.parseNodeData(""), NodeData.StringData(""))
    assertEquals(DocumentEditor.parseNodeData("4 two"), NodeData.StringData("4 two"))
  }

  test("parseNodeData parses ints") {
    assertEquals(DocumentEditor.parseNodeData("42"), NodeData.IntData(42))
    assertEquals(DocumentEditor.parseNodeData("-7"), NodeData.IntData(-7))
  }

  test("parseNodeData parses floats") {
    assertEquals(DocumentEditor.parseNodeData("1.5"), NodeData.FloatData(1.5))
    assertEquals(DocumentEditor.parseNodeData("-0.25"), NodeData.FloatData(-0.25))
  }

  test("parseNodeData parses internal refs") {
    assertEquals(DocumentEditor.parseNodeData("&5"), NodeData.InternalNodeRef(5))
    assertEquals(DocumentEditor.parseNodeData("&0"), NodeData.InternalNodeRef(0))
    assertEquals(DocumentEditor.parseNodeData("&"), NodeData.StringData("&"))
  }

  test("displayData renders each node data variant") {
    assertEquals(DocumentEditor.displayData(NodeData.StringData("abc")), "\"abc\"")
    assertEquals(DocumentEditor.displayData(NodeData.IntData(3)), "3")
    assertEquals(DocumentEditor.displayData(NodeData.FloatData(2.5)), "2.5")
    assertEquals(DocumentEditor.displayData(NodeData.InternalNodeRef(9)), "&9")
    assertEquals(
      DocumentEditor.displayData(NodeData.ExternalNodeRef(ExternalNodeReference("doc.olw", 2, 7))),
      "ext:doc.olw@2#7"
    )
  }

  test("subtreeSize counts a detached tree") {
    import Detached._
    assertEquals(DocumentEditor.subtreeSize(sl("x")), 1)
    assertEquals(DocumentEditor.subtreeSize(n(s("a"), sl("b"), n(s("c"), il(1)))), 4)
  }
}
