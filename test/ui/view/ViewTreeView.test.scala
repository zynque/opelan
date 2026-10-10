package opelan.ui.view

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.view.ViewTree
import opelan.foundation.view.ViewTree._
import opelan.ui.fp.View

class ViewTreeViewSuite extends munit.FunSuite {

  private def vt(t: DetachedNode[NodeData]) =
    viewTree[Int](t, identity, (id, _) => -id)

  test("elements map to their tags; leaves render as text") {
    vt(cell(txt("x"))) match {
      case View.Elem(tag, _, _, kids) =>
        assertEquals(tag, "td")
        assertEquals(kids, Vector(View.Text("x")))
      case other => fail(s"expected cell element, got $other")
    }
    assertEquals(vt(txt("hi")), View.Text("hi"))
    assertEquals(vt(il(3)), View.Text("3"))
  }

  test("a SourceRef first child annotates the element with a click") {
    vt(flow(src(7), txt("x"))) match {
      case View.Elem(_, attrs, evs, _) =>
        assert(evs.contains("click"))
        assertEquals(attrs("title"), "node #7")
      case other => fail(s"expected element, got $other")
    }
  }

  test("a provenance-tagged leaf gets wrapped in a clickable span") {
    vt(from(3, il(4))) match {
      case View.Elem("span", _, evs, _) => assert(evs.contains("click"))
      case other => fail(s"expected span, got $other")
    }
  }

  test("grids render tables; stray children get wrapped in cells") {
    vt(grid(row(cell(txt("a"))), txt("stray"))) match {
      case View.Elem("table", _, _, Vector(tbody)) =>
        tbody match {
          case View.Elem("tbody", _, _, rows) =>
            assertEquals(rows.length, 2)
            assert(rows(1) match {
              case View.Elem("tr", _, _, _) => true
              case _                        => false
            })
          case other => fail(s"expected tbody, got $other")
        }
      case other => fail(s"expected table, got $other")
    }
  }

  test("an unknown tag degrades to a hole chip plus rendered children") {
    vt(n(s("mystery"), sl("inner"))) match {
      case View.Elem("div", _, _, kids) =>
        assertEquals(kids.length, 2)
        assert(kids.head match {
          case View.Elem("span", _, _, Vector(View.Text("?mystery"))) => true
          case _ => false
        })
        assertEquals(kids(1), View.Text("inner"))
      case other => fail(s"expected block, got $other")
    }
  }

  test("a gap leaf renders as a hole chip") {
    vt(hole("abc")) match {
      case View.Elem("span", _, _, Vector(View.Text("abc"))) => ()
      case other => fail(s"expected hole chip, got $other")
    }
  }

  test("an input renders a text box carrying its display and commit events") {
    vt(ViewTree.input(7, txt("42"))) match {
      case View.Elem("input", attrs, evs, _) =>
        assertEquals(attrs("value"), "42")
        assert(evs.contains("keydown"))
        assert(evs.contains("blur"))
      case other => fail(s"expected input element, got $other")
    }
  }

  test("an input without a source ref degrades to a hole") {
    vt(n(s("input"), sl("42"))) match {
      case View.Elem("span", _, _, Vector(View.Text("?input"))) => ()
      case other => fail(s"expected hole chip, got $other")
    }
  }
}
