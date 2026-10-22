package opelan.examples

import opelan.foundation.document._
import opelan.foundation.langdef._
import opelan.foundation.language.InputSessions
import opelan.foundation.means.printRule
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
    // The combinator form's operators name the kind they build by
    // internal ref to its schema entry.
    val combinatorDoc = langDefDoc(exprCombinatorDef)
    val mulEntry = kindRefs(withKindRefs(exprCombinatorDef))("mul").nodeId
    assert(combinatorDoc.nodes.exists(
      _.data == NodeData.InternalNodeRef(mulEntry)))
  }

  test("means rules encode as text lines and decode back") {
    val doc = langDefDoc(exprDef)
    val strings = doc.nodes.map(_.data)
      .collect { case NodeData.StringData(t) => t }
    assert(strings.contains("a / b means div(a, b)"), strings)
    assertEquals(
      langDefFromDocument(exprDef.typeRef.documentUrl, doc)
        .syntax.get.rules,
      Some(exprRules))
  }

  test("the def view projects rule lines as inputs bound to their text leaves") {
    val doc = langDefDoc(exprDef)
    val bound = inputSourceIds(defView(doc, contentId(doc).get)).toSet
    // Rules, binding patterns, and example fields are each editable.
    Set("a / b means div(a, b)", "a * b", "1 + 2 * 3", "1 + (2 * 3)")
      .foreach(text => assert(
        bound.contains(stringLeafId(doc, text)),
        s"no input bound to leaf \"$text\""))
  }

  test("a rule line commits through the doc language's input parser") {
    val url = exprDef.typeRef.documentUrl
    val doc = langDefDoc(exprDef)
    val line = stringLeafId(doc, "a / b means div(a, b)")
    val (content, _) = DocLanguage
      .commitInput(line, "a % b means add(a, b)", InputSessions()).get
    val updated = commitContent(line, content, doc).toOption.get
    val d = langDefFromDocument(url, updated)
    assert(d.syntax.get.rules.get.rules.map(r => printRule(r))
      .contains("a % b means add(a, b)"))
    // And the edited language really parses with the new rule.
    val parsed = languageFromDef(d).parse("6 % 4").get
    assertEquals(parsed.data, NodeData.ExternalNodeRef(
      kindRefs(withKindRefs(exprDef))("add")))
  }

  test("a malformed rule line commit lands as a hole rule") {
    val url = exprDef.typeRef.documentUrl
    val doc = langDefDoc(exprDef)
    val line = stringLeafId(doc, "a - b means sub(a, b)")
    val (content, _) =
      DocLanguage.commitInput(line, "means only", InputSessions()).get
    val updated = commitContent(line, content, doc).toOption.get
    val d = langDefFromDocument(url, updated)
    assert(d.syntax.get.rules.get.rules.exists(_.pattern.pieces.isEmpty))
    assert(languageFromDef(d).parse("1 + 2").isDefined)
  }

  // The SourceRef ids of every `input` element in a view tree.
  private def inputSourceIds(t: DetachedNode[NodeData]): List[Int] = t match {
    case DetachedNode(NodeData.StringData("input"), kids) =>
      kids.collect { case DetachedNode(NodeData.SourceRef(id), _) => id } ++
        kids.flatMap(inputSourceIds)
    case _ => t.children.flatMap(inputSourceIds)
  }

  // GapData leaves in a view tree — the hole chips a check reports.
  private def gapChips(t: DetachedNode[NodeData]): List[String] = t match {
    case DetachedNode(NodeData.GapData(s), _) => List(s)
    case _ => t.children.flatMap(gapChips)
  }

  // The live example check needs the doc's stored identity — a def
  // doc's URL is the language it defines — and a way to interpret it.
  private def checkCtx: ViewContext = ViewContext(
    interpretDef = d => Some(languageFromDef(d)),
    docUrl = Some(exprDef.typeRef.documentUrl))

  test("a worked example that holds renders unmarked") {
    val doc = langDefDoc(exprDef)
    val view = defView(doc, contentId(doc).get, checkCtx)
    assert(!gapChips(view).exists(_.startsWith("reads as")))
  }

  test("a worked example that fails marks itself with what it read as") {
    val doc = langDefDoc(exprDef)
    val input = stringLeafId(doc, "1 + 2 * 3")
    val broken = updateNodeData(input, s("1 - 2 * 3"), doc).toOption.get
    val view = defView(broken, contentId(broken).get, checkCtx)
    assertEquals(gapChips(view).filter(_.startsWith("reads as")),
      List("reads as 1 - 2 * 3"))
  }

  test("without an interpretation context examples stay unmarked") {
    // Never Broken: no way to check means no claim of failure.
    val doc = langDefDoc(exprDef)
    val view = defView(doc, contentId(doc).get)
    assert(!gapChips(view).exists(_.startsWith("reads as")))
  }

  private def stringLeafId(doc: Document[NodeData], text: String): Int =
    doc.nodes.indices.find(id => doc.getNode(id)
      .exists(_.data == NodeData.StringData(text))).get

  test("a rule line that doesn't parse decodes as a hole rule") {
    // Never Broken for rules too: garbled text becomes a rule that
    // never applies, and the rest of the set still compiles.
    val doc = langDefDoc(exprDef)
    val ruleText = doc.nodes.indices.find(id =>
      doc.getNode(id).exists(
        _.data == NodeData.StringData("a / b means div(a, b)"))).get
    val broken = updateNodeData(ruleText, s("not a rule"), doc).toOption.get
    val d = langDefFromDocument(exprDef.typeRef.documentUrl, broken)
    assert(d.syntax.get.rules.get.rules.exists(_.pattern.pieces.isEmpty))
    assert(languageFromDef(d).parse("1 + 2").isDefined)
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
