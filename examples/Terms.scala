package opelan.examples

import opelan.foundation.document._
import opelan.foundation.document.typed._

// Term matching shared by the example languages: whether a construct
// use names a language's term. Code names a term ("mul", "hrow") only
// to look up its vocabulary entry in the language's canonical
// encoding; documents store the entry's ref, and readers match on it —
// so a construct renamed in its lang: document keeps working (node
// ids are never reused).

// Whether `ref` names `term` — matched by identity (`sameNode`: the
// version pin is ignored, like any construct use).
def isTermRef(
    terms: Map[String, ExternalNodeReference], term: String,
    ref: ExternalNodeReference): Boolean =
  terms.get(term).exists(sameNode(_, ref))

// Whether a document node applies `term` (through its import entry).
def isTerm(
    terms: Map[String, ExternalNodeReference], term: String,
    doc: Document[NodeData], id: Int): Boolean =
  externalRefOf(doc, id).exists(isTermRef(terms, term, _))

// The name `ref` stands for in `terms`, when it names one — naming
// for display and tests, never for dispatch (readers match by ref).
def termIn(
    terms: Map[String, ExternalNodeReference],
    ref: ExternalNodeReference): Option[String] =
  terms.collectFirst { case (t, r) if sameNode(r, ref) => t }

// The term a document node applies (through its import entry), or a
// detached node's data names inline.
def termOf(
    terms: Map[String, ExternalNodeReference],
    doc: Document[NodeData], id: Int): Option[String] =
  externalRefOf(doc, id).flatMap(termIn(terms, _))
