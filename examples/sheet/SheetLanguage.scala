package opelan.examples.sheet

import opelan.examples.{languageForRef, languageFromDef}
import opelan.examples.expr.exprTag
import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.typed._
import opelan.foundation.document.Detached._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc._

// The spreadsheet language — `sheetDef` interpreted live. The object
// adds only the seeded sample document; rendering, the cell input
// grammar (the expression language, by pinned ref), and input sessions
// come from DefLanguage.
object SheetLanguage extends DefLanguage(
    sheetDef, sheetImpls,
    ref => languageForRef(ref, Store.empty),
    dd => Some(languageFromDef(dd))) {

  // The demo's app: a small household budget. Cells are positional;
  // formula refs are `ref` constructs (`ref > column, row`) resolved
  // against the live grid at read time. `per person` is seeded as a
  // gap holding formula text: it computes once the cell grammar
  // (lang: expression) can read `/`.
  def sampleDoc: Document[NodeData] =
    makeTyped(typeRef,
      er(docTitleRef), er(docImportsRef),
      NodeData.StringData("Household budget"), sheet)

  private def ref(r: Int, c: Int): DetachedNode[NodeData] =
    cellRefTree(r, c)

  private def sheet: DetachedNode[NodeData] =
    n(sheetTag(SheetTag),
      n(sheetTag(HRowTag), sl("item"), sl("qty"), sl("price"), sl("line total")),
      n(sheetTag(RowTag), sl("Apples"), il(4), fl(1.20), n(exprTag("*"), ref(1, 1), ref(1, 2))),
      n(sheetTag(RowTag), sl("Bread"),  il(2), fl(2.50), n(exprTag("*"), ref(2, 1), ref(2, 2))),
      n(sheetTag(RowTag), sl("Coffee"), il(1), fl(6.40), n(exprTag("*"), ref(3, 1), ref(3, 2))),
      n(sheetTag(RowTag), sl("total"), sl(""), sl(""),
        n(exprTag("+"),
          n(exprTag("+"), ref(1, 3), ref(2, 3)),
          ref(3, 3))),
      n(sheetTag(RowTag), sl("people"), il(2), sl(""), sl("")),
      n(sheetTag(RowTag), sl("per person"), sl(""), sl(""),
        DetachedNode.leaf(NodeData.GapData("D5 / B6"))))
}
