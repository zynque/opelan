package opelan.examples

import opelan.foundation.document._
import opelan.foundation.langdef.kindRefs

// Construct identities of the example languages. Code names a kind
// ("mul", "hrow") only to look up its schema entry in the language's
// canonical encoding; documents store the entry's ref, and readers match
// on it — so a construct renamed in its lang: document keeps working
// (node ids are never reused).
lazy val exprKinds: Map[String, ExternalNodeReference] = kindRefs(exprDef)
lazy val sheetKinds: Map[String, ExternalNodeReference] = kindRefs(sheetDef)

// Node data applying a construct, inline — a document imports it when
// the tree is inserted (see makeTyped / internalizeRefs).
def exprTag(kind: String): NodeData = NodeData.ExternalNodeRef(exprKinds(kind))
def sheetTag(kind: String): NodeData = NodeData.ExternalNodeRef(sheetKinds(kind))

// Which of `kinds` a construct ref names.
def kindIn(
    kinds: Map[String, ExternalNodeReference],
    ref: ExternalNodeReference): Option[String] =
  kinds.collectFirst { case (k, r) if sameNode(r, ref) => k }

// The kind a document node applies (through its import entry), or a
// detached node's data names inline.
def kindOf(
    kinds: Map[String, ExternalNodeReference],
    doc: Document[NodeData], id: Int): Option[String] =
  externalRefOf(doc, id).flatMap(kindIn(kinds, _))

def kindOfData(
    kinds: Map[String, ExternalNodeReference], data: NodeData): Option[String] =
  data match {
    case NodeData.ExternalNodeRef(r) => kindIn(kinds, r)
    case _                           => None
  }
