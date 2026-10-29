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
private[langdef] def metaTag(doc: Document[NodeData], id: Int): Option[String] =
  doc.getNode(id).map(_.data).flatMap(pendingName)
    .orElse(metaConsOf(doc, id))

// Term name → its declaration node's id: the first meaning-position
// node carrying that name, in doc order. Meaning positions are
// `meaning` children and construct arguments; meta constructs
// (`use`/`literal`/`where` pieces…) declare nothing, so their subtrees
// don't walk — matching the builds() half of vocabularyFrom.
private[langdef] def deriveTermIds(
    doc: Document[NodeData], cid: Int): Map[String, Int] = {
  var ids = Map.empty[String, Int]
  def term(id: Int): Unit =
    if (metaTag(doc, id).isEmpty) {
      doc.getNode(id).map(_.data) match {
        case Some(NodeData.StringData(name)) if !ids.contains(name) =>
          ids = ids + (name -> id)
        case _ => ()
      }
      doc.childrenOf(id).foreach(term)
    }
  doc.subtreeIds(cid)
    .filter(id => metaTag(doc, id).contains("meaning"))
    .foreach(m => doc.childrenOf(m).foreach(term))
  ids
}

// Point each term use at its declaration — the counterpart of
// linkUses for meaning constructs and `op` leaves. Every meaning
// position after the first occurrence of a name becomes
// InternalNodeRef to the declaration (a rename heals every use);
// the first keeps its name — it IS the declaration. An `op` encoded
// with a section-entry ref (vocabulary-section defs, older docs)
// already carries InternalNodeRef and keeps it.
private[langdef] def linkTerms(
    doc: Document[NodeData], root: Int,
    ids: Map[String, Int]): Document[NodeData] = {
  val decls = ids.values.toSet
  // Non-declaration term positions — meaning children and nested
  // construct arguments — walked like deriveTermIds: meta-tagged
  // subtrees (use/literal/reparse/quote/…) don't declare or use, so
  // their name operands are never touched.
  var uses = List.empty[Int]
  def term(id: Int): Unit =
    if (metaTag(doc, id).isEmpty) {
      doc.getNode(id).map(_.data) match {
        case Some(NodeData.StringData(_)) if !decls(id) => uses ::= id
        case _ => ()
      }
      doc.childrenOf(id).foreach(term)
    }
  doc.subtreeIds(root)
    .filter(id => metaTag(doc, id).contains("meaning"))
    .foreach(m => doc.childrenOf(m).foreach(term))
  val opLeaves = doc.subtreeIds(root)
    .filter(id => metaTag(doc, id).contains("op"))
    .flatMap(id => doc.childrenOf(id).lift(1))
  (uses ++ opLeaves).foldLeft(doc) { (d, id) =>
    doc.getNode(id).map(_.data) match {
      case Some(NodeData.StringData(name)) =>
        ids.get(name).flatMap(t =>
          updateNodeData(id, NodeData.InternalNodeRef(t), d)
            .toOption).getOrElse(d)
      case _ => d
    }
  }
}
