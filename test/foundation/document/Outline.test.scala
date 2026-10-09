package opelan.foundation.document

import opelan.foundation.document.Detached._

class OutlineSuite extends munit.FunSuite {

  val sample: Document[NodeData] =
    buildDocument(n(s("root"), sl("a"), n(s("b"), il(1), il(2)), sl("c")))

  test("render produces an indented outline") {
    assertEquals(
      render(sample),
      "\"root\"\n  \"a\"\n  \"b\"\n    1\n    2\n  \"c\"")
  }

  test("applyOutline parses outline text back into the document") {
    val doc = beginDocument(s("root"))
    val text = "\"root\"\n  \"a\"\n  \"b\"\n    1\n    2\n  \"c\""
    val d = applyOutline(doc, text).toOption.get
    assertEquals(d.getNode(d.rootId).map(_.data), Some(NodeData.StringData("root")))
    assertEquals(
      d.childrenOf(d.rootId).map(id => d.getNode(id).get.data),
      List(NodeData.StringData("a"), NodeData.StringData("b"), NodeData.StringData("c")))
    val b = d.childrenOf(d.rootId)(1)
    assertEquals(
      d.childrenOf(b).map(id => d.getNode(id).get.data),
      List(NodeData.IntData(1), NodeData.IntData(2)))
  }

  test("applyOutline preserves the root node id") {
    val doc = beginDocument(s("old"))
    val d = applyOutline(doc, "\"new\"\n  \"x\"").toOption.get
    assertEquals(d.rootId, doc.rootId)
    assertEquals(d.getNode(d.rootId).map(_.data), Some(NodeData.StringData("new")))
  }

  test("applyOutline replaces the previous children") {
    val d = applyOutline(sample, "\"root\"\n  \"only\"").toOption.get
    assertEquals(d.childrenOf(d.rootId).length, 1)
  }

  test("round-trips an outline render") {
    val d = applyOutline(sample, render(sample)).toOption.get
    assertEquals(render(d), render(sample))
  }

  test("parse builds a fresh, valid document from outline text") {
    val d = parse(render(sample)).get
    assertEquals(render(d), render(sample))
    assertEquals(validate(d), Nil)
  }

  test("parse round-trips external refs") {
    val d = parse("\"root\"\n  ref:opelan:docs/a@2#5").get
    assertEquals(
      d.childrenOf(d.rootId).map(id => d.getNode(id).map(_.data)),
      List(Some(NodeData.ExternalNodeRef(
        ExternalNodeReference("opelan:docs/a", 2, 5)))))
  }

  test("parse returns None on empty text") {
    assertEquals(parse(""), None)
    assertEquals(parse("   \n\n"), None)
  }

  test("gap lines parse to GapData") {
    val d = applyOutline(
      beginDocument(s("root")),
      "\"root\"\n  ?some unparsed text").toOption.get
    val child = d.childrenOf(d.rootId).head
    assertEquals(d.getNode(child).map(_.data), Some(NodeData.GapData("some unparsed text")))
  }

  test("empty text is an error") {
    assert(applyOutline(sample, "").isLeft)
    assert(applyOutline(sample, "   \n\n").isLeft)
  }
}
