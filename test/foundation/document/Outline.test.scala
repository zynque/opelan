package opelan.foundation.document

import opelan.foundation.document.Detached._

class OutlineSuite extends munit.FunSuite {

  val sample: Document[NodeData] =
    Build.buildDocument(n(s("root"), sl("a"), n(s("b"), il(1), il(2)), sl("c")))

  test("render produces an indented outline") {
    assertEquals(
      Outline.render(sample),
      "\"root\"\n  \"a\"\n  \"b\"\n    1\n    2\n  \"c\"")
  }

  test("apply parses outline text back into the document") {
    val doc = Build.beginDocument(s("root"))
    val text = "\"root\"\n  \"a\"\n  \"b\"\n    1\n    2\n  \"c\""
    val d = Outline(doc, text).toOption.get
    assertEquals(d.getNode(d.rootId).map(_.data), Some(NodeData.StringData("root")))
    assertEquals(
      d.childrenOf(d.rootId).map(id => d.getNode(id).get.data),
      List(NodeData.StringData("a"), NodeData.StringData("b"), NodeData.StringData("c")))
    val b = d.childrenOf(d.rootId)(1)
    assertEquals(
      d.childrenOf(b).map(id => d.getNode(id).get.data),
      List(NodeData.IntData(1), NodeData.IntData(2)))
  }

  test("apply preserves the root node id") {
    val doc = Build.beginDocument(s("old"))
    val d = Outline(doc, "\"new\"\n  \"x\"").toOption.get
    assertEquals(d.rootId, doc.rootId)
    assertEquals(d.getNode(d.rootId).map(_.data), Some(NodeData.StringData("new")))
  }

  test("apply replaces the previous children") {
    val d = Outline(sample, "\"root\"\n  \"only\"").toOption.get
    assertEquals(d.childrenOf(d.rootId).length, 1)
  }

  test("round-trips an outline render") {
    val d = Outline(sample, Outline.render(sample)).toOption.get
    assertEquals(Outline.render(d), Outline.render(sample))
  }

  test("gap lines parse to GapData") {
    val d = Outline(
      Build.beginDocument(s("root")),
      "\"root\"\n  ?some unparsed text").toOption.get
    val child = d.childrenOf(d.rootId).head
    assertEquals(d.getNode(child).map(_.data), Some(NodeData.GapData("some unparsed text")))
  }

  test("empty text is an error") {
    assert(Outline(sample, "").isLeft)
    assert(Outline(sample, "   \n\n").isLeft)
  }
}
