package opelan.foundation.document

import Detached._

class DocumentSuite extends munit.FunSuite {

  //         tree:           a(5)
  //                     /    |     \
  //                   b(0)  2(1)   c(4)
  //                              /      \
  //                            d(2)   3(3)
  val detached = n(s("a"), sl("b"), il(2), n(s("c"), sl("d"), il(3)))
  val document = Build.buildDocument(detached)

  test("beginDocument creates a single root node") {
    val doc = Build.beginDocument("data")
    assertEquals(doc.getNode(0), Some(Node(0, "data", Nil, None)))
  }

  test("buildDocument assigns ids in post-order with contiguous subtrees") {
    // ids match the olw-p4 reference layout: b=0, 2=1, d=2, 3=3, c=4, a=5
    assertEquals(document.nodes.length, 6)
    assertEquals(document.rootId, 5)
    assertEquals(document.nodes.map(_.data).toList,
      List(s("b"), i(2), s("d"), i(3), s("c"), s("a")))
  }

  test("get parent") {
    assertEquals(document.parentOf(2), Some(4))
  }

  test("get no parent") {
    assertEquals(document.parentOf(5), None)
  }

  test("get children") {
    assertEquals(document.childrenOf(4), List(2, 3))
  }

  test("get no children") {
    assertEquals(document.childrenOf(3), Nil)
  }

  test("path from root to node") {
    assertEquals(document.pathFromRootTo(2), List(5, 4, 2))
  }

  test("path from root to root") {
    assertEquals(document.pathFromRootTo(5), List(5))
  }

  test("subtreeIds includes the whole subtree") {
    assertEquals(document.subtreeIds(4).toSet, Set(4, 2, 3))
    assertEquals(document.subtreeIds(5).toSet, Set(5, 0, 1, 4, 2, 3))
  }

  test("validate reports no errors on a built document") {
    assertEquals(Edit.validate(document), Nil)
  }

  test("validate detects a broken parent link") {
    val bad = document.copy(
      nodes = document.nodes.updated(2, document.nodes(2).copy(parentId = Some(5)))
    )
    assert(Edit.validate(bad).nonEmpty)
  }
}
