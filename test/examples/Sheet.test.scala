package opelan.examples

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
    assertEquals(registryForDoc(doc), Some(SheetLanguage))
    // The content root applies lang: sheet's `sheet` construct by ref,
    // through the imports section — no "sheet" string in the document.
    assertEquals(kindOf(sheetKinds, doc, sheetId), Some(SheetTag))
    assert(doc.getNode(sheetId).exists(_.data.isInstanceOf[NodeData.InternalNodeRef]))
    assert(!doc.nodes.exists(_.data == NodeData.StringData(SheetTag)))
    // Each construct is imported once: title, sheet, hrow, row, mul,
    // add. (The imports construct itself stays a direct ext ref; `div`
    // appears only in the per-person hole's text.)
    assertEquals(importEntries(doc).length, 6)
  }

  test("the sheet view evaluates formulas through cell refs") {
    assertEquals(flat("sheet"), Right(
      List(
        "item\tqty\tprice\tline total",
        "Apples\t4\t1.2\t$4.80",
        "Bread\t2\t2.5\t$5.00",
        "Coffee\t1\t6.4\t$6.40",
        "total\t\t\t$16.20",
        "people\t2\t\t",
        "per person\t\t\t$8.10"
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
    assertEquals(rows(1), "Apples\t4\t1.2\t$4.80")
  }

  test("the per-person cell is seeded as a hole holding formula text") {
    val cell = doc.getNode(cellId(6, 3)).get
    assertEquals(cell.data,
      NodeData.GapData(s"ref:${cellId(4, 3)} / ref:${cellId(5, 1)}"))
  }

  test("a hole heals in the views when the cell grammar can read it") {
    import opelan.foundation.langdef.ViewContext
    val sid = contentId(doc).get
    val noGrammar = ViewTree.flatten(sheetView(doc, sid))
    assert(noGrammar.split("\n")(6).contains("?ref:"), noGrammar)
    val healed = ViewTree.flatten(
      sheetView(doc, sid, ViewContext(parseCell = t => Some(parseCellText(t)))))
    assertEquals(healed.split("\n")(6), "per person\t\t\t8.1")
    // the document itself still holds the hole
    assert(doc.getNode(cellId(6, 3)).exists(_.data.isInstanceOf[NodeData.GapData]))
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

  // -- projectional editing --------------------------------------------

  test("value cells are editable inputs; formulas and refs stay read-only") {
    val inputs = inputSrcs(SheetLanguage.render("sheet", doc).toOption.get)
    assert(inputs.contains(cellId(1, 2)), inputs.mkString(",")) // a price
    assert(inputs.contains(cellId(0, 0)), inputs.mkString(",")) // a header
    assert(!inputs.contains(cellId(1, 3)), "formula cell must stay read-only")
  }

  test("the formulas view is editable too, showing cell surface text") {
    val inputs = inputSrcs(SheetLanguage.render("formulas", doc).toOption.get)
    assertEquals(inputs.length, rowIds.map(doc.childrenOf(_).length).sum)
  }

  test("cellText is the canonical surface text for every cell kind") {
    assertEquals(cellText(doc, cellId(0, 0)), "item")        // label
    assertEquals(cellText(doc, cellId(1, 1)), "4")           // int literal
    assertEquals(cellText(doc, cellId(1, 2)), "1.2")         // float literal
    assertEquals(cellText(doc, cellId(1, 3)), s"ref:${cellId(1, 1)} * ref:${cellId(1, 2)}")
  }

  test("parseCellText covers the cell grammar") {
    assertEquals(parseCellText("Oranges").data, NodeData.StringData("Oranges"))
    assertEquals(parseCellText("5").data, NodeData.IntData(5))
    assertEquals(parseCellText("2.50").data, NodeData.FloatData(2.5))
    assertEquals(parseCellText("?oops").data, NodeData.GapData("oops"))
    assertEquals(parseCellText("?").data, NodeData.GapData(""))
    assertEquals(parseCellText("").data, NodeData.StringData(""))
    // unparsed text that mentions a cell is a formula-in-waiting: a hole
    assertEquals(parseCellText("ref:1 % ref:2").data, NodeData.GapData("ref:1 % ref:2"))
    assertEquals(parseCellText("2 % people").data, NodeData.StringData("2 % people"))
    assertEquals(
      parseCellText("ref:5 * 2").data, exprTag(Expr.MulTag))
  }

  test("a committed cell edit rewrites the node in place, keeping refs") {
    // Commit "3" over Apples' qty (4): the node keeps its id, so the
    // line-total ref still resolves — now to 3.
    val edited = replaceNodeContent(
      cellId(1, 1), parseCellText("3"), doc).toOption.get
    assert(edited.getNode(cellId(1, 1)).exists(_.data == NodeData.IntData(3)))
    val rows = SheetLanguage.render("sheet", edited)
      .map(ViewTree.flatten).toOption.get.split("\n")
    assertEquals(rows(1), "Apples\t3\t1.2\t$3.60")
  }

  test("replaceNodeContent swaps children while keeping the node id") {
    // A formula cell edited to a new formula: same node, new children.
    val cid = cellId(1, 3)
    val edited = replaceNodeContent(
      cid, parseCellText(s"ref:${cellId(2, 1)} + 1"), doc)
      .flatMap(internalizeRefs(_, cid)).toOption.get
    val node = edited.getNode(cid).get
    assertEquals(kindOf(exprKinds, edited, cid), Some(Expr.AddTag))
    // `add` was already imported (the total row uses it) — reused.
    assertEquals(importEntries(edited), importEntries(doc))
    assertEquals(node.childIds.length, 2)
    assert(edited.getNode(node.childIds(0)).exists(
      _.data == NodeData.InternalNodeRef(cellId(2, 1))))
    // and the ref chain still computes: 2 (Bread qty) + 1 = 3
    val rows = SheetLanguage.render("sheet", edited)
      .map(ViewTree.flatten).toOption.get.split("\n")
    assertEquals(rows(1), "Apples\t4\t1.2\t$3.00")
  }

  private def inputSrcs(t: DetachedNode[NodeData]): List[Int] =
    (t match {
      case DetachedNode(NodeData.StringData("input"), kids) =>
        kids.collect { case DetachedNode(NodeData.SourceRef(id), _) => id }
      case _ => Nil
    }) ++ t.children.flatMap(inputSrcs)

  private def collectSrcs(t: DetachedNode[NodeData]): List[Int] =
    (t.data match {
      case NodeData.SourceRef(id) => List(id)
      case _                      => Nil
    }) ++ t.children.flatMap(collectSrcs)
}
