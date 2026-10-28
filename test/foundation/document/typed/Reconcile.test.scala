package opelan.foundation.document.typed

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import munit.FunSuite

// commitContent: reparsed content committed into a node preserves the
// node ids of every subtree whose content didn't change — at any
// depth — and touches nothing else.
class ReconcileSuite extends FunSuite {

  private def box(doc: Document[NodeData]): Int =
    doc.childrenOf(doc.rootId).head

  test("unchanged content keeps every id and bumps nothing") {
    val doc = buildDocument(n(s("root"), n(s("box"), il(1), sl("x"))))
    val b = box(doc)
    val d = commitContent(b, n(s("box"), il(1), sl("x")), doc).toOption.get
    assertEquals(d.childrenOf(b), doc.childrenOf(b))
    assertEquals(d.getNode(b).map(_.version), doc.getNode(b).map(_.version))
  }

  test("a changed child gets a fresh id; its siblings keep theirs") {
    val doc =
      buildDocument(n(s("root"), n(s("box"), il(1), sl("x"), il(3))))
    val b = box(doc)
    val old = doc.childrenOf(b)
    val d = commitContent(b, n(s("box"), il(1), sl("y"), il(3)), doc)
      .toOption.get
    val ids = d.childrenOf(b)
    assertEquals(ids(0), old(0))
    assertNotEquals(ids(1), old(1))
    assertEquals(ids(2), old(2))
    assertEquals(d.getNode(ids(1)).map(_.data), Some(s("y")))
  }

  test("a moved child keeps its id") {
    val doc = buildDocument(n(s("root"), n(s("box"), il(1), sl("x"))))
    val b = box(doc)
    val old = doc.childrenOf(b)
    val d = commitContent(b, n(s("box"), sl("x"), il(1)), doc)
      .toOption.get
    assertEquals(d.childrenOf(b), List(old(1), old(0)))
  }

  test("a construct whose children changed keeps its own id") {
    val doc = buildDocument(
      n(s("root"), n(s("box"), n(s("add"), il(1), il(2)), sl("x"))))
    val b = box(doc)
    val add = doc.childrenOf(b).head
    val inside = doc.childrenOf(add)
    val d = commitContent(b,
      n(s("box"), n(s("add"), il(1), il(20)), sl("x")), doc).toOption.get
    assertEquals(d.childrenOf(b), doc.childrenOf(b))
    val newInside = d.childrenOf(add)
    assertEquals(newInside(0), inside(0))
    assertNotEquals(newInside(1), inside(1))
    assertEquals(d.getNode(newInside(1)).map(_.data), Some(i(20)))
  }

  test("identical siblings keep their own ids — matching claims one each") {
    val doc = buildDocument(n(s("root"), n(s("box"), il(1), il(1), il(1))))
    val b = box(doc)
    val d = commitContent(b, n(s("box"), il(1), il(2), il(1)), doc)
      .toOption.get
    assertEquals(d.childrenOf(b)(0), doc.childrenOf(b)(0))
    assertEquals(d.childrenOf(b)(2), doc.childrenOf(b)(2))
  }

  test("internalized construct data matches the commit's inline ref") {
    val lang = ExternalNodeReference("opelan:langs/x", 0, 0)
    val kind = ExternalNodeReference("opelan:langs/x", 3, 0)
    val imports = ExternalNodeReference("opelan:langs/core", 1, 0)
    val title = ExternalNodeReference("opelan:langs/core", 2, 0)
    val built = buildDocument(n(er(lang),
      n(er(imports), erl(kind)),
      n(er(title), sl("doc")),
      n(s("pending"), il(1))))
    val content = contentId(built).get
    val entry = built.childrenOf(importsId(built).get).head
    val stored = updateNodeData(
      content, NodeData.InternalNodeRef(entry), built).toOption.get
    // Committing the inline-ref form of what's already stored keeps the
    // content node's data untouched — and its id, and its children's.
    val d = commitContent(content, n(er(kind), il(1)), stored).toOption.get
    assertEquals(
      d.getNode(content).map(_.version),
      stored.getNode(content).map(_.version))
    assertEquals(d.childrenOf(content), stored.childrenOf(content))
    assertEquals(
      d.getNode(content).map(_.data), Some(NodeData.InternalNodeRef(entry)))
  }

  test("a commit to a missing node fails") {
    val doc = buildDocument(n(s("root"), sl("x")))
    assert(commitContent(999, sl("y"), doc).isLeft)
  }
}
