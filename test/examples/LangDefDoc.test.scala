package opelan.examples

import opelan.foundation.document._
import opelan.foundation.langdef._
import opelan.foundation.document.Detached._
import munit.FunSuite

// The def-doc format: encoding and reading must round-trip, and the
// seeded lang: docs (ExprLanguage.langDoc / SheetLanguage.langDoc)
// must decode back to the in-memory defs they describe (plus the kind
// identities the encoding assigns — withKindRefs).
class LangDefDocSuite extends FunSuite {

  // A meta construct, written inline (makeTyped imports it).
  private def meta(name: String): NodeData = er(metaRef(name).get)

  test("exprDef round-trips through the document encoding") {
    val url = exprDef.typeRef.documentUrl
    val doc = langDefDoc(exprDef)
    assertEquals(langDefFromDocument(url, doc), withKindRefs(exprDef))
  }

  test("constructs are stored by reference, never by name") {
    val doc = langDefDoc(exprDef)
    val strings = doc.nodes.map(_.data).collect { case NodeData.StringData(t) => t }.toSet
    assert(!strings.contains("schema") && !strings.contains("kind"), strings)
    // Every meta construct use goes through the imports section.
    val imported = importEntries(doc).map(_._2)
    assert(imported.contains(metaRef("kind").get))
    assert(imported.forall(_.documentUrl == langDefTypeRef.documentUrl))
    // Operators name the kind they build by internal ref to its entry.
    val mulEntry = kindRefs(withKindRefs(exprDef))("mul").nodeId
    assert(doc.nodes.exists(_.data == NodeData.InternalNodeRef(mulEntry)))
  }

  test("renaming a construct's definition renames its uses, decoding unaffected") {
    // Rename the meta `kind` entry in lang: language. Def docs use the
    // entry by id, so decoding ignores the rename and the projected
    // name of every use follows it.
    val kindEntry = metaRef("kind").get.nodeId
    val metaDoc = DocLanguage.langDoc
    val nameLeaf = metaDoc.childrenOf(kindEntry).head
    val renamed = updateNodeData(nameLeaf, s("construct"), metaDoc).toOption.get
    val store = Store.empty.put(langDefTypeRef.documentUrl, renamed)._1
    val doc = langDefDoc(exprDef)
    val kindUse = doc.nodes.indices.find(id =>
      !isImportEntry(doc, id) && externalRefOf(doc, id).contains(metaRef("kind").get)).get
    assertEquals(refName(store, doc, kindUse), Some("construct"))
    assertEquals(
      langDefFromDocument(exprDef.typeRef.documentUrl, doc).schema.map(_.name),
      exprDef.schema.map(_.name))
  }

  test("sheetDef round-trips, including the input language ref") {
    val url = sheetDef.typeRef.documentUrl
    val doc = langDefDoc(sheetDef)
    val back = langDefFromDocument(url, doc)
    assertEquals(back, withKindRefs(sheetDef))
    // The input grammar really does name the expression language.
    assertEquals(
      back.inputParser,
      Some(InputParserDef.Language(
        ExprLanguage.typeRef, InputFallback.Label)))
  }

  test("the seeded lang docs decode to their defs") {
    assertEquals(
      langDefFromDocument("opelan:languages/int-expression",
        ExprLanguage.langDoc),
      withKindRefs(exprDef))
    assertEquals(
      langDefFromDocument("opelan:languages/sheet",
        SheetLanguage.langDoc),
      withKindRefs(sheetDef))
  }

  test("encoding is deterministic") {
    assertEquals(
      render(langDefDoc(exprDef)),
      render(langDefDoc(exprDef)))
  }

  test("a damaged def doc still decodes — unknown grammar nodes become holes") {
    val doc = makeTyped(langDefTypeRef, er(docTitleRef), er(docImportsRef),
      s("lang: broken"),
      n(meta("definition"),
        n(meta("description"), sl("a half-typed def")),
        n(meta("syntax"), sl("expr"), sl("flibble"))))
    // The damaged doc claims the expression language's URL, so it
    // inherits exprImpls — the "expr" lexicon it names resolves.
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
      Node(0, meta("syntax"), List(2, 3), Some(0)),
      Node(0, NodeData.StringData("expr"), Nil, Some(1)),
      Node(0, meta("choice"), List(3), Some(1))))
    val d = langDefFromDocument("opelan:docs/cyc", cyc)
    assertEquals(
      d.syntax.map(_.grammar),
      Some(GrammarDef.Choice(List(GrammarDef.Hole))))
  }

  test("sheet input parsing goes through the language ref") {
    val defed = languageFromDef(sheetDef)
    val inputs = List("Apples", "4", "1.5", "ref:5", "ref:1 * 2",
      "?stuck", "", "a b", "1 +")
    inputs.foreach { t =>
      assertEquals(defed.parseInput(t), SheetLanguage.parseInput(t),
        s"input: ${"\"" + t + "\""}")
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
