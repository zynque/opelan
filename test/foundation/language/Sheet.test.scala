package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.view.ViewTree

class SheetSuite extends munit.FunSuite {

  private val doc = SheetLanguage.sampleDoc
  private val sheetId = contentId(doc).get
  private def rowIds = doc.childrenOf(sheetId)
  private def cellId(r: Int, c: Int) = doc.childrenOf(rowIds(r))(c)

  // The tree as plain text: rows separated by newlines, cells by tabs.
  private def flat(view: String) =
    SheetLanguage.render(view, doc).map(ViewTree.flatten)

  test("sampleDoc is a typed sheet document") {
    assertEquals(languageForDoc(doc), Some(SheetLanguage))
    assertEquals(doc.getNode(sheetId).map(_.data),
      Some(NodeData.StringData(SheetTag)))
  }

  test("the sheet view evaluates formulas through cell refs") {
    assertEquals(flat("sheet"), Right(
      List(
        "item\tqty\tprice\tline total",
        "Apples\t4\t1.2\t4.8",
        "Bread\t2\t2.5\t5",
        "Coffee\t1\t6.4\t6.4",
        "total\t\t\t16.2",
        "people\t2\t\t",
        "per person\t\t\t8.1"
      ).mkString("\n")))
  }

  test("the formulas view shows the machinery") {
    val f = flat("formulas").toOption.get
    assert(f.contains("ref:"), f)
    assert(f.contains(" * "), f)
    assert(f.contains(" / "), f)
  }

  test("cells carry provenance back to the document") {
    val tree = SheetLanguage.render("sheet", doc).toOption.get
    val srcs = collectSrcs(tree)
    // Every displayed cell is tagged with its source node.
    assert(srcs.contains(cellId(1, 3)), srcs.mkString(","))
    assert(srcs.contains(cellId(6, 3)), srcs.mkString(","))
  }

  test("a hole in a referenced cell blocks the cells that use it") {
    // Replace Coffee's price (row 3, col 2) with a gap: its line total,
    // the total, and the per-person row all degrade to holes.
    val broken =
      updateNodeData(cellId(3, 2), NodeData.GapData("oops"), doc).toOption.get
    val f = SheetLanguage.render("sheet", broken).map(ViewTree.flatten)
    val rows = f.toOption.get.split("\n")
    assert(rows(3).endsWith("\toops"), rows(3))
    assert(rows(4).endsWith("\toops"), rows(4))
    assert(rows(6).endsWith("\toops"), rows(6))
    assertEquals(rows(1), "Apples\t4\t1.2\t4.8")
  }

  test("a self-referencing cell reports a cycle, not a hang") {
    // Point a leaf cell at itself: the qty cell's own value and every
    // formula downstream show the cycle hole rather than diverging.
    val cyc = updateNodeData(
      cellId(1, 1), NodeData.InternalNodeRef(cellId(1, 1)), doc).toOption.get
    val rows = SheetLanguage.render("sheet", cyc)
      .map(ViewTree.flatten).toOption.get.split("\n")
    assertEquals(rows(1), "Apples\tcycle\t1.2\tcycle")
  }

  test("an unknown view is a Left") {
    assert(SheetLanguage.render("nope", doc).isLeft)
  }

  private def collectSrcs(t: DetachedNode[NodeData]): List[Int] =
    (t.data match {
      case NodeData.SourceRef(id) => List(id)
      case _                      => Nil
    }) ++ t.children.flatMap(collectSrcs)
}
