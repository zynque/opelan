package opelan.examples

import opelan.foundation.document._
import opelan.foundation.langdef.{
  DocLanguage, docLanguageDef, langDefDoc, langDefFromDocument,
  langDefTypeRef}
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
    // Cell behavior identical to the compiled SheetLanguage.
    List("Apples", "4", "1.5", "ref:1 * 2", "?stuck", "").foreach { t =>
      assertEquals(l.get.parseCell(t), SheetLanguage.parseCell(t),
        s"cell input: $t")
    }
  }

  test("resolution reads the head version — an edited def takes effect") {
    val edited = exprDef.copy(name = "Expressions v2")
    val s2 = seededStore.put(
      ExprLanguage.typeRef.documentUrl, langDefDoc(edited))._1
    assertEquals(
      languageForRef(ExprLanguage.typeRef, s2).map(_.name),
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
    val junk = Store.empty.put(
      "opelan:languages/int-expression",
      buildDocument[NodeData](
        DetachedNode.leaf(NodeData.StringData("junk"))))._1
    assertEquals(
      languageForRef(ExprLanguage.typeRef, junk), Some(ExprLanguage))
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
      docLanguageDef)
  }
}
