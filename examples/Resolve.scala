package opelan.examples

import opelan.foundation.document._
import opelan.foundation.langdef._
import opelan.foundation.language.Language

// The assembled world: the concrete languages the demo knows about.
// DocLanguage is the bootstrap root — it resolves the meta type ref
// that every `lang:` document carries.
val allLanguages: List[Language] = List(ExprLanguage, SheetLanguage, DocLanguage)

// Registry resolution — no store: a language ref finds a code-defined
// language by document URL and node id.
def registryForRef(ref: ExternalNodeReference): Option[Language] =
  allLanguages.find(l =>
    l.typeRef.documentUrl == ref.documentUrl && l.typeRef.nodeId == ref.nodeId)

def registryForDoc(doc: Document[NodeData]): Option[Language] =
  typeRefOf(doc).flatMap(registryForRef)

// Store-first language resolution — the bootstrap step: a language's
// type ref names its definition *document*, and when the store holds a
// doc at that URL typed as a definition (langDefTypeRef) the doc IS the
// language — decoded and interpreted on the spot. Resolution reads the
// pinned version — execution honors `url@version`, so saving an edited
// def doc appends a head without retargeting dependents (a pin bump is
// the accept-update gesture); head is the fallback when the pinned
// version isn't stored. A URL with no def-typed doc falls through to
// the registry, which is also where DocLanguage itself bottoms out.
def languageForRef(
    ref: ExternalNodeReference, store: Store): Option[Language] =
  storedLanguage(ref, store).orElse(registryForRef(ref))

// The language of a typed document, resolved through the store first.
def languageForDoc(
    doc: Document[NodeData], store: Store): Option[Language] =
  typeRefOf(doc).flatMap(ref => languageForRef(ref, store))

// A def interpreted as a live language: impls bound from the
// foundational pair whose type ref the def claims; language refs (an
// input grammar's) resolve through the same store — pinned, so a
// referred language's new version reaches dependents only when their
// refs bump to it.
def languageFromDef(d: LanguageDef, store: Store = Store.empty): Language =
  new DefLanguage(d, implsFor(d), ref => languageForRef(ref, store))

private def storedLanguage(
    ref: ExternalNodeReference, store: Store): Option[Language] =
  for {
    (v, doc) <- pinnedOrHead(ref, store)
    if typeRefOf(doc).contains(langDefTypeRef)
  } yield languageFromDef(langDefFromDocument(ref.documentUrl, doc, v), store)

// The pinned version, falling back to head when the pin isn't stored
// (an older or pruned store) — the URL still names a def-typed doc.
private def pinnedOrHead(
    ref: ExternalNodeReference,
    store: Store): Option[(Int, Document[NodeData])] =
  store.get(ref.documentUrl, ref.documentVersionId)
    .map(ref.documentVersionId -> _)
    .orElse(store.head(ref.documentUrl))
