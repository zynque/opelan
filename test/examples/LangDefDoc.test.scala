package opelan.examples

import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc._
import opelan.foundation.langdef.syntax._
import opelan.foundation.langdef.view._
import opelan.foundation.language.{InputSessions, TextSession}
import opelan.foundation.means.{Meaning, printRule}
import opelan.foundation.document.Detached._
import munit.FunSuite

// The def-doc format: encoding and reading must round-trip, and the
// seeded lang: docs (ExprLanguage.langDoc / SheetLanguage.langDoc)
// must decode back to the in-memory defs they describe (plus the term
// identities the encoding assigns — withTermRefs).
class LangDefDocSuite extends FunSuite {

  // A meta construct, written inline (makeTyped imports it).
  private def meta(name: String): NodeData = er(metaRef(name).get)

  test("exprDef round-trips through the document encoding") {
    val url = exprDef.typeRef.documentUrl
    val doc = langDefDoc(exprDef)
    assertEquals(langDefFromDocument(url, doc), withTermRefs(exprDef))
  }

  test("constructs are stored by reference, never by name") {
    val doc = langDefDoc(exprDef)
    val strings = doc.nodes.map(_.data).collect { case NodeData.StringData(t) => t }.toSet
    assert(!strings.contains("vocabulary") && !strings.contains("term"), strings)
    // Every meta construct use goes through the imports section.
    val imported = importEntries(doc).map(_._2)
    assert(imported.contains(metaRef("term").get))
    assert(imported.forall(_.documentUrl == langDefTypeRef.documentUrl))
    // The combinator form's operators name the term they build by
    // internal ref to its vocabulary entry.
    val combinatorDoc = langDefDoc(exprCombinatorDef)
    val mulEntry = termRefs(withTermRefs(exprCombinatorDef))("mul").nodeId
    assert(combinatorDoc.nodes.exists(
      _.data == NodeData.InternalNodeRef(mulEntry)))
  }

  test("means rules encode as structure and decode back") {
    // Each `rule` node carries `pattern` + `meaning` sections — the
    // line's text is a projection, not the storage.
    val doc = langDefDoc(exprDef)
    val ruleIds = ruleNodeIds(doc)
    assertEquals(ruleIds.length, exprRules.rules.length)
    ruleIds.foreach { id =>
      val parts = doc.childrenOf(id).flatMap(metaConsOf(doc, _))
      assert(parts.contains("pattern") && parts.contains("meaning"),
        parts.mkString(","))
    }
    assertEquals(
      langDefFromDocument(exprDef.typeRef.documentUrl, doc)
        .syntax.get.rules,
      withTermRefs(exprDef).syntax.get.rules)
  }

  test("rule meanings reference their vocabulary entries by node identity") {
    // `a / b means div(a, b)` decodes to a construct whose reference
    // is the `div` term entry's node — the by-reference encoding.
    val doc = langDefDoc(exprDef)
    val d = langDefFromDocument(exprDef.typeRef.documentUrl, doc)
    val div = d.syntax.get.rules.get.rules
      .find(_.meaning.tag == "div").get.meaning
    assertEquals(div.reference,
      Some(termRefs(withTermRefs(exprDef))("div")))
    assertEquals(div.arguments.map(_.tag), List("use", "use"))
  }

  test("renaming a term entry renames the meanings that use it") {
    // The meaning's data is the entry's ref, never the name — so the
    // decoded tag follows a rename (same healing as document uses).
    val doc = langDefDoc(exprDef)
    val divId = termRefs(withTermRefs(exprDef))("div").nodeId
    val renamed = updateNodeData(
      doc.childrenOf(divId).head, s("quotient"), doc).toOption.get
    val d = langDefFromDocument(exprDef.typeRef.documentUrl, renamed)
    assert(d.syntax.get.rules.get.rules
      .exists(_.meaning.tag == "quotient"))
  }

  test("renaming a pattern placeholder renames its meaning uses") {
    // `use` operands point at the piece node, so the name they decode
    // to follows the placeholder's name leaf.
    val doc = langDefDoc(exprDef)
    val url = exprDef.typeRef.documentUrl
    val div = langDefFromDocument(url, doc)
      .syntax.get.rules.get.rules.find(_.meaning.tag == "div").get
    // The operand `a` of div(a, b) carries the `a` piece's node id.
    val pieceId = div.meaning.arguments.head
      .arguments.head.reference.get.nodeId
    val renamed = updateNodeData(
      doc.childrenOf(pieceId).head, s("x"), doc).toOption.get
    val useA = langDefFromDocument(url, renamed)
      .syntax.get.rules.get.rules.find(_.meaning.tag == "div").get
      .meaning.arguments.head
    assertEquals(useA.arguments.flatMap(Meaning.nameOf), List("x"))
  }

  test("decoded rules carry their node ids") {
    val doc = langDefDoc(exprDef)
    val rules = langDefFromDocument(exprDef.typeRef.documentUrl, doc)
      .syntax.get.rules.get.rules
    assert(rules.forall(_.id.isDefined))
  }

  test("a placeholder rename keeps the session — node ids survive renames") {
    val url = exprDef.typeRef.documentUrl
    val doc = langDefDoc(exprDef)
    val d0 = langDefFromDocument(url, doc)
    // The `(a)` rule's `use` operand points at its placeholder piece —
    // rename the piece's name leaf, as the use-rename test does.
    val parens = d0.syntax.get.rules.get.rules
      .find(_.meaning.tag == "use").get
    val pieceId = parens.meaning.arguments.head.reference.get.nodeId
    val renamed = updateNodeData(
      doc.childrenOf(pieceId).head, s("x"), doc).toOption.get
    val d1 = langDefFromDocument(url, renamed)
    // Same node id, consistently renamed content — the compiled
    // parser is identical, so nothing invalidates.
    assertEquals(syntaxChanges(d0.syntax.get, d1.syntax.get),
      Set.empty[String])
    val text = "(7 + 2)"
    val s0 = TextSession.start(languageFromDef(d0).textSyntax.get, text)
    val s1 = s0.retext(languageFromDef(d1).textSyntax.get, text)
    val fresh = TextSession.start(languageFromDef(d1).textSyntax.get, text)
    assert(s1.evaluations < fresh.evaluations,
      s"${s1.evaluations} vs ${fresh.evaluations}")
    assertEquals(Some(s1.content), languageFromDef(d1).parse(text))
  }

  test("the def view projects rule lines as inputs bound to the rule nodes") {
    val doc = langDefDoc(exprDef)
    val bound = inputSourceIds(defView(doc, contentId(doc).get)).toSet
    // A rule's input binds to the rule node itself — a commit
    // rewrites it as pattern + meaning nodes; binding patterns and
    // example fields still bind to their text leaves.
    ruleNodeIds(doc).foreach(id =>
      assert(bound.contains(id), s"no input bound to rule node $id"))
    Set("a * b", "1 + 2 * 3", "1 + (2 * 3)")
      .foreach(text => assert(
        bound.contains(stringLeafId(doc, text)),
        s"no input bound to leaf \"$text\""))
  }

  test("a rule line commits as structured nodes through commitMetaInput") {
    val url = exprDef.typeRef.documentUrl
    val doc = langDefDoc(exprDef)
    val ruleId = ruleNodeIds(doc)(2) // a / b means div(a, b)
    val content = commitMetaInput(doc, url, ruleId,
      "a % b means add(a, b)").get
    val updated = commitContent(ruleId, content, doc).toOption.get
    val d = langDefFromDocument(url, updated)
    assert(d.syntax.get.rules.get.rules.map(r => printRule(r))
      .contains("a % b means add(a, b)"))
    // And the edited language really parses with the new rule.
    val parsed = languageFromDef(d).parse("6 % 4").get
    assertEquals(parsed.data, NodeData.ExternalNodeRef(
      termRefs(withTermRefs(exprDef))("add")))
    // The construct committed by node ref to the vocabulary entry.
    val addId = termRefs(withTermRefs(exprDef))("add").nodeId
    assert(updated.subtreeIds(ruleId).exists(k =>
      updated.getNode(k).exists(
        _.data == NodeData.InternalNodeRef(addId))))
    // And `use` operands point at the pattern's piece nodes — the
    // `a` placeholder's node kept its id through the commit.
    val aLeaf = updated.subtreeIds(ruleId).find(k =>
      updated.getNode(k).exists(
        _.data == NodeData.StringData("a"))).get
    val aPieceId = updated.getNode(aLeaf).get.parentId.get
    assert(updated.subtreeIds(ruleId).exists(k =>
      updated.getNode(k).exists(
        _.data == NodeData.InternalNodeRef(aPieceId))))
  }

  test("a malformed rule line commit lands as a hole rule") {
    val url = exprDef.typeRef.documentUrl
    val doc = langDefDoc(exprDef)
    val ruleId = ruleNodeIds(doc).last // any rule slot serves the commit
    val content =
      commitMetaInput(doc, url, ruleId, "means only").get
    val updated = commitContent(ruleId, content, doc).toOption.get
    val d = langDefFromDocument(url, updated)
    assert(d.syntax.get.rules.get.rules.exists(_.pattern.pieces.isEmpty))
    assert(languageFromDef(d).parse("1 + 2").isDefined)
  }

  test("a phrase meaning commits desugared — terms, not reparse") {
    val url = exprDef.typeRef.documentUrl
    val doc = langDefDoc(exprDef)
    val ruleId = ruleNodeIds(doc)(4) // a - b means sub(a, b)
    val content = commitMetaInput(doc, url, ruleId,
      "a % means a / 100", implsFor(exprDef)).get
    val updated = commitContent(ruleId, content, doc).toOption.get
    val rule = langDefFromDocument(url, updated)
      .syntax.get.rules.get.rules(4)
    // The phrase parsed through the language's own rules at entry —
    // the stored meaning is the desugared term tree.
    assertEquals(rule.meaning.tag, "div")
    assertEquals(rule.meaning.arguments.map(_.tag), List("use", "quote"))
    // And the def view projects the authored spelling back through
    // the language's own printer, not the constructor's.
    val view = defView(updated, contentId(updated).get)
    val text = opelan.foundation.view.ViewTree.flatten(view)
    assert(text.contains("a % means a / 100"), text)
  }

  test("a phrase the set can't read stays reparse and heals on arrival") {
    val url = demoExprDef.typeRef.documentUrl
    val doc = langDefDoc(demoExprDef) // no `/` rule yet
    val pctId = ruleNodeIds(doc)(1)   // a * b means mul(a, b)
    val pct = commitMetaInput(doc, url, pctId,
      "a % means a / 100", implsFor(demoExprDef)).get
    val withPct = commitContent(pctId, pct, doc).toOption.get
    // The unparseable phrase keeps its reparse form — the transitional
    // storage that still heals — and reads back as authored.
    val pctRule = langDefFromDocument(url, withPct)
      .syntax.get.rules.get.rules(1)
    assertEquals(pctRule.meaning.tag, "reparse")
    assertEquals(printRule(pctRule), "a % means a / 100")
    // Committing `/` into another slot gives the phrase its rule; the
    // interpretation-time desugar retries — the stored reparse
    // produces real terms and `5 %` parses as div(5, 100).
    val subId = ruleNodeIds(withPct)(3) // a - b means sub(a, b)
    val withDiv = commitContent(subId,
      commitMetaInput(withPct, url, subId,
        "a / b means div(a, b)", implsFor(demoExprDef)).get,
      withPct).toOption.get
    val d2 = langDefFromDocument(url, withDiv)
    val parsed = languageFromDef(d2).parse("5 %").get
    assertEquals(parsed.data,
      NodeData.ExternalNodeRef(termRefs(d2)("div")))
    assertEquals(parsed.children.map(_.data),
      List(NodeData.IntData(5), NodeData.IntData(100)))
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

  private def ruleNodeIds(doc: Document[NodeData]): List[Int] =
    doc.nodes.indices.filter(id =>
      !isImportEntry(doc, id) &&
        metaConsOf(doc, id).contains("rule")).toList

  test("a rule node missing its pattern decodes as a hole rule") {
    // Never Broken for rules too: a malformed rule becomes a rule
    // that never applies, and the rest of the set still compiles.
    val doc = langDefDoc(exprDef)
    val ruleId = ruleNodeIds(doc)(2) // a / b means div(a, b)
    val broken = updateNodeChildIds(
      ruleId, doc.childrenOf(ruleId).tail, doc).toOption.get
    val d = langDefFromDocument(exprDef.typeRef.documentUrl, broken)
    assert(d.syntax.get.rules.get.rules.exists(_.pattern.pieces.isEmpty))
    assert(languageFromDef(d).parse("1 + 2").isDefined)
  }

  test("a text-form rule still decodes — the transitional encoding") {
    // A `rule` node whose first child is a string reads through
    // readRuleOrHole, as docs written before the structured form did.
    val doc = langDefDoc(exprDef)
    val ruleId = ruleNodeIds(doc)(2)
    val texty = commitContent(ruleId,
      n(meta("rule"), sl("a / b means div(a, b)")), doc).toOption.get
    val d = langDefFromDocument(exprDef.typeRef.documentUrl, texty)
    assert(d.syntax.get.rules.get.rules.map(r => printRule(r))
      .contains("a / b means div(a, b)"))
  }

  test("renaming a construct's definition renames its uses, decoding unaffected") {
    // Rename the meta `term` entry in lang: language. Def docs use the
    // entry by id, so decoding ignores the rename and the projected
    // name of every use follows it.
    val termEntry = metaRef("term").get.nodeId
    val metaDoc = DocLanguage.langDoc
    val nameLeaf = metaDoc.childrenOf(termEntry).head
    val renamed = updateNodeData(nameLeaf, s("construct"), metaDoc).toOption.get
    val store = Store.empty.put(langDefTypeRef.documentUrl, renamed)._1
    val doc = langDefDoc(exprDef)
    val termUse = doc.nodes.indices.find(id =>
      !isImportEntry(doc, id) && externalRefOf(doc, id).contains(metaRef("term").get)).get
    assertEquals(refName(store, doc, termUse), Some("construct"))
    assertEquals(
      langDefFromDocument(exprDef.typeRef.documentUrl, doc).vocabulary.map(_.name),
      exprDef.vocabulary.map(_.name))
  }

  test("sheetDef round-trips, including the input language ref") {
    val url = sheetDef.typeRef.documentUrl
    val doc = langDefDoc(sheetDef)
    val back = langDefFromDocument(url, doc)
    assertEquals(back, withTermRefs(sheetDef))
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
      withTermRefs(exprDef))
    assertEquals(
      langDefFromDocument("opelan:languages/sheet",
        SheetLanguage.langDoc),
      withTermRefs(sheetDef))
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
