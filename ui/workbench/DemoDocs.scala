package opelan.ui.workbench

import opelan.foundation.document._
import opelan.examples.{allLanguages, SheetLanguage}

// The demo's opening lineup, seeded into a store that doesn't already
// have it. Each registered language's descriptive document is seeded at
// the URL its typeRef names — so following a document's type ref lands
// on a real node (act 2's "under the hood" beat) — and the budget is
// the app built on them.
//
// Seeding never overwrites: a URL the store already holds is left
// alone, so a returning user's edited documents are safe.
private[workbench] val seedDocs: List[(String, Document[NodeData])] =
  allLanguages.map(l => l.typeRef.documentUrl -> l.langDoc) :+
    (Store.urlForName("Household budget") -> SheetLanguage.sampleDoc)

// The seeds `store` is missing.
private[workbench] def missingSeeds(
    store: Store): List[(String, Document[NodeData])] =
  seedDocs.filter { case (url, _) => store.head(url).isEmpty }
