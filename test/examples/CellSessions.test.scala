package opelan.examples

import scala.util.Random
import opelan.foundation.document._
import opelan.foundation.language.{CellSessions, TextSession}
import munit.FunSuite

// Projected cell commits through per-cell sessions: whatever the edits,
// a commit's content equals the cell grammar's whole parse, and
// re-editing a cell reruns only part of the grammar.
class CellSessionsSuite extends FunSuite {

  private val sheet = languageFromDef(sheetDef)

  test("property: committed cells match whole parses under random edits") {
    val random = new Random(11)
    val alphabet = "0123456789+-*/(). ?xref:"
    var text = "(ref:4 + 2) * 3 - ref:5 / 5"
    var cells = CellSessions()
    (1 to 300).foreach { i =>
      val id = random.nextInt(3)
      val from = random.nextInt(text.length + 1)
      val to = from + random.nextInt((text.length - from).min(4) + 1)
      val inserted = List.fill(random.nextInt(4))(
        alphabet(random.nextInt(alphabet.length))).mkString
      text = text.substring(0, from) + inserted + text.substring(to)
      val (content, next) = sheet.commitCell(id, text, cells).get
      cells = next
      assertEquals(Some(content), sheet.parseCell(text), s"edit $i: '$text'")
    }
  }

  test("re-editing a cell reruns a fraction of the grammar") {
    val text = (1 to 20).map(i => s"(ref:$i * $i)").mkString(" + ")
    val (_, c0) = sheet.commitCell(7, text, CellSessions()).get
    val edited = text.replace("(ref:7 * 7)", "(ref:7 * 8)")
    val (_, c1) = sheet.commitCell(7, edited, c0).get
    val fresh = TextSession.start(ExprLanguage.textSyntax.get, edited)
    assert(c1.sessions(7).evaluations * 4 < fresh.evaluations,
      s"${c1.sessions(7).evaluations} vs ${fresh.evaluations}")
  }

  test("a language change between commits carries the session and heals") {
    val staged = languageFromDef(demoExprDef).textSyntax.get
    val withDiv = languageFromDef(exprDef)
    val (c0, cells) = CellSessions().parse(1, staged, "ref:3 / ref:4")
    assert(c0.data.isInstanceOf[NodeData.GapData], c0.toString)
    val (c1, _) = cells.parse(1, withDiv.textSyntax.get, "ref:3 / ref:4")
    assertEquals(Some(c1), withDiv.parse("ref:3 / ref:4"))
  }

  test("shortcut cells and builtin grammars commit without a session") {
    List("?stuck", "", "  ").foreach { t =>
      val (content, cells) = sheet.commitCell(1, t, CellSessions()).get
      assertEquals(Some(content), sheet.parseCell(t))
      assertEquals(cells, CellSessions())
    }
    val (content, cells) =
      SheetLanguage.commitCell(1, "ref:1 * 2", CellSessions()).get
    assertEquals(Some(content), SheetLanguage.parseCell("ref:1 * 2"))
    assertEquals(cells, CellSessions())
  }

  test("retain drops the sessions of cells that are gone") {
    val (_, c0) = sheet.commitCell(1, "1 + 2", CellSessions()).get
    val (_, c1) = sheet.commitCell(2, "3 * 4", c0).get
    assertEquals(c1.retain(Set(2)).sessions.keySet, Set(2))
  }
}
