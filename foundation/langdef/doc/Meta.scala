package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._

// Meta-construct identity. A def document uses `term`, `vocabulary`,
// `rule` … as meta placeholders — lang: language's vocabulary anchored
// in the foundation's placeholder registry (PlaceholderTerms.scala) —
// so code recognizes them by ref, never by name: the ids are fixed by
// the registry and stay valid however the eventual stored entries are
// renamed — ids are never reused.

// The meta construct a ref names, if it addresses lang: language's
// (placeholder) vocabulary.
private[langdef] def metaNameOf(r: ExternalNodeReference): Option[String] =
  metaPlaceholders.find(t => sameNode(t.ref, r)).map(_.name)

// The identity of a meta construct — what a def doc imports to use it.
def metaRef(name: String): Option[ExternalNodeReference] =
  metaPlaceholderRefs.get(name)

// The meta construct a node applies, by identity — `None` for any node
// that doesn't import a lang: language vocabulary entry. Public so hosts
// (the typed-doc commit path) can tell meta content from a document's
// own constructs.
def metaConsOf(doc: Document[NodeData], id: Int): Option[String] =
  externalRefOf(doc, id).flatMap(metaNameOf)

// The identity of `title` — the document-structure construct a standard
// document's first child carries. Lives in lang: language's vocabulary, so
// it is imported like every other construct.
def docTitleRef: ExternalNodeReference = metaRef("title").get

// The identity of `imports` — the document-structure construct a
// standard document's second child carries.
def docImportsRef: ExternalNodeReference = metaRef("imports").get

// While a def doc is being built its meta constructs aren't addressable
// yet (lang: language's own entries are being created in the same
// build), so the encoder writes a pending marker — a negative internal
// ref keyed by meta term — and resolvePending swaps in the real data.
private[langdef] def pending(name: String): NodeData = {
  val i = metaTerms.indexWhere(_.name == name)
  require(i >= 0, s"unknown meta construct: $name")
  NodeData.InternalNodeRef(-(i + 1))
}

private[langdef] def pendingName(d: NodeData): Option[String] = d match {
  case NodeData.InternalNodeRef(n) if n < 0 => metaTerms.lift(-n - 1).map(_.name)
  case _                                    => None
}

private[langdef] def resolvePending(
    doc: Document[NodeData], c: String => NodeData): Document[NodeData] =
  doc.mapData(d => pendingName(d).map(c).getOrElse(d))
