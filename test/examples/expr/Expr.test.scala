package opelan.examples.expr

import opelan.examples.{exprSampleDoc, languageForDoc, languageFromDef}
import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.typed._
import opelan.foundation.document.Detached._
import opelan.foundation.language.applyDocText
import opelan.foundation.means.engine.{evalNode, leafText, printWith}
import opelan.foundation.view.ViewTree

class ExprSuite extends munit.FunSuite {

  // The language the seed document reads back — decodedExprDef
  // interpreted, exactly what a stored lang: expression resolves to.
  private val lang = languageFromDef(decodedExprDef)
  private val seeded =
    Store.empty.put(exprTypeRef.documentUrl, exprDoc)._1
  private def langFor(d: Document[NodeData]) = languageForDoc(d, seeded)

  private def parse(input: String): DetachedNode[NodeData] =
    lang.parse(input).getOrElse(fail(s"no parse: $input"))

  // The derived evaluator's contract: evalNode over the document a
  // parse commits — a ref names a real node id in the same document.
  private def eval(text: String): Either[(String, Int), NodeData] = {
    val doc = buildDocument(parse(text))
    evalNode(exprReading.semantics, exprTerms,
      doc, doc.rootId, Set(doc.rootId))
  }

  // (1 + 2) - (3 - 4)  ==  4
  test("eval computes integer results") {
    assertEquals(eval("1 + 2 - (3 - 4)"), Right(NodeData.IntData(4)))
    assertEquals(eval("-7"), Right(NodeData.IntData(-7)))
    assertEquals(eval("1 - 2 - 3"), Right(NodeData.IntData(-4)))
  }

  test("eval keeps integers integral and floats decimal") {
    assertEquals(eval("4 * 5"), Right(NodeData.IntData(20)))
    assertEquals(eval("4 * 1.2"), Right(NodeData.FloatData(4.8)))
    assertEquals(eval("1 + 0.5"), Right(NodeData.FloatData(1.5)))
  }

  test("division produces a float and fails at zero") {
    assertEquals(eval("8 / 2"), Right(NodeData.FloatData(4.0)))
    assert(eval("1 / 0").isLeft)
  }

  test("refs evaluate through the document") {
    // ref:1 + 1 — post-order ids: the ref leaf is id 0, the literal
    // sibling id 1, the add root id 2. The ref evaluates the literal.
    assertEquals(eval("ref:1 + 1"), Right(NodeData.IntData(2)))
    assert(eval("ref:7").isLeft)
    // A ref to itself reports a cycle rather than diverging.
    assert(eval("ref:0").isLeft)
  }

  test("eval is blocked by gaps") {
    assert(eval("1 + x").isLeft)
  }

  // The derived printer over the parsed tree (printViewWith's string
  // sibling — the same rule set and tiers drive the document's `print`
  // view).
  private def printExpr(text: String): String =
    printWith(exprReading.rules, exprTag, leafText)(parse(text))

  test("print uses minimal parentheses") {
    assertEquals(printExpr("1 + 2 - (3 - 4)"), "1 + 2 - (3 - 4)")
    assertEquals(printExpr("1 - 2 - 3"), "1 - 2 - 3")
    assertEquals(printExpr("1 + (2 + 3)"), "1 + (2 + 3)")
    assertEquals(printExpr("42"), "42")
    assertEquals(printExpr(""), "?")
    assertEquals(printExpr("1 + foo"), "1 + foo")
  }

  test("print respects * and / precedence") {
    assertEquals(printExpr("2 + 3 * 4"), "2 + 3 * 4")
    assertEquals(printExpr("(2 + 3) * 4"), "(2 + 3) * 4")
    assertEquals(printExpr("8 * 2 / (4 * 1)"), "8 * 2 / (4 * 1)")
    assertEquals(printExpr("1.5 * 2"), "1.5 * 2")
    assertEquals(printExpr("ref:9"), "ref:9")
  }

  test("a node no rule claims displays as leaf text") {
    assertEquals(
      printWith(exprReading.rules, exprTag, leafText)(sl("hello")), "hello")
  }

  test("the seeded language evaluates a typed document's content") {
    val doc = exprSampleDoc
    assert(languageForDoc(doc, seeded).isDefined)
    assertEquals(
      lang.render("eval", doc).map(ViewTree.flatten),
      Right("4"))
    assertEquals(
      lang.render("print", doc).map(ViewTree.flatten),
      Right("1 + 2 - (3 - 4)"))
  }

  test("rendered views keep gaps as gap nodes") {
    val doc = applyDocText(exprSampleDoc, langFor, "1 + abc").toOption.get
    assertEquals(
      gaps(lang.render("eval", doc).toOption.get), List("abc"))
    assertEquals(
      gaps(lang.render("print", doc).toOption.get), List("abc"))
  }

  test("view trees tag emitted pieces with their source nodes") {
    val doc = exprSampleDoc
    val cid = contentId(doc).get
    assertEquals(
      lang.render("eval", doc),
      Right(ViewTree.from(cid, ViewTree.txt("4"))))
    // A gap blocking eval points at the node holding it.
    val gappy = applyDocText(exprSampleDoc, langFor, "1 + abc").toOption.get
    val gapId = gappy.nodes.indexWhere(_.data == NodeData.GapData("abc"))
    assertEquals(
      lang.render("eval", gappy),
      Right(ViewTree.from(gapId, ViewTree.gap("abc"))))
  }

  test("the derived print view prints text the rules read back") {
    // printViewWith derives minimal-parentheses printing from the rule
    // set; each input's flattened view is its own display.
    val expected = List(
      "1 + 2 - 3"   -> "1 + 2 - 3",
      "(1 + 2) * 3" -> "(1 + 2) * 3",
      "1.5 * 2"     -> "1.5 * 2",
      "ref:3 + 1"   -> "ref:3 + 1",
      "-4 + 5"      -> "-4 + 5",
      "1 - 2 - 3"   -> "1 - 2 - 3",
      "1 + (2 - 3)" -> "1 + (2 - 3)",
      "1 + abc"     -> "1 + abc",
      "1 +"         -> "1 + ?",
      "(1 2)"       -> "(1 2)",
      "abc"         -> "abc",
      "8 / 4 / 2"   -> "8 / 4 / 2")
    expected.foreach { case (t, printed) =>
      val doc =
        applyDocText(exprSampleDoc, langFor, t).toOption.get
      assertEquals(
        lang.render("print", doc).map(ViewTree.flatten),
        Right(printed), s"print: $t")
    }
  }

  private def gaps(t: DetachedNode[NodeData]): List[String] =
    (t.data match {
      case NodeData.GapData(text) => List(text)
      case _                    => Nil
    }) ++ t.children.flatMap(gaps)

  test("the language reports documents without expression content") {
    val raw = beginDocument(s("root"))
    assert(lang.render("eval", raw).isLeft)
  }
}
