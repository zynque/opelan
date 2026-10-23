package opelan.examples

import opelan.foundation.document._
import opelan.foundation.langdef.termRefs

// Construct identities of the example languages. Code names a term
// ("mul", "hrow") only to look up its vocabulary entry in the language's
// canonical encoding; documents store the entry's ref, and readers match
// on it — so a construct renamed in its lang: document keeps working
// (node ids are never reused).
lazy val exprTerms: Map[String, ExternalNodeReference] = termRefs(exprDef)
lazy val sheetTerms: Map[String, ExternalNodeReference] = termRefs(sheetDef)

// Node data applying a construct, inline — a document imports it when
// the tree is inserted (see makeTyped / internalizeRefs).
def exprTag(term: String): NodeData = NodeData.ExternalNodeRef(exprTerms(term))
def sheetTag(term: String): NodeData = NodeData.ExternalNodeRef(sheetTerms(term))

// Which of `terms` a construct ref names.
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

def termOfData(
    terms: Map[String, ExternalNodeReference], data: NodeData): Option[String] =
  data match {
    case NodeData.ExternalNodeRef(r) => termIn(terms, r)
    case _                           => None
  }
