package opelan.examples.sheet

import opelan.examples.expr.exprTypeRef
import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc.termRefs

// The sheet language's definition as data — what its `lang:` document
// encodes. `sheetDef` IS the language: SheetLanguage is DefLanguage
// over it, so a stored lang: doc and the registry fallback run the same
// machinery. `langDefDoc` must round-trip it through
// `langDefFromDocument`. The def is paired with `sheetImpls`, the Impls
// bundle holding the code-side semantics it names.
import NodeShape._

// The language's identity ref — the URL its `lang:` document lives at.
val sheetTypeRef = ExternalNodeReference("opelan:languages/sheet", 0, 0)

val sheetDef = LanguageDef(
  typeRef = sheetTypeRef,
  title = "lang: sheet",
  name = "Sheet",
  description = "the spreadsheet language — a grid of cells",
  vocabulary = List(
    NodeTerm("sheet", Any),
    NodeTerm("hrow", Any),
    NodeTerm("row", Any),
    NodeTerm("label", Leaf),
    NodeTerm("literal", Leaf),
    NodeTerm("ref", Leaf),
    NodeTerm("formula", Any)),
  syntax = None,
  views = List(
    ViewBinding("sheet", "sheet-grid", Map("currency" -> "$")),
    ViewBinding("formulas", "sheet-formulas")),
  // Cells parse with the expression language's grammar — by pinned
  // ref, so a new lang: expression version heals sheet cells once the
  // pin bumps to it.
  inputParser = Some(
    InputParserDef.Language(exprTypeRef, InputFallback.Label)))

// The code-side semantics the def names — kept beside it.
val sheetImpls = Impls(
  views = Map(
    "sheet-grid"     -> ((doc, id, ctx) => sheetView(doc, id, ctx)),
    "sheet-formulas" -> ((doc, id, ctx) => formulasView(doc, id, ctx))),
  inputParsers = Map("sheet-cell" -> parseCellText))

// Construct identities of the language. Code names a term ("hrow") only
// to look up its vocabulary entry in the canonical encoding; documents
// store the entry's ref, and readers match on it — so a construct
// renamed in its lang: document keeps working (node ids are never
// reused).
lazy val sheetTerms: Map[String, ExternalNodeReference] = termRefs(sheetDef)

// Node data applying a construct, inline — a document imports it when
// the tree is inserted (see makeTyped / internalizeRefs).
def sheetTag(term: String): NodeData = NodeData.ExternalNodeRef(sheetTerms(term))
