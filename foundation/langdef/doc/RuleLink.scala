package opelan.foundation.langdef.doc

import opelan.foundation.document._

// Point each meaning's `use`/`source`/`builtin`/`literal` operand at
// the placeholder piece node it names — the rules counterpart of
// linkUses for grammar nodes. The operand decodes through the piece's
// projected name leaf, so a placeholder rename heals every use. A
// `literal` argument that isn't a name (a `neg` subtree) isn't a
// string leaf, so the name check below skips it.
// Works on pending markers (mid-build in encodeDef) and resolved refs
// (after a committed edit) alike — hosts run it on the subtree a
// rule-line commit rewrote.
def linkRuleUses(
    doc: Document[NodeData], root: Int): Document[NodeData] =
  doc.subtreeIds(root)
    .filter(id => metaTag(doc, id).contains("rule"))
    .foldLeft(doc) { (d, ruleId) =>
      val pieces = pieceIdsFor(doc, ruleId)
      doc.childrenOf(ruleId)
        .filter(id => metaTag(doc, id).contains("meaning"))
        .flatMap(m => doc.subtreeIds(m))
        .filter(id => metaTag(doc, id)
          .exists(Set("use", "source", "builtin", "literal")))
        .flatMap(id => doc.childrenOf(id))
        .foldLeft(d) { (d2, leafId) =>
          doc.getNode(leafId).map(_.data) match {
            case Some(NodeData.StringData(name)) =>
              pieces.get(name).flatMap(t =>
                updateNodeData(leafId, NodeData.InternalNodeRef(t), d2)
                  .toOption).getOrElse(d2)
            case _ => d2
          }
        }
    }

// Placeholder name → its piece node id, within a `rule` node's
// pattern — the operand link targets.
private[langdef] def pieceIdsFor(
    doc: Document[NodeData], ruleId: Int): Map[String, Int] =
  doc.childrenOf(ruleId)
    .filter(id => metaTag(doc, id).contains("pattern"))
    .flatMap(doc.childrenOf)
    .filter(id => metaTag(doc, id).contains("placeholder"))
    .flatMap(pid => doc.childrenOf(pid).headOption
      .flatMap(k => doc.getNode(k).map(_.data)
        .collect { case NodeData.StringData(s) => s })
      .map(_ -> pid))
    .toMap

// A node's meta construct — pending-marker or resolved ref alike, so
// the pass runs mid-build and post-commit without two variants.
private def metaTag(doc: Document[NodeData], id: Int): Option[String] =
  doc.getNode(id).map(_.data).flatMap(pendingName)
    .orElse(metaConsOf(doc, id))
