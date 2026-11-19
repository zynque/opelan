package opelan.examples

import opelan.examples.sheet.{SheetLanguage, sheetDef}
import opelan.examples.expr.{ExprLanguage, demoExprDoc, exprDef}
import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.langdef.{
  DocLanguage, docLanguageDef, langDefTypeRef, ViewBinding}
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
    // `edit` and `summary` are the code-supplied meta surfaces — a
    // stored lang: language doc needn't declare them
    // (DefLanguage.isDocLanguage).
    assertEquals(l.get.views, List("edit", "summary"))
    assert(l.get.render("edit", d).isRight)
    assert(l.get.render("summary", d).isRight)
  }

  test("a meta doc stored under the old view names reads as the new") {
    val legacy = langDefDoc(docLanguageDef.copy(
      views = List(ViewBinding("def", "def-view"))))
    val store = Store.empty.put(langDefTypeRef.documentUrl, legacy)._1
    val l = languageForRef(langDefTypeRef, store).get
    assertEquals(l.views, List("edit", "summary"))
    assert(l.render("edit", legacy).isRight)
    assert(l.render("summary", legacy).isRight)
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
  // the budget accepts the sheet's — the gap heals on the last bump.
  test("a stored gap heals as each layer accepts its pending update") {
    import opelan.foundation.view.ViewTree
    // Seeded like the demo: lang: expression v0 staged without `/`,
    // so every pin to it lands on the staged grammar.
    val staged = List(ExprLanguage, SheetLanguage, DocLanguage)
      .foldLeft(Store.empty)((s, l) => s.put(
        l.typeRef.documentUrl,
        if (l == ExprLanguage) demoExprDoc else l.langDoc)._1)
    val budget = SheetLanguage.sampleDoc
    def perPerson(st: Store, b: Document[NodeData]) =
      languageForDoc(b, st).get
        .render("sheet", b).map(ViewTree.flatten).toOption.get
        .split("\n")(6)
    assert(perPerson(staged, budget).contains("?D5 / B6"), perPerson(staged, budget))
    val sheet = languageForDoc(budget, staged).get
    assertEquals(sheet.parseInput("ref:1 / ref:2").map(_.data),
      Some(NodeData.GapData("ref:1 / ref:2")))
    // Cell names too: unparsed text mentioning one stays a healable
    // gap rather than a label.
    assertEquals(sheet.parseInput("D5 / B6").map(_.data),
      Some(NodeData.GapData("D5 / B6")))
    // Saving `/` as expression v1: pins unchanged — still a gap.
    val fixed = staged.put(
      ExprLanguage.typeRef.documentUrl, ExprLanguage.langDoc)._1
    assert(perPerson(fixed, budget).contains("?D5 / B6"), perPerson(fixed, budget))
    // lang: sheet accepts: its input-grammar ref bumps to expression v1.
    val sheetDoc = fixed.get("opelan:languages/sheet", 0).get
    val s2 = fixed.put(
      "opelan:languages/sheet", acceptUpdates(sheetDoc, fixed).get)._1
    // The budget accepts: type ref and term imports bump — the gap heals.
    val budget2 = acceptUpdates(budget, s2).get
    assertEquals(perPerson(s2, budget2), "per person\t\t\t$8.10")
  }

  // The `a // b` report: an authored alternate notation saved as
  // expression v1 reaches cells only through the same two bumps —
  // the sheet def's input-grammar pin, then the budget's type-ref pin.
  test("an authored alternate notation reaches cells after both pin bumps") {
    import opelan.examples.expr.{demoExprDef, exprRules}
    import opelan.foundation.langdef.SyntaxDef
    import opelan.foundation.means.text.readRuleOrGap
    // Seeded like the workbench: lang: expression v0 is the staged
    // demo def — no `/`, no `div` declared.
    val staged = List(ExprLanguage, SheetLanguage, DocLanguage)
      .foldLeft(Store.empty)((s, l) => s.put(
        l.typeRef.documentUrl,
        if (l == ExprLanguage) demoExprDoc else l.langDoc)._1)
    val budget = SheetLanguage.sampleDoc
    val cell = "D5 // B6"
    def read(st: Store, b: Document[NodeData]) =
      languageForDoc(b, st).get.parseInput(cell)

    // expression v0 knows no `//`: whatever v0 makes of the cell, it
    // is not a clean two-operand construct.
    val before = read(staged, budget)

    // The authored rule saved as expression v1 — pins unchanged.
    val v1 = demoExprDef.copy(syntax = demoExprDef.syntax.map(s =>
      s.copy(rules = s.rules.map(rs => rs.copy(rules =
        rs.rules :+ readRuleOrGap("a // b means div(a, b)"))))))
    val s1 = staged.put(
      ExprLanguage.typeRef.documentUrl, langDefDoc(v1))._1
    assertEquals(read(s1, budget), before)

    // lang: sheet accepts: its input-grammar ref bumps to expression v1.
    val sheetDoc = s1.get("opelan:languages/sheet", 0).get
    val s2 = s1.put("opelan:languages/sheet",
      acceptUpdates(sheetDoc, s1).get)._1

    // The budget accepts: type ref bumps to sheet v1 — `//` now reads.
    val budget2 = acceptUpdates(budget, s2).get
    val after = read(s2, budget2).get
    assert(!after.data.isInstanceOf[NodeData.GapData],
      s"cell still unread: $after")
    assertEquals(after.children.size, 2, s"cell shape: $after")

    // The commit path the workbench exercises: a session started
    // under v0 (lexicon without `//`) must not retext under the stale
    // lexicon — the derived literal set changed, so it restarts.
    import opelan.foundation.language.InputSessions
    def hasGap(n: DetachedNode[NodeData]): Boolean =
      n.data.isInstanceOf[NodeData.GapData] || n.children.exists(hasGap)
    val sheet0 = languageForDoc(budget, s1).get
    val sheet1 = languageForDoc(budget2, s2).get
    val (stale, sessions) =
      sheet0.commitInput(5, cell, InputSessions()).get
    assert(hasGap(stale), s"v0 cell unexpectedly clean: $stale")
    val (committed, _) = sheet1.commitInput(5, cell, sessions).get
    assert(!hasGap(committed),
      s"committed cell still carries a gap: $committed")
    assertEquals(committed.children.size, 2,
      s"committed cell shape: $committed")
  }

  // The workbench report: after both bumps, a cell committed as
  // `D5 // B6` renders as a value — the sheet evaluates (and prints)
  // through the pinned expression def's rules, not the code-side set.
  test("a cell using an authored alternate notation evaluates in the sheet view") {
    import opelan.foundation.document.typed.commitContent
    import opelan.foundation.language.{
      InputSessions, Language, applyDocText, docText}
    import opelan.foundation.view.ViewTree
    val staged = List(ExprLanguage, SheetLanguage, DocLanguage)
      .foldLeft(Store.empty)((s, l) => s.put(
        l.typeRef.documentUrl,
        if (l == ExprLanguage) demoExprDoc else l.langDoc)._1)
    // Authored as the workbench does: Enter at the end of the
    // `(a) means a` line, then the new line typed keystroke by
    // keystroke — every state commits. Node ids of existing rules
    // must survive it: cells reference their declarations by id.
    val langFor: Document[NodeData] => Option[Language] =
      _ => Some(DocLanguage)
    val text = docText(demoExprDoc, langFor)
    val line = text.linesIterator.find(_.contains("(a) means")).get
    val end = text.indexOf(line) + line.length
    val rule = line.takeWhile(_ == ' ') + "a // b means div(a, b)"
    val typed = (0 to rule.length).map(k =>
      text.substring(0, end) + "\n" + rule.take(k) + text.substring(end))
    val v1 = typed.foldLeft(demoExprDoc)((d, t) =>
      applyDocText(d, langFor, t).toOption.get)
    val s1 = staged.put(ExprLanguage.typeRef.documentUrl, v1)._1
    val sheetDoc = s1.get("opelan:languages/sheet", 0).get
    val s2 = s1.put("opelan:languages/sheet",
      acceptUpdates(sheetDoc, s1).get)._1
    val budget = acceptUpdates(SheetLanguage.sampleDoc, s2).get
    val cellId = budget.nodes.indexWhere(n =>
      n != null && n.data == NodeData.GapData("D5 / B6"))
    val sheet = languageForDoc(budget, s2).get
    val (content, _) = sheet.commitInput(cellId, "D5 // B6", InputSessions()).get
    val edited = commitContent(cellId, content, budget).toOption.get
    val rows = sheet.render("sheet", edited).map(ViewTree.flatten)
      .toOption.get.split("\n")
    assertEquals(rows(6), "per person\t\t\t$8.10")
    val formulas = sheet.render("formulas", edited).map(ViewTree.flatten)
      .toOption.get.split("\n")
    assertEquals(formulas(6), "per person\t\t\tD5 // B6")
    assertEquals(formulas(1), "Apples\t4\t1.2\tB2 * C2")
    assertEquals(formulas(4), "total\t\t\tD2 + D3 + D4")
  }

  // Typing at the start of the `a * b` line, then Enter, through the
  // workbench's tracked text surface: the `*` rule keeps its identity
  // throughout, so the budget's `*` cells still read through it.
  test("a rule typed in front of an existing line keeps that line's rule") {
    import opelan.foundation.language.{
      Language, LineIdentity, applyTrackedText, docText}
    import opelan.foundation.view.ViewTree
    val staged = List(ExprLanguage, SheetLanguage, DocLanguage)
      .foldLeft(Store.empty)((s, l) => s.put(
        l.typeRef.documentUrl,
        if (l == ExprLanguage) demoExprDoc else l.langDoc)._1)
    val langFor: Document[NodeData] => Option[Language] =
      _ => Some(DocLanguage)
    val text = docText(demoExprDoc, langFor)
    val line = text.linesIterator.find(_.contains("a * b means")).get
    val indent = line.takeWhile(_ == ' ')
    val at = text.indexOf(line) + indent.length
    val rule = "a // b means div(a, b)"
    val states = (1 to rule.length).map(k =>
      text.substring(0, at) + rule.take(k) + text.substring(at)) :+
      (text.substring(0, at) + rule + "\n" + indent + text.substring(at))
    val (v1, _, _) = states.foldLeft(
        (demoExprDoc, text, Option.empty[LineIdentity])) {
      case ((d, before, identity), t) =>
        val (applied, next) = applyTrackedText(d, langFor, before, identity, t)
        (applied.toOption.get, t, next)
    }
    val s1 = staged.put(ExprLanguage.typeRef.documentUrl, v1)._1
    val sheetDoc = s1.get("opelan:languages/sheet", 0).get
    val s2 = s1.put("opelan:languages/sheet",
      acceptUpdates(sheetDoc, s1).get)._1
    val budget = acceptUpdates(SheetLanguage.sampleDoc, s2).get
    val sheet = languageForDoc(budget, s2).get
    val formulas = sheet.render("formulas", budget).map(ViewTree.flatten)
      .toOption.get.split("\n")
    assertEquals(formulas(1), "Apples\t4\t1.2\tB2 * C2")
    assertEquals(formulas(4), "total\t\t\tD2 + D3 + D4")
    val rows = sheet.render("sheet", budget).map(ViewTree.flatten)
      .toOption.get.split("\n")
    assertEquals(rows(1), "Apples\t4\t1.2\t$4.80")
  }
}
