package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.means.readRuleOrHole

// Text committed against a meta construct produces the structured
// subtree its decode reads back, never a bare string: a `rule` line
// becomes pattern + meaning nodes, its constructs bound to the doc's
// own vocabulary entries. `None` for a node that isn't meta content —
// an ordinary document input goes through the doc's own input grammar.
def commitMetaInput(
    doc: Document[NodeData], docUrl: String,
    id: Int, text: String): Option[DetachedNode[NodeData]] =
  metaConsOf(doc, id) match {
    case Some("rule") => Some(ruleNode(readRuleOrHole(text),
      vocabularyTermIds(doc, docUrl), pieceIdsFor(doc, id), externalMeta))
    case _ => None
  }

// The doc's own vocabulary as the term name → entry-id map a structured
// meaning encodes against — empty for a doc that isn't a def.
private[langdef] def vocabularyTermIds(
    doc: Document[NodeData], docUrl: String): Map[String, Int] =
  langDefFromDocument(docUrl, doc).vocabulary
    .flatMap(k => k.ref.map(k.name -> _.nodeId)).toMap
