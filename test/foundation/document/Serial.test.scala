package opelan.foundation.document

import Detached._

class SerialSuite extends munit.FunSuite {

  // A document whose ids are not post-order (inserted nodes append) and
  // with an internal ref — exactly what a plain outline reparse breaks.
  private val base = buildDocument(n(s("root"), sl("a"), sl("b")))
  private val edited = insertNode(n(s("c"), DetachedNode.leaf(NodeData.InternalNodeRef(0))),
    base.rootId, 0, base).toOption.get

  test("serialize/deserialize preserves every node id and ref") {
    val back = deserialize(serialize(edited)).get
    assertEquals(back.rootId, edited.rootId)
    assertEquals(back.preorder(back.rootId), edited.preorder(edited.rootId))
    edited.preorder(edited.rootId).foreach { case (id, _) =>
      assertEquals(back.getNode(id).map(_.data), edited.getNode(id).map(_.data))
    }
  }

  test("a plain outline reparse would renumber — the reason Serial exists") {
    val reparsed = parse(render(edited)).get
    assertNotEquals(reparsed.preorder(reparsed.rootId), edited.preorder(edited.rootId))
  }

  test("unreachable ids stay reserved, so they are never reassigned") {
    val cut = cutNode(base.childrenOf(base.rootId).head, base).toOption.get
    val back = deserialize(serialize(cut)).get
    assertEquals(back.nodes.length, cut.nodes.length)
    assertEquals(reachableIds(back), reachableIds(cut))
  }

  test("text without ids falls back to the outline parse") {
    assertEquals(deserialize(render(base)).map(render), Some(render(base)))
  }

  test("duplicate ids (a text merge) get fresh ones") {
    val back = deserialize("#2 \"root\"\n  #0 \"a\"\n  #0 \"b\"").get
    assertEquals(back.childrenOf(back.rootId).length, 2)
    assertEquals(back.childrenOf(back.rootId).distinct.length, 2)
  }

  test("empty text is no document") {
    assertEquals(deserialize("  \n"), None)
  }
}
