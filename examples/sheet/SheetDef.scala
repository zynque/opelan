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

// The language's identity ref — the URL its `lang:` document lives at.
val sheetTypeRef = ExternalNodeReference("opelan:languages/sheet", 0, 0)

val sheetDef = LanguageDef(
  typeRef = sheetTypeRef,
  title = "lang: sheet",
  name = "Sheet",
  description = "the spreadsheet language — a grid of cells",
  // The grid's structural terms are placeholders — the def language
  // can't yet declare them in-document, so the registry
  // (foundation/langdef/PlaceholderTerms.scala) anchors each at the
  // opelan:languages/sheet url where its real definition will live.
  // Listed here so the def's declared names resolve to their
  // placeholder refs; they encode as no `vocabulary` section — the
  // ambient layer names them already.
  vocabulary = structuralPlaceholders.map(t =>
    NodeTerm(t.name, t.shape, Some(t.ref))),
  syntax = None,
  views = List(
    ViewBinding("sheet", "sheet-grid", Map("currency" -> "$")),
    ViewBinding("formulas", "sheet-formulas")),
  // Cells parse with the expression language's grammar — by pinned
  // ref, so a new lang: expression version heals sheet cells once the
  // pin bumps to it.
  inputParser = Some(
    InputParserDef.Language(exprTypeRef, InputFallback.Label)))

// The code-side semantics the def names — kept beside it. `refName`
// keeps unparsed text mentioning a cell name a healable gap;
// `commitLink` materializes committed cell names ("D5") as `ref`
// constructs — the reference structure text can't carry.
val sheetImpls = Impls(
  views = Map(
    "sheet-grid"     -> ((doc, id, ctx) => sheetView(doc, id, ctx)),
    "sheet-formulas" -> ((doc, id, ctx) => formulasView(doc, id, ctx))),
  inputParsers = Map("sheet-cell" -> parseCellText),
  refName = Some(isCellRefName),
  commitLink = Some(linkCellRefs))

// Construct identities of the language. Code names a term ("hrow") only
// to look up its vocabulary entry in the canonical encoding; documents
// store the entry's ref, and readers match on it — so a construct
// renamed in its lang: document keeps working (node ids are never
// reused).
lazy val sheetTerms: Map[String, ExternalNodeReference] = termRefs(sheetDef)

// Node data applying a construct, inline — a document imports it when
// the tree is inserted (see makeTyped / internalizeRefs).
def sheetTag(term: String): NodeData = NodeData.ExternalNodeRef(sheetTerms(term))
