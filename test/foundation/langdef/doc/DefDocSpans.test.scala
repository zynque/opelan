package opelan.foundation.langdef.doc

import munit.FunSuite
import opelan.examples.expr.decodedExprDef
import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._
import opelan.foundation.language.{
  Language, TextProvenance, applyTrackedText, docText}
import opelan.foundation.means.text.printPattern

class DefDocSpansSuite extends FunSuite {

  private val langFor: Document[NodeData] => Option[Language] =
    _ => Some(DocLanguage)

  private def textOf(doc: Document[NodeData]) = docText(doc, langFor)

  // The `definition` node ids whose rule's pattern prints `pattern`.
  private def definitionIds(
      doc: Document[NodeData], pattern: String): List[Int] = {
    val cid = contentId(doc).get
    val termIds = deriveTermIds(doc, cid)
    doc.subtreeIds(cid).filter(id =>
      metaConsOf(doc, id).contains("definition") &&
        printPattern(
          ruleFromDoc(doc, "", 0, termIds, _ => None, id).pattern) == pattern)
  }

  // The pattern-piece `literal` node ids whose token text is `text` —
  // not a `literal(n)` meaning operand, whose parent isn't `pattern`.
  private def literalIds(doc: Document[NodeData], text: String): List[Int] =
    doc.subtreeIds(contentId(doc).get).filter(id =>
      metaConsOf(doc, id).contains("literal") &&
        doc.parentOf(id).exists(p => metaConsOf(doc, p).contains("pattern")) &&
        doc.childrenOf(id).headOption.flatMap(doc.getNode)
          .exists(_.data == NodeData.StringData(text)))

  private def edited(doc: Document[NodeData], text: String) =
    applyTrackedText(doc, langFor, textOf(doc), None, text)._1.toOption.get

  test("a means line's parse locates its pieces and meaning arguments") {
    val text = "dialect\n  syntax\n    definitions\n" +
      "      a * b means mul(a, b) where b: number"
    val tree = DocLanguage.parseSpanned(text).get
    def at(path: List[Int]) =
      path.foldLeft(Option(tree))((n, i) => n.flatMap(_.children.lift(i)))
        .map(n => text.substring(n.data.span.from, n.data.span.to))
    // dialect / syntax / definitions / definition / pattern / meaning / where
    assertEquals(at(Nil), Some("dialect"))
    assertEquals(at(List(0, 0, 0)),
      Some("      a * b means mul(a, b) where b: number"))
    assertEquals(at(List(0, 0, 0, 0, 0)), Some("a"))       // placeholder a
    assertEquals(at(List(0, 0, 0, 0, 1)), Some("*"))       // literal *
    assertEquals(at(List(0, 0, 0, 0, 2)), Some("b"))       // placeholder b
    assertEquals(at(List(0, 0, 0, 1, 0)), Some("mul(a, b)")) // construct
    assertEquals(at(List(0, 0, 0, 1, 0, 1)), Some("b"))    // argument b
    assertEquals(at(List(0, 0, 0, 2, 0)), Some("b: number")) // constraint
    assertEquals(at(List(0, 0, 0, 2, 0, 0)), Some("b"))    // name leaf
    assertEquals(at(List(0, 0, 0, 2, 0, 1)), Some("number")) // term leaf
  }

  test("text typed in front of a rule keeps the rule's node") {
    val doc = langDefDoc(decodedExprDef)
    val text = textOf(doc)
    val line = text.linesIterator.find(_.contains("a * b means")).get
    val indent = line.takeWhile(_ == ' ')
    val updated = edited(doc,
      text.replace(line, indent + "a // b means div(a, b)\n" + line))
    assertEquals(definitionIds(updated, "a * b"), definitionIds(doc, "a * b"))
    assert(definitionIds(updated, "a // b").nonEmpty)
  }

  test("a literal keeps its node while an original character survives") {
    val doc = langDefDoc(decodedExprDef)
    val updated = edited(doc,
      textOf(doc).replace("a * b means", "a ** b means"))
    assertEquals(literalIds(updated, "**"), literalIds(doc, "*"))
    assertEquals(definitionIds(updated, "a ** b"), definitionIds(doc, "a * b"))
  }

  test("a retyped literal is a new piece; its rule keeps its node") {
    val doc = langDefDoc(decodedExprDef)
    val updated = edited(doc,
      textOf(doc).replace("a * b means", "a // b means"))
    assertEquals(definitionIds(updated, "a // b"), definitionIds(doc, "a * b"))
    val before = literalIds(doc, "*")
    val after = literalIds(updated, "//")
    assert(after.nonEmpty)
    assert(!after.exists(before.contains))
  }

  test("an inserted piece is new while the moved piece keeps its node") {
    val doc = langDefDoc(decodedExprDef)
    val updated = edited(doc,
      textOf(doc).replace("a * b means", "a // b a * b means"))
    assertEquals(literalIds(updated, "*"), literalIds(doc, "*"))
    assert(literalIds(updated, "//").nonEmpty)
    assert(!literalIds(updated, "//").exists(literalIds(doc, "*").contains))
  }
}
