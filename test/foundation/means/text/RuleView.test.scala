package opelan.foundation.means.text

import munit.FunSuite
import opelan.foundation.document._
import opelan.foundation.means._
import opelan.foundation.view.{Verbosity, ViewTree}

class RuleViewSuite extends FunSuite {

  private def read(t: String): Rule = readRule(t).fold(e => fail(e), identity)

  // The styled: tags under a view node, in order.
  private def marks(t: DetachedNode[NodeData]): List[String] =
    (t.data match {
      case NodeData.StringData(tag) if tag.startsWith("styled:") => List(tag)
      case _ => Nil
    }) ++ t.children.flatMap(marks)

  test("the rule view flattens back to the printed line") {
    val r = read("a + b means add(a, b) where a, b: number")
    assertEquals(ViewTree.flatten(ruleView(r)), printRule(r))
    assertEquals(ViewTree.flatten(ruleView(r, Verbosity.Terse)),
      printRule(r, Verbosity.Terse))
  }

  test("roles reach the view as styled: elements") {
    assertEquals(marks(ruleView(read("a + b means add(a, b)"))).toSet,
      Set("styled:placeholder", "styled:literal", "styled:keyword", "styled:tag"))
  }

  test("terse hides where behind a styled marker") {
    val r = read("a + b means add(a, b) where a, b: number")
    assert(marks(ruleView(r, Verbosity.Terse)).contains("styled:marker"))
  }

  test("field displays carry their roles") {
    val r = read("a + b means add(a, b) where a: number")
    assertEquals(patternView(r.pattern).flatMap(marks).toSet,
      Set("styled:placeholder", "styled:literal"))
    assertEquals(meaningView(r.meaning).flatMap(marks).toSet,
      Set("styled:placeholder", "styled:tag"))
    assertEquals(whereView(r.where).flatMap(marks).toSet,
      Set("styled:placeholder", "styled:term"))
    assertEquals(
      patternView(r.pattern).map(ViewTree.flatten).mkString, "a + b")
  }
}
