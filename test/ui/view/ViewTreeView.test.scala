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
      case View.Elem(tag, _, _, children) =>
        assertEquals(tag, "td")
        assertEquals(children, Vector(View.Text("x")))
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

  test("an unknown tag degrades to a gap chip plus rendered children") {
    vt(n(s("mystery"), sl("inner"))) match {
      case View.Elem("div", _, _, children) =>
        assertEquals(children.length, 2)
        assert(children.head match {
          case View.Elem("span", _, _, Vector(View.Text("?mystery"))) => true
          case _ => false
        })
        assertEquals(children(1), View.Text("inner"))
      case other => fail(s"expected block, got $other")
    }
  }

  test("a gap leaf renders as a gap chip") {
    vt(gap("abc")) match {
      case View.Elem("span", _, _, Vector(View.Text("abc"))) => ()
      case other => fail(s"expected gap chip, got $other")
    }
  }

  test("an styled: run renders a styled span; unknown roles stay plain") {
    vt(styled("keyword", txt("means"))) match {
      case View.Elem("span", attrs, _, Vector(View.Text("means"))) =>
        assert(attrs("style").contains("font-weight: bold"))
      case other => fail(s"expected styled span, got $other")
    }
    vt(styled("nonsense", txt("x"))) match {
      case View.Elem("span", attrs, _, Vector(View.Text("x"))) =>
        assert(!attrs("style").contains("color"))
      case other => fail(s"expected plain span, got $other")
    }
  }

  test("an input with styled display shows the styled run, hiding its box") {
    vt(ViewTree.input(7, styled("placeholder", txt("a")), txt(" + b"))) match {
      case View.Elem("span", _, _, Vector(display, box)) =>
        display match {
          case View.Elem("span", _, _, _) => ()
          case other => fail(s"expected display span, got $other")
        }
        box match {
          case View.Elem("input", attrs, _, _) =>
            assertEquals(attrs("value"), "a + b")
            assert(attrs("style").contains("display: none"))
          case other => fail(s"expected hidden input, got $other")
        }
      case other => fail(s"expected wrapper span, got $other")
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

  test("an input without a source ref degrades to a gap") {
    vt(n(s("input"), sl("42"))) match {
      case View.Elem("span", _, _, Vector(View.Text("?input"))) => ()
      case other => fail(s"expected gap chip, got $other")
    }
  }
}
