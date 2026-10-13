package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.language.{allLanguages, Language, languageForRef => registryForRef}

// Store-first language resolution — the bootstrap step: a language's
// type ref names its definition *document*, and when the store holds a
// doc at that URL typed as a definition (langDefTypeRef) the doc IS the
// language — decoded and interpreted on the spot. Resolution reads the
// store's head version, so saving an edited def doc changes the
// language live; a URL with no def-typed doc falls through to the
// Scala registry, which is also where DocLanguage itself bottoms out.
def languageForRef(
    ref: ExternalNodeReference, store: Store): Option[Language] =
  storedLanguage(ref, store)
    .orElse(
      Option.when(ref.documentUrl == langDefTypeRef.documentUrl &&
        ref.nodeId == langDefTypeRef.nodeId)(DocLanguage))
    .orElse(registryForRef(ref))

// The language of a typed document, resolved through the store first.
def languageForDoc(
    doc: Document[NodeData], store: Store): Option[Language] =
  typeRefOf(doc).flatMap(ref => languageForRef(ref, store))

private def storedLanguage(
    ref: ExternalNodeReference, store: Store): Option[Language] =
  for {
    (_, doc) <- store.head(ref.documentUrl)
    if typeRefOf(doc).contains(langDefTypeRef)
  } yield new DefLanguage(
    langDefFromDocument(ref.documentUrl, doc),
    // The decoded def inherits the impls of the foundational language
    // whose type ref it claims; nested language refs (e.g. a cell
    // grammar's) resolve through the same store.
    ref2 => languageForRef(ref2, store))
