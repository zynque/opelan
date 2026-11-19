package opelan.foundation.language

import munit.FunSuite

class LineIdentitySuite extends FunSuite {
  private val start = LineIdentity.derived(
    "rules\n  a * b means mul(a, b)\n  a + b means add(a, b)", List(1, 2, 3))

  private def typed(from: LineIdentity, at: Int, text: String) =
    text.indices.foldLeft(from)((id, k) =>
      id.retext(from.text.take(at) + text.take(k + 1) + from.text.drop(at)))

  test("text typed in front of a line keeps the line's node") {
    val at = start.text.indexOf("a * b")
    val merged = typed(start, at, "a // b means div(a, b)")
    assertEquals(merged.ids, List(Some(1), Some(2), Some(3)))
  }

  test("splitting the line leaves the node with its original text") {
    val at = start.text.indexOf("a * b")
    val merged = typed(start, at, "a // b means div(a, b)")
    val split = merged.retext(
      merged.text.take(at + 22) + "\n  " + merged.text.drop(at + 22))
    assertEquals(split.lines.map(_.text.trim), List("rules",
      "a // b means div(a, b)", "a * b means mul(a, b)",
      "a + b means add(a, b)"))
    assertEquals(split.ids, List(Some(1), None, Some(2), Some(3)))
  }

  test("a line retyped from scratch is a new line") {
    val line = "  a * b means mul(a, b)"
    val at = start.text.indexOf(line)
    val cleared = start.retext(start.text.take(at) + start.text.drop(at + line.length))
    val retyped = typed(cleared, at, "  a ^ b means pow(a, b)")
    assertEquals(retyped.ids, List(Some(1), None, Some(3)))
  }

  test("new lines adopt the committed ids") {
    val grown = start.retext(start.text + "\n  a - b means sub(a, b)")
    assertEquals(grown.ids.last, None)
    assertEquals(grown.adopt(List(1, 2, 3, 9)).ids.last, Some(9))
  }
}
