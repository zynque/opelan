package opelan.foundation.langdef.doc

import munit.FunSuite
import opelan.examples.expr.decodedExprDef
import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._
import opelan.foundation.language.{Language, applyDocText, docText}
import opelan.foundation.means.text.printPattern

class DefDocTextSuite extends FunSuite {

  private val langFor: Document[NodeData] => Option[Language] =
    _ => Some(DocLanguage)

  private def textOf(doc: Document[NodeData]): String =
    docText(doc, langFor)

  test("the text surface displays meta constructs by name and rules as means lines") {
    val text = textOf(langDefDoc(decodedExprDef))
    assert(text.contains("dialect"), text)
    assert(text.contains("definitions"), text)
    assert(text.contains("a + b means add(a, b)"), text)
    assert(text.contains("+n means literal(n) where n: number"), text)
    assert(text.contains("binding"), text)
    // decodedExprDef declares no views — `edit`/`print`/`eval` derive — so
    // no views section encodes.
    assert(!text.contains("view"), text)
    assert(!text.contains("ref:opelan:languages/language"), text)
  }

  test("the printed text reparses to the same definition") {
    val doc = langDefDoc(decodedExprDef)
    val reapplied = applyDocText(doc, langFor, textOf(doc)).toOption.get
    assertEquals(textOf(reapplied), textOf(doc))
    assertEquals(
      langDefFromDocument("u", reapplied),
      langDefFromDocument("u", doc))
  }

  test("lang: language's own doc round-trips the surface") {
    val doc = langDefDoc(docLanguageDef)
    val reapplied = applyDocText(doc, langFor, textOf(doc)).toOption.get
    assertEquals(textOf(reapplied), textOf(doc))
  }

  test("a means line edit writes a structured definition, not a leaf string") {
    val doc = langDefDoc(decodedExprDef)
    val text = textOf(doc)
    val line = text.linesIterator.find(_.contains("a * b means")).get
    val indent = line.takeWhile(_ == ' ')
    val edited = text.replace(line,
      line + "\n" + indent + "a ^ b means pow(a, b)")
    val updated = applyDocText(doc, langFor, edited).toOption.get
    val set = langDefFromDocument("u", updated).syntax.get.rules.get
    assert(set.rules.exists(r => printPattern(r.pattern) == "a ^ b"),
      set.rules.map(r => printPattern(r.pattern)).mkString(", "))
    // The stored node is the structured encoding: a `definition` node
    // whose
    // children are `pattern`/`meaning` sections, not a string leaf.
    val cid = contentId(updated).get
    val ruleIds = updated.subtreeIds(cid)
      .filter(id => metaConsOf(updated, id).contains("definition"))
    assert(ruleIds.exists(id =>
      updated.childrenOf(id).exists(k =>
        metaConsOf(updated, k).contains("meaning"))))
  }

  test("a committed definition's use operands link to their pattern pieces") {
    val doc = langDefDoc(decodedExprDef)
    val updated = applyDocText(doc, langFor, textOf(doc)).toOption.get
    val cid = contentId(updated).get
    val uses = updated.subtreeIds(cid)
      .filter(id => metaConsOf(updated, id).contains("use"))
    assert(uses.nonEmpty)
    assert(uses.exists(u => updated.childrenOf(u).exists(k =>
      updated.getNode(k).map(_.data)
        .exists(_.isInstanceOf[NodeData.InternalNodeRef]))))
  }
}
