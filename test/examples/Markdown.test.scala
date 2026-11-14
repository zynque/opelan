package opelan.examples

import opelan.examples.markdown._
import opelan.foundation.document._
import opelan.foundation.document.store.Store
import opelan.foundation.document.typed.contentId
import opelan.foundation.language.{applyDocText, docText}
import opelan.foundation.view.ViewTree
import munit.FunSuite

// The markdown language: the means definitions its `lang:` document
// holds, the `blocks`/`runs` productions they parse through, the live
// preview, and store-first resolution of the def document.
class MarkdownSuite extends FunSuite {

  private val source =
    "# Notes\n\n## Groceries\n\n* Apples\n* **Good** bread\n\n" +
      "plain *italic* and **bold** runs\n"

  private def tag(t: DetachedNode[NodeData]): NodeData = t.data

  test("markdown text parses to a block tree") {
    val content = markdownParse(source)
    assertEquals(tag(content), markdownTag(MarkdownTag))
    val Seq(h1, h2, list, para) = content.children
    // Blocks store the surface constructs the definitions declare —
    // `#`, `##`, `*` — each holding its line's `runs`.
    assertEquals(tag(h1), markdownTag(H1Tag))
    assertEquals(tag(h2), markdownTag(H2Tag))
    assertEquals(tag(list), markdownTag(ListTag))
    assertEquals(
      list.children.map(tag),
      List(markdownTag(ItemTag), markdownTag(ItemTag)))
    assertEquals(tag(para), markdownTag(ParaTag))
    // The second item's runs carry a bold span; the paragraph's runs
    // an italic and a bold among plain text leaves.
    val itemRuns = list.children(1).children.flatMap(_.children)
    assert(itemRuns.exists(c => tag(c) == markdownTag(BoldTag)),
      itemRuns.map(tag))
    val spans = para.children.flatMap(_.children).map(tag)
    assert(spans.contains(markdownTag(ItalicTag)), spans)
    assert(spans.contains(markdownTag(BoldTag)), spans)
    assert(spans.exists(_.isInstanceOf[NodeData.StringData]))
  }

  test("an unmatched star stays literal text") {
    val content = markdownParse("2 * 3\n")
    val Seq(para) = content.children
    assertEquals(tag(para), markdownTag(ParaTag))
    // `2 ` a text run, `*` a stray marker kept verbatim, ` ` and `3`
    // a space leaf and a text token — literal text, reassembled.
    assertEquals(
      para.children.flatMap(_.children).map(_.data),
      List(NodeData.StringData("2 "), NodeData.StringData("*"),
        NodeData.StringData(" "), NodeData.StringData("3")))
  }

  test("the def's text surface shows the definitions") {
    // `lang: markdown`'s raw text — the `definitions` the rules spell.
    val text = docText(MarkdownLanguage.langDoc, registryForDoc)
    assert(text.contains("# t means h1(t)"), text)
    assert(text.contains("## t means h2(t)"), text)
    assert(text.contains("* t means item(t)"), text)
    assert(text.contains("**t** means bold(t)"), text)
    assert(text.contains("*t* means italic(t)"), text)
  }

  test("the language's text surface round-trips") {
    val doc = MarkdownLanguage.sampleDoc
    val text = docText(doc, registryForDoc)
    assert(text.contains("# Notes"), text)
    assert(text.contains("* **Good** coffee"), text)
    // A commit of the printed text lands the same content, and an edit
    // keeps the ids of the subtrees it didn't touch.
    val edited = applyDocText(
      doc, registryForDoc, text.replace("Apples", "Pears")).toOption.get
    assertEquals(docText(edited, registryForDoc),
      text.replace("Apples", "Pears"))
    val cid = contentId(doc).get
    val kept = doc.childrenOf(cid).zip(contentId(edited)
      .map(edited.childrenOf(_)).getOrElse(Nil))
      .filter { case (a, b) =>
        doc.getNode(a).map(_.data) == edited.getNode(b).map(_.data) }
    assert(kept.forall { case (a, b) => a == b }, kept)
  }

  test("the edit surface supplies marks and a live preview") {
    assertEquals(MarkdownLanguage.views, List("edit", "preview"))
    val surface = MarkdownLanguage.editSurface.get
    assertEquals(surface.preview, Some("preview"))
    val List(mark) = surface.marks("## Hi *there*")
    assertEquals(mark.kind, "cm-h2")
    assert(mark.spans.contains((0, 2, "cm-marker")), mark.spans)
    assert(mark.spans.exists(_._3 == "cm-italic"), mark.spans)
    assertEquals(
      surface.marks("* item").head.kind, "cm-list")
    assertEquals(
      surface.marks("plain").head.kind, "")
  }

  test("preview renders the formatted document, read-only") {
    val tree = MarkdownLanguage
      .render("preview", MarkdownLanguage.sampleDoc).toOption.get
    assert(hasTag(tree, "styled:h1"), ViewTree.flatten(tree))
    assert(hasTag(tree, "styled:h2"), ViewTree.flatten(tree))
    assert(hasTag(tree, "styled:bold"), ViewTree.flatten(tree))
    assert(hasTag(tree, "styled:italic"), ViewTree.flatten(tree))
    assert(!hasTag(tree, "input"))
  }

  test("a stored lang: markdown doc resolves with the same machinery") {
    val store = Store.empty
      .put(markdownTypeRef.documentUrl, MarkdownLanguage.langDoc)._1
    val l = languageForRef(markdownTypeRef, store).get
    assertEquals(l.name, "Markdown")
    assertEquals(l.views, List("edit", "preview"))
    assert(l.editSurface.isDefined)
    // The text surface and the preview work off the stored def doc.
    assertEquals(
      l.parse("# A").map(_.data), Some(markdownTag(MarkdownTag)))
    assert(l.render("preview", MarkdownLanguage.sampleDoc).isRight)
    val text = docText(MarkdownLanguage.sampleDoc, _ => Some(l))
    assert(text.contains("# Notes"), text)
  }

  private def hasTag(t: DetachedNode[NodeData], tag: String): Boolean =
    t.data == NodeData.StringData(tag) ||
      t.children.exists(hasTag(_, tag))
}
