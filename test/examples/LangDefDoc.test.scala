package opelan.examples

import opelan.foundation.document._
import opelan.foundation.langdef._
import munit.FunSuite

// The def-doc format: encoding and reading must round-trip, and the
// seeded lang: docs (ExprLanguage.langDoc / SheetLanguage.langDoc)
// must decode back to the in-memory defs they describe.
class LangDefDocSuite extends FunSuite {

  test("exprDef round-trips through the document encoding") {
    val url = exprDef.typeRef.documentUrl
    val doc = langDefDoc(exprDef)
    assertEquals(langDefFromDocument(url, doc), exprDef)
  }

  test("sheetDef round-trips, including the cell language ref") {
    val url = sheetDef.typeRef.documentUrl
    val doc = langDefDoc(sheetDef)
    val back = langDefFromDocument(url, doc)
    assertEquals(back, sheetDef)
    // The cell grammar really does name the expression language.
    assertEquals(
      back.cellParser,
      Some(CellParserDef.Language(
        ExprLanguage.typeRef, CellFallback.Label)))
  }

  test("the seeded lang docs decode to their defs") {
    assertEquals(
      langDefFromDocument("opelan:languages/int-expression",
        ExprLanguage.langDoc),
      exprDef)
    assertEquals(
      langDefFromDocument("opelan:languages/sheet",
        SheetLanguage.langDoc),
      sheetDef)
  }

  test("encoding is deterministic") {
    assertEquals(
      render(langDefDoc(exprDef)),
      render(langDefDoc(exprDef)))
  }

  test("a damaged def doc still decodes — unknown grammar nodes become holes") {
    val doc = buildDocument[NodeData](DetachedNode(
      NodeData.StringData("lang: broken"), List(
        DetachedNode.leaf(NodeData.StringData("a half-typed def")),
        DetachedNode(NodeData.StringData("syntax"), List(
          DetachedNode.leaf(NodeData.StringData("expr")),
          DetachedNode.leaf(NodeData.StringData("flibble")))))))
    // The damaged doc claims the expression language's URL, so it
    // inherits exprImpls — the "expr" lexer it names resolves.
    val d = langDefFromDocument(
      ExprLanguage.typeRef.documentUrl, doc)
    assertEquals(d.name, "lang: broken") // falls back to the title
    assertEquals(d.syntax.map(_.grammar), Some(GrammarDef.Hole))
    // The hole grammar parses nothing — total, not thrown.
    assertEquals(
      languageFromDef(d).parse("1 + 2").map(_.data),
      Some(NodeData.GapData("1 + 2")))
  }

  test("a cyclic document decodes to holes instead of looping") {
    // A malformed Document can list a node as its own descendant —
    // node 3's only child is itself.
    val cyc: Document[NodeData] = Document(0, Vector(
      Node(0, NodeData.StringData("lang: cyc"), List(1), None),
      Node(0, NodeData.StringData("syntax"), List(2, 3), Some(0)),
      Node(0, NodeData.StringData("expr"), Nil, Some(1)),
      Node(0, NodeData.StringData("choice"), List(3), Some(1))))
    val d = langDefFromDocument("opelan:docs/cyc", cyc)
    assertEquals(
      d.syntax.map(_.grammar),
      Some(GrammarDef.Choice(List(GrammarDef.Hole))))
  }

  test("sheet cell parsing goes through the language ref") {
    val defed = languageFromDef(sheetDef)
    val inputs = List("Apples", "4", "1.5", "ref:5", "ref:1 * 2",
      "?stuck", "", "a b", "1 +")
    inputs.foreach { t =>
      assertEquals(defed.parseCell(t), SheetLanguage.parseCell(t),
        s"cell input: ${"\"" + t + "\""}")
    }
  }

  test("DefLanguage(sheetDef) renders identically to SheetLanguage") {
    val doc = SheetLanguage.sampleDoc
    assertEquals(
      languageFromDef(sheetDef).render("sheet", doc),
      SheetLanguage.render("sheet", doc))
    assertEquals(
      languageFromDef(sheetDef).render("formulas", doc),
      SheetLanguage.render("formulas", doc))
  }
}
