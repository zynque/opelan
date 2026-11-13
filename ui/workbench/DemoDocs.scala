package opelan.ui.workbench

import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.langdef.{placeholderDoc, placeholderUrl}
import opelan.foundation.langdef.doc.langDefDoc
import opelan.examples.allLanguages
import opelan.examples.expr.{demoExprDoc, ExprLanguage}
import opelan.examples.sheet.SheetLanguage

// The demo's opening lineup, seeded into a store that doesn't already
// have it. Each registered language's descriptive document is seeded at
// the URL its typeRef names — so following a document's type ref lands
// on a real node (act 2's "under the hood" beat) — and the two samples
// are the documents built on them. lang: expression is seeded staged,
// without
// `/`, so the budget's per-person gap heals when the audience adds it.
//
// Seeding never overwrites: a URL the store already holds is left
// alone, so a returning user's edited documents are safe.
private[workbench] val seedDocs: List[(String, Document[NodeData])] =
  allLanguages.map(l => l.typeRef.documentUrl -> seedDocOf(l)) :+
    // The placeholder registry — placeholder refs name themselves
    // through it (foundation/langdef/PlaceholderTerms.scala).
    (placeholderUrl -> placeholderDoc) :+
    // The URL slug sorts after household-budget so the budget stays the
    // first-opened document; the doc's own title supplies the sidebar
    // label ("Expression sample").
    (Store.urlForName("Sample expression") -> ExprLanguage.sampleDoc) :+
    (Store.urlForName("Household budget") -> SheetLanguage.sampleDoc)

private def seedDocOf(l: opelan.foundation.language.Language): Document[NodeData] =
  if (l.eq(ExprLanguage)) demoExprDoc else l.langDoc

// The seeds `store` is missing.
private[workbench] def missingSeeds(
    store: Store): List[(String, Document[NodeData])] =
  seedDocs.filter { case (url, _) => store.head(url).isEmpty }
