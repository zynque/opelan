package opelan.foundation.document

import munit.FunSuite

// Pending updates: a document's external refs that pin behind a head,
// and the accept gesture that bumps them — covering the type ref on the
// root, imports entries, and inline refs alike.
class UpdatesSuite extends FunSuite {

  private def leafDoc(label: String): Document[NodeData] =
    buildDocument(DetachedNode.leaf(NodeData.StringData(label)))

  private val ref0 = ExternalNodeReference("dep", 0, 0)

  // dep v0 and v1; node 0 exists in both.
  private val store =
    Store.empty.put("dep", leafDoc("v0"))._1
      .put("dep", leafDoc("v1"))._1

  // A raw doc pinning dep@0 in its root data and in a child.
  private val pinned: Document[NodeData] = buildDocument(DetachedNode(
    NodeData.ExternalNodeRef(ref0),
    List(DetachedNode.leaf(NodeData.ExternalNodeRef(ref0)))))

  test("refs pinned behind a head are pending") {
    assertEquals(
      pendingUpdates(pinned, store).map { case (id, r, v) => (r, v) },
      List(ref0 -> 1, ref0 -> 1))
  }

  test("refs at or above the head are not pending") {
    assertEquals(pendingUpdates(pinned, Store.empty.put("dep", leafDoc("v0"))._1), Nil)
    val future = ExternalNodeReference("dep", 7, 0)
    assertEquals(
      pendingUpdates(
        buildDocument(DetachedNode.leaf(NodeData.ExternalNodeRef(future))),
        store),
      Nil)
  }

  test("a ref whose node vanished at head is a broken import, not an update") {
    val gone = ExternalNodeReference("dep", 0, 99)
    val d: Document[NodeData] =
      buildDocument(DetachedNode.leaf(NodeData.ExternalNodeRef(gone)))
    assertEquals(pendingUpdates(d, store), Nil)
    assertEquals(
      brokenRefs(d, store).map { case (_, r, v) => (r, v) },
      List(gone -> 1))
    // Accept leaves it pinned — a bump would lose the node.
    assertEquals(acceptUpdates(d, store), None)
  }

  test("a ref whose node exists at head is pending, not broken") {
    assertEquals(brokenRefs(pinned, store), Nil)
  }

  test("acceptUpdates bumps every pinned ref to the head") {
    val d = acceptUpdates(pinned, store).get
    assertEquals(pendingUpdates(d, store), Nil)
    val bumped = ExternalNodeReference("dep", 1, 0)
    assertEquals(d.getNode(d.rootId).map(_.data),
      Some(NodeData.ExternalNodeRef(bumped)))
    assert(d.childrenOf(d.rootId).forall(id =>
      d.getNode(id).map(_.data).contains(NodeData.ExternalNodeRef(bumped))))
  }

  test("acceptUpdates is a no-op without pending updates") {
    assertEquals(acceptUpdates(leafDoc("plain"), store), None)
  }
}
