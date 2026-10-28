package opelan.examples

import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.langdef.{
  DocLanguage, docLanguageDef, langDefTypeRef}
import opelan.foundation.langdef.doc.{
  langDefDoc, langDefFromDocument, withTermRefs}
import munit.FunSuite

// Store-first language resolution: a type ref names a def doc, and the
// stored document *is* the language. The Scala registry and the
// code-resident DocLanguage remain as fallbacks.
class ResolveSuite extends FunSuite {

  private val seededStore =
    List(ExprLanguage, SheetLanguage, DocLanguage)
      .foldLeft(Store.empty)((s, l) =>
        s.put(l.typeRef.documentUrl, l.langDoc)._1)

  test("a stored def doc resolves to a live language") {
    val l = languageForRef(SheetLanguage.typeRef, seededStore)
    assert(l.isDefined)
    assertEquals(l.get.name, sheetDef.name)
    // Input behavior identical to the compiled SheetLanguage.
    List("Apples", "4", "1.5", "ref:1 * 2", "?stuck", "").foreach { t =>
      assertEquals(l.get.parseInput(t), SheetLanguage.parseInput(t),
        s"input: $t")
    }
  }

  test("resolution reads the pinned version — a save pends, doesn't retarget") {
    val edited = exprDef.copy(name = "Expressions v2")
    val s2 = seededStore.put(
      ExprLanguage.typeRef.documentUrl, langDefDoc(edited))._1
    // The ref pins v0; the v1 edit resolves only after the pin bumps.
    assertEquals(
      languageForRef(ExprLanguage.typeRef, s2).map(_.name),
      Some(ExprLanguage.name))
    assertEquals(
      languageForRef(
        ExprLanguage.typeRef.copy(documentVersionId = 1), s2).map(_.name),
      Some("Expressions v2"))
    // The untouched def still resolves too.
    assertEquals(
      languageForRef(SheetLanguage.typeRef, s2).map(_.name),
      Some(sheetDef.name))
  }

  test("registry fallback for missing or non-def docs") {
    assertEquals(
      languageForRef(ExprLanguage.typeRef, Store.empty),
      Some(ExprLanguage))
    val bogus = Store.empty.put(
      "opelan:languages/int-expression",
      buildDocument[NodeData](
        DetachedNode.leaf(NodeData.StringData("bogus"))))._1
    assertEquals(
      languageForRef(ExprLanguage.typeRef, bogus), Some(ExprLanguage))
  }

  test("DocLanguage resolves the meta ref even unseeded") {
    assertEquals(languageForRef(langDefTypeRef, Store.empty), Some(DocLanguage))
  }

  test("a lang: doc's language is the def language") {
    val d = SheetLanguage.langDoc
    val l = languageForDoc(d, seededStore)
    assert(l.isDefined)
    assertEquals(l.get.views, List("def"))
    assert(l.get.render("def", d).isRight)
  }

  test("DocLanguage is itself described by a lang: doc") {
    assertEquals(
      langDefFromDocument(
        langDefTypeRef.documentUrl, DocLanguage.langDoc),
      withTermRefs(docLanguageDef))
  }

  // The demo's healing moment under pin semantics: `/` saved as
  // expression v1 changes nothing until the sheet def accepts its
  // pending update (input-grammar ref → v1, publishing sheet v1) and
  // the budget accepts the sheet's — the hole heals on the last bump.
  test("a stored hole heals as each layer accepts its pending update") {
    import opelan.foundation.view.ViewTree
    // Seeded like the demo: lang: expression v0 staged without `/`,
    // so every pin to it lands on the staged grammar.
    val staged = List(ExprLanguage, SheetLanguage, DocLanguage)
      .foldLeft(Store.empty)((s, l) => s.put(
        l.typeRef.documentUrl,
        if (l == ExprLanguage) langDefDoc(demoExprDef) else l.langDoc)._1)
    val budget = SheetLanguage.sampleDoc
    def perPerson(st: Store, b: Document[NodeData]) =
      languageForDoc(b, st).get
        .render("sheet", b).map(ViewTree.flatten).toOption.get
        .split("\n")(6)
    assert(perPerson(staged, budget).contains("?ref:"), perPerson(staged, budget))
    val sheet = languageForDoc(budget, staged).get
    assertEquals(sheet.parseInput("ref:1 / ref:2").map(_.data),
      Some(NodeData.GapData("ref:1 / ref:2")))
    // Saving `/` as expression v1: pins unchanged — still a hole.
    val fixed = staged.put(
      ExprLanguage.typeRef.documentUrl, ExprLanguage.langDoc)._1
    assert(perPerson(fixed, budget).contains("?ref:"), perPerson(fixed, budget))
    // lang: sheet accepts: its input-grammar ref bumps to expression v1.
    val sheetDoc = fixed.get("opelan:languages/sheet", 0).get
    val s2 = fixed.put(
      "opelan:languages/sheet", acceptUpdates(sheetDoc, fixed).get)._1
    // The budget accepts: type ref and term imports bump — the hole heals.
    val budget2 = acceptUpdates(budget, s2).get
    assertEquals(perPerson(s2, budget2), "per person\t\t\t$8.10")
  }
}
