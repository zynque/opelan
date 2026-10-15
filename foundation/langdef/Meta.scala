package opelan.foundation.langdef

import opelan.foundation.document._

// Meta-construct identity. A def document uses `kind`, `schema`, `op` …
// by importing the schema entries of lang: language, so code recognizes
// them by node id, never by name: these ids come from encoding
// docLanguageDef (the seed of opelan:languages/language), and stay
// valid however the stored entries are renamed — ids are never reused.

// Meta kind name → its schema entry's node id in lang: language.
private[langdef] lazy val metaKindIds: Map[String, Int] =
  encodeDef(docLanguageDef)._2

private lazy val metaNames: Map[Int, String] = metaKindIds.map(_.swap)

// The meta construct a ref names, if it addresses lang: language's
// schema.
private[langdef] def metaNameOf(r: ExternalNodeReference): Option[String] =
  if (r.documentUrl == langDefTypeRef.documentUrl) metaNames.get(r.nodeId)
  else None

// The identity of a meta construct — what a def doc imports to use it.
def metaRef(name: String): Option[ExternalNodeReference] =
  metaRefIn(metaKindIds, name)

// The identity of `title` — the document-structure construct a standard
// document's first child carries. Lives in lang: language's schema, so
// it is imported like every other construct.
def docTitleRef: ExternalNodeReference = metaRef("title").get

// The identity of `imports` — the document-structure construct a
// standard document's second child carries.
def docImportsRef: ExternalNodeReference = metaRef("imports").get

private[langdef] def metaRefIn(
    ids: Map[String, Int], name: String): Option[ExternalNodeReference] =
  ids.get(name).map(ExternalNodeReference(langDefTypeRef.documentUrl, 0, _))

// While a def doc is being built its meta constructs aren't addressable
// yet (lang: language's own entries are being created in the same
// build), so the encoder writes a pending marker — a negative internal
// ref keyed by meta kind — and resolvePending swaps in the real data.
private[langdef] def pending(name: String): NodeData = {
  val i = metaKinds.indexWhere(_.name == name)
  require(i >= 0, s"unknown meta construct: $name")
  NodeData.InternalNodeRef(-(i + 1))
}

private[langdef] def pendingName(d: NodeData): Option[String] = d match {
  case NodeData.InternalNodeRef(n) if n < 0 => metaKinds.lift(-n - 1).map(_.name)
  case _                                    => None
}

private[langdef] def resolvePending(
    doc: Document[NodeData], c: String => NodeData): Document[NodeData] =
  doc.mapData(d => pendingName(d).map(c).getOrElse(d))
