package opelan.foundation.view

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import ViewTree._

class ViewTreeSuite extends munit.FunSuite {

  test("flatten concatenates text runs and holes") {
    assertEquals(flatten(flow(txt("a"), il(4), hole("x"))), "a4x")
    assertEquals(flatten(hole("")), "?")
  }

  test("flatten lays out blocks, grids, and rows") {
    assertEquals(flatten(block(txt("a"), txt("b"))), "a\nb")
    assertEquals(
      flatten(grid(
        row(cell(txt("a")), cell(il(1))),
        row(cell(txt("b")), cell(il(2))))),
      "a\t1\nb\t2")
  }

  test("flatten skips provenance and degrades unknown tags to holes") {
    assertEquals(flatten(from(7, txt("x"))), "x")
    assertEquals(flatten(n(s("mystery"), sl("inner"))), "?mysteryinner")
  }

  test("a SourceRef leaf is the provenance annotation") {
    assertEquals(src(5), DetachedNode.leaf(NodeData.SourceRef(5)))
    assertEquals(
      from(5, txt("x")),
      flow(src(5), txt("x")))
  }
}
