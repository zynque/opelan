package opelan.foundation.means

import opelan.foundation.document.ExternalNodeReference
import opelan.foundation.document.typed.sameNode

// The builtin `where` terms' anchored identities. A `where` clause
// binds a placeholder to a term — `n: number`, `d: blocks` — and the
// binding's identity is the term's ref, so the document stores
// `erl(ref)` and a constraint keeps its meaning however the name is
// displayed or renamed (foundation/langdef/PLAN, *Ref-based `where`-term
// binding*). Impls declare their leaf productions and productions by
// name today; the registry anchors each name so the ref-keyed
// `lexicals` table and the document encoding bind by identity. Ids are
// never reused: new builtin terms append only.
val lexicalTermNames: List[String] = List(
  "gap", "value", "number", "whole number", "int", "float",
  "literal", "ref", "plain", "blocks", "runs", "document")

val lexicalRefs: Map[String, ExternalNodeReference] =
  lexicalTermNames.zipWithIndex.map((name, i) =>
    name -> ExternalNodeReference("opelan:foundation/lexical", 0, i + 1))
    .toMap

// The term a ref names, if it anchors a builtin lexical term — the
// ref→name direction of lexicalRefs, for decoding `erl` constraints.
def lexicalTermOf(ref: ExternalNodeReference): Option[String] =
  lexicalRefs.collectFirst { case (name, r) if sameNode(r, ref) => name }
