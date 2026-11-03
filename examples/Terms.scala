package opelan.examples

import opelan.foundation.document._
import opelan.foundation.document.typed._

// Term matching shared by the example languages: which of a language's
// construct terms a ref names. Code names a term ("mul", "hrow") only
// to look up its vocabulary entry in the language's canonical encoding;
// documents store the entry's ref, and readers match on it — so a
// construct renamed in its lang: document keeps working (node ids are
// never reused).

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
