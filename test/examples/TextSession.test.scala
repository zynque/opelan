package opelan.examples

import scala.util.Random
import opelan.foundation.document._
import opelan.foundation.grammar.Edit
import opelan.foundation.language.{Language, TextSession, textEdit}
import munit.FunSuite

// The text surface's incremental parse: whatever the edits, a session's
// content equals the language's full parse of the current text — for
// the Scala expression language and the interpreted one alike — and a
// small edit reruns only part of the grammar.
class TextSessionSuite extends FunSuite {

  private val interpreted = languageFromDef(exprDef)

  test("textEdit is the minimal middle replacement") {
    assertEquals(textEdit("1 + 2", "1 + 20"), Edit(5, 5, "0"))
    assertEquals(textEdit("1 + 2", "1 * 2"), Edit(2, 3, "*"))
    assertEquals(textEdit("aaa", "aa"), Edit(2, 3, ""))
    assertEquals(textEdit("", "x"), Edit(0, 0, "x"))
    assertEquals(textEdit("same", "same"), Edit(4, 4, ""))
  }

  List[(String, Language)]("ExprLanguage" -> ExprLanguage,
    "DefLanguage(exprDef)" -> interpreted).foreach { (label, lang) =>
    test(s"property: $label sessions match full parses under random edits") {
      val random = new Random(7)
      val alphabet = "0123456789+-*/(). ?xref:"
      val syntax = lang.textSyntax.get
      var text = "(1 + 2) * 3 - ref:4 / 5"
      var s = TextSession.start(syntax, text)
      (1 to 300).foreach { i =>
        val from = random.nextInt(text.length + 1)
        val to = from + random.nextInt((text.length - from).min(4) + 1)
        val ins = List.fill(random.nextInt(4))(
          alphabet(random.nextInt(alphabet.length))).mkString
        text = text.substring(0, from) + ins + text.substring(to)
        s = s.retext(syntax, text)
        assertEquals(Some(s.content), lang.parse(text), s"edit $i: '$text'")
      }
    }
  }

  test("a small edit in a long text reruns a fraction of the grammar") {
    val syntax = interpreted.textSyntax.get
    val text = (1 to 20).map(i => s"($i * $i)").mkString(" + ")
    val s0 = TextSession.start(syntax, text)
    val s1 = s0.retext(syntax, text.replace("(7 * 7)", "(7 * 8)"))
    assert(s1.evaluations * 4 < s0.evaluations,
      s"${s1.evaluations} vs ${s0.evaluations}")
  }

  test("a re-decoded unchanged def keeps the session; a changed one carries it") {
    val text = "8 / 2 + ref:3"
    val s0 = TextSession.start(languageFromDef(demoExprDef).textSyntax.get, text)
    val again = languageFromDef(demoExprDef).textSyntax.get
    assertEquals(again.key, s0.syntax.key)
    // adding "/" back changes only the expr rule: the session carries
    // over, and still equals a full parse
    val withDiv = languageFromDef(exprDef)
    val s1 = s0.retext(withDiv.textSyntax.get, text)
    assertEquals(withDiv.textSyntax.get.changedSince(s0.syntax).map(_.rules),
      Some(Set("expr")))
    assertEquals(Some(s1.content), withDiv.parse(text))
  }

  test("a def version bump carries the session — kept results re-pin") {
    import opelan.foundation.langdef.{langDefDoc, langDefFromDocument}
    val doc = langDefDoc(exprDef)
    val url = exprDef.typeRef.documentUrl
    val at = (v: Int) =>
      languageFromDef(langDefFromDocument(url, doc, v)).textSyntax.get
    val text = (1 to 5).map(i => s"($i * $i)").mkString(" + ")
    val s0 = TextSession.start(at(0), text)
    val s1 = s0.retext(at(1), text)
    // the memo carries: the rerun re-evaluates only the boundary
    // entries the edit dropped, not every position a fresh parse runs
    val fresh = TextSession.start(at(1), text)
    assert(s1.evaluations < fresh.evaluations,
      s"${s1.evaluations} vs ${fresh.evaluations}")
    // and content equals a fresh parse under the bumped version —
    // term tags pin @1, not the @0 a plain reuse would have kept
    assertEquals(Some(s1.content),
      languageFromDef(langDefFromDocument(url, doc, 1)).parse(text))
  }
}
