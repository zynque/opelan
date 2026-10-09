package opelan.foundation.document

import Detached._

class TypedSuite extends munit.FunSuite {

  val ref = ExternalNodeReference("test:type", 0, 7)
  val doc = makeTyped(ref, s("doc"), n(s("expr"), il(1), il(2)))

  test("make puts the type node first and the content root after it") {
    val children = doc.childrenOf(doc.rootId)
    assertEquals(children.length, 2)
    assertEquals(doc.getNode(children(0)).map(_.data), Some(NodeData.ExternalNodeRef(ref)))
    assertEquals(doc.getNode(children(1)).map(_.data), Some(s("expr")))
    assertEquals(doc.childrenOf(children(1)).map(id => doc.getNode(id).map(_.data)),
      List(Some(i(1)), Some(i(2))))
  }

  test("typeRefOf reads the type reference") {
    assertEquals(typeRefOf(doc), Some(ref))
  }

  test("contentId is the child after the type node") {
    assertEquals(contentId(doc), doc.childrenOf(doc.rootId).drop(1).headOption)
  }

  test("a raw document has no type") {
    val raw = beginDocument(s("root"))
    assertEquals(typeRefOf(raw), None)
    assert(!isTyped(raw))
  }

  test("a first child that is not an external ref is not a type") {
    val raw = buildDocument(n(s("a"), sl("b"), sl("c")))
    assertEquals(typeRefOf(raw), None)
    assert(!isTyped(raw))
  }
}
