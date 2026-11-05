package opelan.examples.sheet

import opelan.examples.{registryForDoc, termOf}
import opelan.examples.expr.{Expr, exprTag, exprTerms}
import opelan.foundation.document._
import opelan.foundation.document.typed._
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
    assertEquals(termOf(sheetTerms, doc, sheetId), Some(SheetTag))
    assert(doc.getNode(sheetId).exists(_.data.isInstanceOf[NodeData.InternalNodeRef]))
    assert(!doc.nodes.exists(_.data == NodeData.StringData(SheetTag)))
    // Each construct is imported once: title, sheet, hrow, row, mul,
    // add, ref, column. (The imports construct itself stays a direct
    // ext ref; `div` appears only in the per-person gap's text.)
    assertEquals(importEntries(doc).length, 8)
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
    assert(f.contains("B2 * C2"), f)
    assert(f.contains("D5 / B6"), f)
  }

  test("cells carry provenance back to the document") {
    val tree = SheetLanguage.render("sheet", doc).toOption.get
    val srcs = collectSrcs(tree)
    // Every displayed cell is tagged with its source node.
    assert(srcs.contains(cellId(1, 3)), srcs.mkString(","))
    assert(srcs.contains(cellId(6, 3)), srcs.mkString(","))
  }

  test("a gap in a referenced cell blocks the cells that use it") {
    // Replace Coffee's price (row 3, col 2) with a gap: its line total,
    // the total, and the per-person row all degrade to gaps.
    val broken =
      updateNodeData(cellId(3, 2), NodeData.GapData("oops"), doc).toOption.get
    val f = SheetLanguage.render("sheet", broken).map(ViewTree.flatten)
    val rows = f.toOption.get.split("\n")
    assert(rows(3).endsWith("\toops"), rows(3))
    assert(rows(4).endsWith("\toops"), rows(4))
    assert(rows(6).endsWith("\toops"), rows(6))
    assertEquals(rows(1), "Apples\t4\t1.2\t$4.80")
  }

  test("the per-person cell is seeded as a gap holding formula text") {
    val cell = doc.getNode(cellId(6, 3)).get
    assertEquals(cell.data, NodeData.GapData("D5 / B6"))
  }

  test("a gap heals in the views when the cell grammar can read it") {
    import opelan.foundation.langdef.ViewContext
    val sid = contentId(doc).get
    val noGrammar = ViewTree.flatten(sheetView(doc, sid))
    assert(noGrammar.split("\n")(6).contains("?D5 / B6"), noGrammar)
    val healed = ViewTree.flatten(
      sheetView(doc, sid, ViewContext(parseInput = t => Some(parseCellText(t)))))
    assertEquals(healed.split("\n")(6), "per person\t\t\t8.1")
    // the document itself still holds the gap
    assert(doc.getNode(cellId(6, 3)).exists(_.data.isInstanceOf[NodeData.GapData]))
  }

  test("a self-referencing cell reports a cycle, not a hang") {
    // Point a cell at itself: the qty cell's own value and every
    // formula downstream show the cycle gap rather than diverging.
    val cyc = replaceNodeContent(cellId(1, 1), cellRefTree(1, 1), doc)
      .flatMap(internalizeRefs(_, cellId(1, 1))).toOption.get
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

  test("cellText is the canonical surface text for every cell term") {
    assertEquals(cellText(doc, cellId(0, 0)), "item")        // label
    assertEquals(cellText(doc, cellId(1, 1)), "4")           // int literal
    assertEquals(cellText(doc, cellId(1, 2)), "1.2")         // float literal
    assertEquals(cellText(doc, cellId(1, 3)), "B2 * C2")
  }

  test("parseCellText covers the cell grammar") {
    assertEquals(parseCellText("Oranges").data, NodeData.StringData("Oranges"))
    assertEquals(parseCellText("5").data, NodeData.IntData(5))
    assertEquals(parseCellText("2.50").data, NodeData.FloatData(2.5))
    assertEquals(parseCellText("?oops").data, NodeData.GapData("oops"))
    assertEquals(parseCellText("?").data, NodeData.GapData(""))
    assertEquals(parseCellText("").data, NodeData.StringData(""))
    // unparsed text that mentions a cell is a formula-in-waiting: a gap
    assertEquals(parseCellText("ref:1 % ref:2").data, NodeData.GapData("ref:1 % ref:2"))
    assertEquals(parseCellText("D4 % E2").data, NodeData.GapData("D4 % E2"))
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
      cid, linkCellRefs(parseCellText("B3 + 1")), doc)
      .flatMap(internalizeRefs(_, cid)).toOption.get
    val node = edited.getNode(cid).get
    assertEquals(termOf(exprTerms, edited, cid), Some(Expr.AddTag))
    // `add` was already imported (the total row uses it) — reused.
    assertEquals(importEntries(edited), importEntries(doc))
    assertEquals(node.childIds.length, 2)
    // B3 is a `ref` construct — the reference structure, not a node id.
    assertEquals(
      termOf(sheetTerms, edited, node.childIds(0)), Some(RefTag))
    // and the ref chain still computes: 2 (Bread qty) + 1 = 3
    val rows = SheetLanguage.render("sheet", edited)
      .map(ViewTree.flatten).toOption.get.split("\n")
    assertEquals(rows(1), "Apples\t4\t1.2\t$3.00")
  }

  test("a committed cell name becomes a ref construct") {
    // Typing "B2" commits a gap the commit link materializes as
    // `ref > column("B"), row(2)` — the reference structure, stored.
    assertEquals(parseCellText("B2").data, NodeData.GapData("B2"))
    val cid = cellId(6, 3)
    val edited = replaceNodeContent(
      cid, linkCellRefs(parseCellText("B2")), doc).toOption.get
    val refNode = edited.getNode(cid).get
    assertEquals(termOf(sheetTerms, edited, cid), Some(RefTag))
    // ref(column("B"), row(2)) — the semantics, stored.
    assertEquals(
      refNode.childIds.map(termOf(sheetTerms, edited, _)),
      List(Some(ColumnTag), Some(RowTag)))
    assertEquals(
      refNode.childIds.map(edited.childrenOf(_).map(edited.nodes(_).data)),
      List(List(NodeData.StringData("B")), List(NodeData.IntData(2))))
    val rows = SheetLanguage.render("sheet", edited)
      .map(ViewTree.flatten).toOption.get.split("\n")
    assertEquals(rows(6), "per person\t\t\t$4.00")
  }

  test("cell names in a committed formula become ref constructs") {
    val cid = cellId(6, 3)
    val edited = replaceNodeContent(
      cid, linkCellRefs(parseCellText("B2 * C2")), doc)
      .flatMap(internalizeRefs(_, cid)).toOption.get
    val node = edited.getNode(cid).get
    assertEquals(termOf(exprTerms, edited, cid), Some(Expr.MulTag))
    assertEquals(
      node.childIds.map(termOf(sheetTerms, edited, _)),
      List(Some(RefTag), Some(RefTag)))
    // and the surface text names the cells back
    assertEquals(cellText(edited, cid), "B2 * C2")
  }

  test("a cell name outside the grid stays a ref, shown as a gap") {
    // `Z99` is a reference to a position the grid doesn't have: stored
    // as the ref construct, displayed as a gap until the grid grows.
    val cid = cellId(6, 3)
    val edited = replaceNodeContent(
      cid, linkCellRefs(parseCellText("Z99")), doc).toOption.get
    assertEquals(termOf(sheetTerms, edited, cid), Some(RefTag))
    val rows = SheetLanguage.render("sheet", edited)
      .map(ViewTree.flatten).toOption.get.split("\n")
    assert(rows(6).endsWith("Z99"), rows(6))
  }

  private def inputSrcs(t: DetachedNode[NodeData]): List[Int] =
    (t match {
      case DetachedNode(NodeData.StringData("input"), children) =>
        children.collect { case DetachedNode(NodeData.SourceRef(id), _) => id }
      case _ => Nil
    }) ++ t.children.flatMap(inputSrcs)

  private def collectSrcs(t: DetachedNode[NodeData]): List[Int] =
    (t.data match {
      case NodeData.SourceRef(id) => List(id)
      case _                      => Nil
    }) ++ t.children.flatMap(collectSrcs)
}
