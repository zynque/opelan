package opelan.examples

import opelan.examples.sheet.{SheetLanguage, sheetDef}
import opelan.examples.expr.{ExprLanguage, demoExprDef, exprDef, exprImpls, exprRules}
import opelan.foundation.document._
import opelan.foundation.grammar.RuleName
import opelan.foundation.document.store._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc._
import opelan.foundation.langdef.syntax._
import opelan.foundation.langdef.view._
import opelan.foundation.language.{InputSessions, TextSession}
import opelan.foundation.means.Meaning
import opelan.foundation.means.engine.{foundationRefs, foundationalOf}
import opelan.foundation.means.text.{printPattern, printRule, readRuleOrGap}
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
    assert(imported.contains(metaRef("rule").get))
    assert(imported.forall(r => Set(langDefTypeRef.documentUrl,
      "opelan:foundation/arithmetic",
      "opelan:foundation/placeholders")(r.documentUrl)))
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

  test("foundational imports decode to the foundation's refs without a section") {
    // A rule-bearing def writes no `vocabulary` section for the
    // terms the ambient layer already names — they resolve through
    // ambientRefs/ambientTermOf, so a decoded `add` IS the
    // foundational `add`, no stored entries involved.
    val doc = langDefDoc(exprDef)
    assert(
      !doc.nodes.indices.exists(metaConsOf(doc, _).contains("vocabulary")))
    val d = langDefFromDocument(exprDef.typeRef.documentUrl, doc)
    // Ambient refs never appear in the decoded vocabulary — the
    // rules' meanings still decode to the foundational tags.
    assert(d.vocabulary.flatMap(_.ref)
      .forall(r => foundationalOf(r).isEmpty))
    assertEquals(
      d.syntax.get.rules.get.rules
        .filter(_.meaning.tag == "add").map(_.meaning.reference),
      List(Some(foundationRefs("add"))))
    // The unused `div` stays referenceable — the audience can add
    // `a / b means div(a, b)` and the name resolves through the
    // ambient registry.
    assertEquals(ambientRefs("div"), foundationRefs("div"))
  }

  test("a committed construct name heals to the def's import") {
    // A meaning landing as a bare `add` — written without its
    // reference — relinks to the foundation's term through the
    // ambient floor, the link `linkCommitted` applies after a
    // text-surface commit.
    val doc = langDefDoc(exprDef)
    val cid = contentId(doc).get
    val addUse = doc.subtreeIds(cid)
      .filter(id => metaConsOf(doc, id).contains("meaning"))
      .flatMap(doc.childrenOf)
      .find(id => externalRefOf(doc, id)
        .contains(foundationRefs("add"))).get
    val named = updateNodeData(addUse, s("add"), doc).toOption.get
    val healed = DocLanguage.linkCommitted(named, cid)
    assertEquals(healed.getNode(addUse).map(_.data),
      Some(NodeData.ExternalNodeRef(foundationRefs("add"))))
  }

  test("rule meanings reference their definitions by node identity") {
    // `a / b means div(a, b)` decodes to a construct whose reference
    // is the foundational `div` entry — the by-reference encoding.
    val doc = langDefDoc(exprDef)
    val d = langDefFromDocument(exprDef.typeRef.documentUrl, doc)
    val div = d.syntax.get.rules.get.rules
      .find(_.meaning.tag == "div").get.meaning
    assertEquals(div.reference, Some(foundationRefs("div")))
    assertEquals(div.arguments.map(_.tag), List("use", "use"))
  }

  test("renaming a term entry renames the meanings that use it") {
    // The meaning's data is the entry's ref, never the name — so the
    // decoded tag follows a rename (same healing as document uses).
    // `pair` is locally declared (its pattern is a construct
    // application), so it has a renameable local entry — the
    // arithmetic terms are foundational references, not local terms.
    val localRules = exprRules.copy(rules =
      exprRules.rules :+ readRuleOrGap("pair(a, b) means pair(a, b)"))
    val local = exprDef.copy(
      vocabulary = exprDef.vocabulary :+
        NodeTerm("pair", NodeShape.Fixed(2)),
      syntax = exprDef.syntax.map(s => s.copy(rules = Some(localRules))))
    val doc = langDefDoc(local)
    val pairId = termRefs(withTermRefs(local))("pair").nodeId
    val renamed = updateNodeData(pairId, s("couple"), doc).toOption.get
    val d = langDefFromDocument(
      exprDef.typeRef.documentUrl, renamed)
    assert(d.syntax.get.rules.get.rules
      .exists(_.meaning.tag == "couple"))
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
    val parentheses = d0.syntax.get.rules.get.rules
      .find(_.meaning.tag == "use").get
    val pieceId = parentheses.meaning.arguments.head.reference.get.nodeId
    val renamed = updateNodeData(
      doc.childrenOf(pieceId).head, s("x"), doc).toOption.get
    val d1 = langDefFromDocument(url, renamed)
    // Same node id, consistently renamed content — the compiled
    // parser is identical, so nothing invalidates.
    assertEquals(syntaxChanges(d0.syntax.get, d1.syntax.get),
      Set.empty[RuleName])
    val text = "(7 + 2)"
    val s0 = TextSession.start(languageFromDef(d0).textSyntax.get, text)
    val s1 = s0.retext(languageFromDef(d1).textSyntax.get, text)
    val fresh = TextSession.start(languageFromDef(d1).textSyntax.get, text)
    assert(s1.evaluations < fresh.evaluations,
      s"${s1.evaluations} vs ${fresh.evaluations}")
    assertEquals(Some(s1.content), languageFromDef(d1).parse(text))
  }

  test("the edit view projects rule fields as inputs bound to their nodes") {
    val doc = langDefDoc(exprDef)
    val bound = inputSourceIds(defView(doc, contentId(doc).get)).toSet
    // A structured rule binds pattern and meaning inputs to their own
    // nodes — each field commits separately; binding patterns and
    // example fields still bind to their text leaves.
    ruleNodeIds(doc).foreach(id => doc.childrenOf(id).foreach(k =>
      assert(metaConsOf(doc, k).forall(c => !Set("pattern", "meaning")(c) ||
        bound.contains(k)), s"no input bound to rule section $k")))
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
      "a % b means add(a, b)").get._1
    val updated = commitContent(ruleId, content, doc).toOption.get
    val d = langDefFromDocument(url, updated)
    assert(d.syntax.get.rules.get.rules.map(r => printRule(r))
      .contains("a % b means add(a, b)"))
    // And the edited language really parses with the new rule.
    val parsed = languageFromDef(d).parse("6 % 4").get
    assertEquals(parsed.data, NodeData.ExternalNodeRef(
      foundationRefs("add")))
    // The committed construct stores its foundational reference —
    // meanings never hold bare names.
    assert(updated.subtreeIds(ruleId).exists(k =>
      updated.getNode(k).exists(
        _.data == NodeData.ExternalNodeRef(foundationRefs("add")))))
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

  test("a meaning field commits as terms through the language's own rules") {
    val url = exprDef.typeRef.documentUrl
    val doc = langDefDoc(exprDef)
    val ruleId = ruleNodeIds(doc)(4) // a - b means sub(a, b)
    val meaningId = doc.childrenOf(ruleId)
      .find(metaConsOf(doc, _).contains("meaning")).get
    val patternId = doc.childrenOf(ruleId)
      .find(metaConsOf(doc, _).contains("pattern")).get
    val content = commitMetaInput(doc, url, meaningId,
      "a / 100", impls = implsFor(exprDef)).get._1
    val updated = commitContent(meaningId, content, doc).toOption.get
    val rule = langDefFromDocument(url, updated)
      .syntax.get.rules.get.rules(4)
    // The phrase parsed at entry: construct term by ref, `a` as use,
    // the literal `100` quoted — and the rule's pattern is untouched.
    assertEquals(rule.meaning.tag, "div")
    assertEquals(rule.meaning.arguments.map(_.tag), List("use", "quote"))
    assertEquals(printPattern(rule.pattern), "a - b")
    // The `use` operand links to the pattern's `a` piece node.
    val aPiece = doc.childrenOf(patternId)
      .find(id => metaConsOf(doc, id).contains("placeholder") &&
        doc.childrenOf(id).exists(k =>
          doc.getNode(k).exists(_.data == NodeData.StringData("a")))).get
    assertEquals(rule.meaning.arguments(0).arguments.head.reference,
      Some(ExternalNodeReference(url, 0, aPiece)))
    // And the language really parses with the stored meaning —
    // the pattern is still `a - b`, so `5 - 3` now builds a div.
    val parsed = languageFromDef(
      langDefFromDocument(url, updated)).parse("5 - 3").get
    assertEquals(parsed.data,
      NodeData.ExternalNodeRef(foundationRefs("div")))
  }

  test("a meaning field re-edit reparses through its input session") {
    val url = exprDef.typeRef.documentUrl
    val doc = langDefDoc(exprDef)
    val meaningId = doc.childrenOf(ruleNodeIds(doc)(4))
      .find(metaConsOf(doc, _).contains("meaning")).get
    val (c0, s0) = commitMetaInput(doc, url, meaningId,
      "a / 100", impls = implsFor(exprDef)).get
    assert(s0.sessions.contains(meaningId))
    // A second commit carries the session — re-edit reparses in
    // proportion to the keystroke.
    val (c1, s1) = commitMetaInput(doc, url, meaningId,
      "a / 200", s0, implsFor(exprDef)).get
    assert(s1.sessions.contains(meaningId))
    val updated = commitContent(meaningId, c1, doc).toOption.get
    val rule = langDefFromDocument(url, updated)
      .syntax.get.rules.get.rules(4)
    assertEquals(rule.meaning.arguments(1).quoted.map(_.data),
      Some(NodeData.IntData(200)))
  }

  test("a malformed rule line commit lands as a gap rule") {
    val url = exprDef.typeRef.documentUrl
    val doc = langDefDoc(exprDef)
    val ruleId = ruleNodeIds(doc).last // any rule slot serves the commit
    val content =
      commitMetaInput(doc, url, ruleId, "means only").get._1
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
      "a % means a / 100", impls = implsFor(exprDef)).get._1
    val updated = commitContent(ruleId, content, doc).toOption.get
    val rule = langDefFromDocument(url, updated)
      .syntax.get.rules.get.rules(4)
    // The phrase parsed through the language's own rules at entry —
    // the stored meaning is the desugared term tree.
    assertEquals(rule.meaning.tag, "div")
    assertEquals(rule.meaning.arguments.map(_.tag), List("use", "quote"))
    // And the edit view projects the authored spelling back through
    // the language's own printer, not the constructor's.
    val view = defView(updated, contentId(updated).get)
    val text = opelan.foundation.view.ViewTree.flatten(view)
    assert(text.contains("a % means a / 100"), text)
  }

  test("a phrase the set can't read stays a quoted gap and heals on arrival") {
    val url = demoExprDef.typeRef.documentUrl
    val doc = langDefDoc(demoExprDef) // no `/` rule yet
    val pctId = ruleNodeIds(doc)(1)   // a * b means mul(a, b)
    val pct = commitMetaInput(doc, url, pctId,
      "a % means a / 100", impls = implsFor(demoExprDef)).get._1
    val withPct = commitContent(pctId, pct, doc).toOption.get
    // The unparseable phrase stores as a quoted gap — `a / 100`
    // keeps its text and prints back marked as a gap.
    val pctRule = langDefFromDocument(url, withPct)
      .syntax.get.rules.get.rules(1)
    assertEquals(pctRule.meaning.tag, "quote")
    assertEquals(printRule(pctRule), "a % means a / 100")
    // Committing `/` into another slot gives the phrase its rule; the
    // interpretation-time rephrase retries — the stored gap produces
    // real terms and `5 %` parses as div(5, 100).
    val subId = ruleNodeIds(withPct)(3) // a - b means sub(a, b)
    val withDiv = commitContent(subId,
      commitMetaInput(withPct, url, subId,
        "a / b means div(a, b)", impls = implsFor(demoExprDef)).get._1,
      withPct).toOption.get
    val d2 = langDefFromDocument(url, withDiv)
    val parsed = languageFromDef(d2).parse("5 %").get
    assertEquals(parsed.data,
      NodeData.ExternalNodeRef(foundationRefs("div")))
    assertEquals(parsed.children.map(_.data),
      List(NodeData.IntData(5), NodeData.IntData(100)))
  }

  // The SourceRef ids of every `input` element in a view tree.
  private def inputSourceIds(t: DetachedNode[NodeData]): List[Int] = t match {
    case DetachedNode(NodeData.StringData("input"), children) =>
      children.collect { case DetachedNode(NodeData.SourceRef(id), _) => id } ++
        children.flatMap(inputSourceIds)
    case _ => t.children.flatMap(inputSourceIds)
  }

  // GapData leaves in a view tree — the gap chips a check reports.
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

  test("a rule node missing its pattern decodes as a gap rule") {
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
    // readRuleOrGap, as docs written before the structured form did.
    val doc = langDefDoc(exprDef)
    val ruleId = ruleNodeIds(doc)(2)
    val texty = commitContent(ruleId,
      n(meta("rule"), sl("a / b means div(a, b)")), doc).toOption.get
    val d = langDefFromDocument(exprDef.typeRef.documentUrl, texty)
    assert(d.syntax.get.rules.get.rules.map(r => printRule(r))
      .contains("a / b means div(a, b)"))
  }

  test("meta constructs are placeholders — refs name themselves") {
    // `rule`/`meaning`/… are placeholder terms (PlaceholderTerms):
    // a def doc stores their placeholder refs, and both directions —
    // metaNameOf at decode, ambientRefs at commit — run through the
    // registry, not through a stored vocabulary entry.
    val doc = langDefDoc(exprDef)
    val ruleIds = doc.nodes.indices.filter(id =>
      !isImportEntry(doc, id) &&
      externalRefOf(doc, id).contains(metaRef("rule").get))
    assert(ruleIds.nonEmpty)
    assert(ruleIds.forall(id => metaConsOf(doc, id).contains("rule")))
    assertEquals(
      langDefFromDocument(exprDef.typeRef.documentUrl, doc).vocabulary.map(_.name),
      exprDef.vocabulary.map(_.name))
  }

  test("sheetDef round-trips, including the input language ref") {
    val url = sheetDef.typeRef.documentUrl
    val doc = langDefDoc(sheetDef)
    val back = langDefFromDocument(url, doc)
    // The grid terms are ambient placeholders — with no `vocabulary`
    // section at all the decoded vocabulary holds only what rules
    // declare, and a rule-less def declares nothing.
    assertEquals(back, withTermRefs(sheetDef).copy(vocabulary = List()))
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
      withTermRefs(sheetDef).copy(vocabulary = List()))
  }

  test("the def text surface's line marks paint what each line read as") {
    val doc = langDefDoc(exprDef)
    val text = defDocLines(
      doc, exprDef.typeRef.documentUrl, contentId(doc).get).mkString("\n")
    val marks = defDocLineMarks(text)
    assertEquals(marks.length, text.split("\n", -1).length)
    val byLine = text.split("\n", -1).zip(marks)
    def kindOf(prefix: String) =
      byLine.collectFirst {
        case (l, m) if l.trim.startsWith(prefix) => m.kind }
    assertEquals(kindOf("definition"), Some("cm-meta"))
    assertEquals(kindOf("syntax"), Some("cm-meta"))
    assertEquals(kindOf("\"expr\""), Some("cm-string"))
    assertEquals(kindOf("a + b"), Some("cm-rule"))
    // A rule line's spans mark `means` as a keyword and its
    // placeholders — offsets relative to the line, not the trimmed text.
    val rule = byLine.find(_._1.trim.startsWith("a + b means")).get
    assert(rule._2.spans.exists { case (_, _, c) => c == "cm-keyword" })
    assert(rule._2.spans.exists { case (f, t, c) =>
      c == "cm-placeholder" && rule._1.substring(f, t) == "a" })
  }

  test("failing rule lines and bare words mark as gap and unmatched") {
    val text = "definition\n  syntax\n    \"expr\"\n    rules\n" +
      "      a + b means add(a, b)\n      total garbage here\n"
    val marks = defDocLineMarks(text)
    assertEquals(marks(4).kind, "cm-rule")
    assertEquals(marks(5).kind, "cm-gap")
    // Outside `rules`, a bare word the meta vocabulary doesn't declare
    // paints as unmatched (it commits as a string — not a pattern), a
    // meta word as a construct line, a `ref:` payload as a reference.
    val outside = defDocLineMarks(
      "definition\n  vocabulary\n    ref:opelan:foundation/arithmetic@0#1\n    bogusword\n")
    assertEquals(outside(1).kind, "cm-meta")
    assertEquals(outside(2).kind, "cm-ref")
    assertEquals(outside(3).kind, "cm-unmatched")
  }

  test("encoding is deterministic") {
    assertEquals(
      render(langDefDoc(exprDef)),
      render(langDefDoc(exprDef)))
  }

  test("a damaged def doc still decodes — unknown grammar nodes become gaps") {
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
    assertEquals(d.syntax.map(_.rules), Some(None))
    // A rule-less syntax parses nothing — total, not thrown.
    assertEquals(
      languageFromDef(d).parse("1 + 2").map(_.data),
      Some(NodeData.GapData("1 + 2")))
  }

  test("a cyclic document decodes without looping") {
    // A malformed Document can list a node as its own descendant —
    // node 3's only child is itself.
    val cyc: Document[NodeData] = Document(0, Vector(
      Node(0, NodeData.StringData("lang: cyc"), List(1), None),
      Node(0, meta("syntax"), List(2, 3), Some(0)),
      Node(0, NodeData.StringData("expr"), Nil, Some(1)),
      Node(0, meta("term"), List(3), Some(1))))
    val d = langDefFromDocument("opelan:docs/cyc", cyc)
    assertEquals(d.syntax.map(_.rules), Some(None))
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
