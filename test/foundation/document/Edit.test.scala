package opelan.foundation.document

import Detached._

class EditSuite extends munit.FunSuite {

  //         tree:           a(5)
  //                     /    |     \
  //                   b(0)  2(1)   c(4)
  //                              /      \
  //                            d(2)   3(3)
  def doc = Build.buildDocument(n(s("a"), sl("b"), il(2), n(s("c"), sl("d"), il(3))))

  test("insertNode adds a detached subtree under a parent at the given index") {
    val res = Edit.insertNode(sl("x"), parentId = 5, index = 1, doc)
    val d = res.fold(fail(_), identity)
    assertEquals(d.childrenOf(5), List(0, 6, 1, 4))
    assertEquals(d.parentOf(6), Some(5))
    assertEquals(d.getNode(6).map(_.data), Some(s("x")))
    assertEquals(Edit.validate(d), Nil)
  }

  test("cutNode detaches a subtree but leaves it in the node sequence") {
    val res = Edit.cutNode(4, doc)
    val d = res.fold(fail(_), identity)
    assertEquals(d.childrenOf(5), List(0, 1))
    assertEquals(d.nodes.length, 6) // orphaned until compaction
    assertEquals(d.subtreeIds(4).toSet, Set(4, 2, 3))
    assertEquals(Edit.validate(d), Nil)
  }

  test("cutNode rejects the root") {
    assert(Edit.cutNode(5, doc).isLeft)
  }

  test("moveNode relocates a subtree atomically") {
    val res = Edit.moveNode(4, 0, 0, doc)
    val d = res.fold(fail(_), identity)
    assertEquals(d.childrenOf(0), List(4))
    assertEquals(d.childrenOf(5), List(0, 1))
    assertEquals(d.parentOf(4), Some(0))
    assertEquals(d.childrenOf(4), List(2, 3)) // subtree intact
    assertEquals(Edit.validate(d), Nil)
  }

  test("moveNode rejects moving a node into its own descendant") {
    assert(Edit.moveNode(4, 2, 0, doc).isLeft)
    assert(Edit.moveNode(5, 4, 0, doc).isLeft)
  }

  test("moveNode rejects moving the root") {
    assert(Edit.moveNode(5, 0, 0, doc).isLeft)
  }

  test("pasteNode reattaches a detached node") {
    val res = for {
      d1 <- Edit.cutNode(4, doc)
      d2 <- Edit.pasteNode(4, 0, 0, d1)
    } yield d2
    val d = res.fold(fail(_), identity)
    assertEquals(d.childrenOf(0), List(4))
    assertEquals(d.parentOf(4), Some(0))
    assertEquals(Edit.validate(d), Nil)
  }

  test("pasteNode rejects a node that is still attached") {
    assert(Edit.pasteNode(4, 0, 0, doc).isLeft)
  }

  test("extractSubtree returns a detached copy") {
    val res = Edit.extractSubtree(4, doc)
    assertEquals(res, Right(n(s("c"), sl("d"), il(3))))
    assertEquals(doc.childrenOf(4), List(2, 3)) // original unchanged
  }

  test("extract then insert copies a subtree under a new parent") {
    val res = for {
      sub <- Edit.extractSubtree(4, doc)
      d <- Edit.insertNode(sub, 0, 0, doc)
    } yield d
    val d = res.fold(fail(_), identity)
    assertEquals(d.childrenOf(0), List(8)) // copied subtree appended at ids 6,7,8
    assertEquals(d.childrenOf(8), List(6, 7))
    assertEquals(d.getNode(8).map(_.data), Some(s("c")))
    assertEquals(Edit.validate(d), Nil)
  }

  test("updateNodeData bumps the version and replaces data") {
    val res = Edit.updateNodeData(0, s("b2"), doc)
    assertEquals(res.map(_.getNode(0).map(n => (n.version, n.data))), Right(Some((1, s("b2")))))
  }

  test("compact removes unreachable nodes and remaps ids") {
    val d1 = Edit.cutNode(4, doc).fold(fail(_), identity)
    val (d2, remap) = Edit.compact(d1)
    // survivors b(0), 2(1), a(5) -> 0,1,2; root a becomes node 2
    assertEquals(d2.nodes.length, 3)
    assertEquals(d2.rootId, 2)
    assertEquals(d2.childrenOf(2), List(0, 1))
    assertEquals(remap(5), 2)
    assertEquals(Edit.validate(d2), Nil)
  }
}
