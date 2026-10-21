package opelan.examples

import scala.util.Random
import opelan.foundation.document._
import opelan.foundation.language.{InputSessions, TextSession}
import munit.FunSuite

// Projected input commits through per-input sessions: whatever the edits,
// a commit's content equals the input grammar's whole parse, and
// re-editing an input reruns only part of the grammar.
class InputSessionsSuite extends FunSuite {

  private val sheet = languageFromDef(sheetDef)

  test("property: committed inputs match whole parses under random edits") {
    val random = new Random(11)
    val alphabet = "0123456789+-*/(). ?xref:"
    var text = "(ref:4 + 2) * 3 - ref:5 / 5"
    var inputs = InputSessions()
    (1 to 300).foreach { i =>
      val id = random.nextInt(3)
      val from = random.nextInt(text.length + 1)
      val to = from + random.nextInt((text.length - from).min(4) + 1)
      val inserted = List.fill(random.nextInt(4))(
        alphabet(random.nextInt(alphabet.length))).mkString
      text = text.substring(0, from) + inserted + text.substring(to)
      val (content, next) = sheet.commitInput(id, text, inputs).get
      inputs = next
      assertEquals(Some(content), sheet.parseInput(text), s"edit $i: '$text'")
    }
  }

  test("re-editing an input reruns a fraction of the grammar") {
    val text = (1 to 20).map(i => s"(ref:$i * $i)").mkString(" + ")
    val (_, c0) = sheet.commitInput(7, text, InputSessions()).get
    val edited = text.replace("(ref:7 * 7)", "(ref:7 * 8)")
    val (_, c1) = sheet.commitInput(7, edited, c0).get
    val fresh = TextSession.start(ExprLanguage.textSyntax.get, edited)
    assert(c1.sessions(7).evaluations * 4 < fresh.evaluations,
      s"${c1.sessions(7).evaluations} vs ${fresh.evaluations}")
  }

  test("a language change between commits carries the session and heals") {
    val staged = languageFromDef(demoExprDef).textSyntax.get
    val withDiv = languageFromDef(exprDef)
    val (c0, inputs) = InputSessions().parse(1, staged, "ref:3 / ref:4")
    assert(c0.data.isInstanceOf[NodeData.GapData], c0.toString)
    val (c1, _) = inputs.parse(1, withDiv.textSyntax.get, "ref:3 / ref:4")
    assertEquals(Some(c1), withDiv.parse("ref:3 / ref:4"))
  }

  test("shortcut inputs and builtin grammars commit without a session") {
    List("?stuck", "", "  ").foreach { t =>
      val (content, inputs) = sheet.commitInput(1, t, InputSessions()).get
      assertEquals(Some(content), sheet.parseInput(t))
      assertEquals(inputs, InputSessions())
    }
    val (content, inputs) =
      SheetLanguage.commitInput(1, "ref:1 * 2", InputSessions()).get
    assertEquals(Some(content), SheetLanguage.parseInput("ref:1 * 2"))
    assertEquals(inputs, InputSessions())
  }

  test("retain drops the sessions of inputs that are gone") {
    val (_, c0) = sheet.commitInput(1, "1 + 2", InputSessions()).get
    val (_, c1) = sheet.commitInput(2, "3 * 4", c0).get
    assertEquals(c1.retain(Set(2)).sessions.keySet, Set(2))
  }
}
