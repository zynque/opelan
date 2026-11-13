package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.typed._
import opelan.foundation.grammar.ParseTree
import opelan.foundation.means._
import opelan.foundation.langdef.{ambientTermOf, LanguageDef}
import opelan.foundation.means.text._

// A `definition` node read back to a `Rule` — the inverse of RuleEncode.
// A
// string first child is the text form (read tolerantly through
// readRuleOrGap, as docs written before the structured encoding did);
// otherwise the structured form — `pattern` pieces, the `meaning` term
// subtree, `where` constraints (foundation/document/typed/DESIGN.md,
// *Everything by reference*).
// Every read is total: missing or malformed parts become gaps.
private[langdef] def ruleFromDoc(
    doc: Document[NodeData], url: String,
    version: Int, termIds: Map[String, Int],
    resolve: ExternalNodeReference => Option[LanguageDef], id: Int): Rule =
  doc.childrenOf(id).headOption.flatMap(i => strOf(doc, i))
    .map(t => readRuleOrGap(t).copy(id = Some(id))).getOrElse(Rule(
      entriesOf(doc, id, "pattern").headOption
        .map(p => Pattern(doc.childrenOf(p).map(pieceFrom(doc, _))))
        .getOrElse(Pattern(Nil)),
      entriesOf(doc, id, "meaning").headOption
        .flatMap(m => doc.childrenOf(m).headOption)
        .map(meaningFromDoc(doc, url, version, termIds, resolve, _))
        .getOrElse(Meaning.gap("")),
      entriesOf(doc, id, "where").headOption.toList.flatMap(w =>
        entriesOf(doc, w, "constraint")
          .flatMap(constraintFrom(doc, _))),
      Some(id)))

private def pieceFrom(doc: Document[NodeData], id: Int): Piece = {
  val text = doc.childrenOf(id).headOption.flatMap(strOf(doc, _))
    .getOrElse("")
  val glued = entriesOf(doc, id, "glued").nonEmpty
  if (metaConsOf(doc, id).contains("placeholder"))
    Piece.Placeholder(text, glued)
  else Piece.Literal(text, glued)
}

// A meaning subtree as a term: meta builtins read positionally —
// `use`/`source`/`builtin` operands are name terms (a linked one
// carries the piece node's ref), `quote` its stored subtree; anything
// else is a construct of the
// defined language, named and referenced through its term entry.
private def meaningFromDoc(
    doc: Document[NodeData], url: String,
    version: Int, termIds: Map[String, Int],
    resolve: ExternalNodeReference => Option[LanguageDef], id: Int): Meaning =
  metaConsOf(doc, id) match {
  case Some(tag @ ("use" | "source" | "builtin")) =>
    Meaning(tag, doc.childrenOf(id).map(operandFrom(doc, url, version, _)))
  // A literal's operands are value terms — name leaves (a linked one
  // carries the piece ref) and construct subtrees alike.
  case Some("literal") =>
    Meaning("literal",
      doc.childrenOf(id)
        .map(meaningFromDoc(doc, url, version, termIds, resolve, _)))
  case Some("quote") =>
    Meaning("quote", quoted =
      doc.childrenOf(id).headOption.map(treeFrom(doc, _)))
  case _ =>
    Meaning(kindName(doc, url, version, termIds, resolve, id),
      doc.childrenOf(id)
        .map(meaningFromDoc(doc, url, version, termIds, resolve, _)),
      kindRef(doc, url, version, termIds, id))
}

// A builtin's operand — a bound placeholder's name, plus the piece
// node's ref when the doc's link pass wrote one.
private def operandFrom(
    doc: Document[NodeData], url: String,
    version: Int, id: Int): Meaning =
  doc.getNode(id).map(_.data) match {
    case Some(NodeData.InternalNodeRef(t)) =>
      Meaning(definitionName(doc, t).getOrElse(""),
        reference = Some(ExternalNodeReference(url, version, t)))
    case Some(NodeData.ExternalNodeRef(r)) =>
      Meaning(definitionName(doc, r.nodeId).getOrElse(""),
        reference = Some(r))
    case Some(NodeData.StringData(s)) => Meaning.nameTerm(s)
    case _                            => Meaning.nameTerm("")
  }

private def kindRef(
    doc: Document[NodeData], url: String,
    version: Int, termIds: Map[String, Int],
    id: Int): Option[ExternalNodeReference] =
  externalRefOf(doc, id).orElse(doc.getNode(id).map(_.data).flatMap {
    case NodeData.InternalNodeRef(t) =>
      Some(ExternalNodeReference(url, version, t))
    // A named construct node's ref is its declaration: the first
    // meaning-position node that carries the name.
    case NodeData.StringData(s) =>
      termIds.get(s).map(ExternalNodeReference(url, version, _))
    case _ => None
  })

private def kindName(
    doc: Document[NodeData], url: String,
    version: Int, termIds: Map[String, Int],
    resolve: ExternalNodeReference => Option[LanguageDef],
    id: Int): String =
  kindRef(doc, url, version, termIds, id) match {
    // An ambient reference names itself (foundation primitive or
    // placeholder); a ref into another def's document names itself
    // through that definition's vocabulary via `resolve`; else the
    // local term entry.
    case Some(r) =>
      ambientTermOf(r).map(_.name)
        .orElse(resolve(r).flatMap(d => d.vocabulary
          .find(t => t.ref.exists(sameNode(_, r))).map(_.name)))
        .orElse(definitionName(doc, r.nodeId)).getOrElse("")
    case None    => strOf(doc, id).getOrElse("")
  }

private def treeFrom(doc: Document[NodeData], id: Int): ParseTree =
  ParseTree(doc.getNode(id).map(_.data).getOrElse(NodeData.GapData("")),
    "", 0, 0, doc.childrenOf(id).map(treeFrom(doc, _)))

// A constraint's strings are its placeholder names then its term.
private def constraintFrom(
    doc: Document[NodeData], id: Int): Option[Constraint] =
  doc.childrenOf(id).flatMap(strOf(doc, _)) match {
    case ss if ss.length >= 2 => Some(Constraint(ss.init, ss.last))
    case _                    => None
  }

private def entriesOf(
    doc: Document[NodeData], id: Int, name: String): List[Int] =
  doc.childrenOf(id).filter(metaConsOf(doc, _).contains(name))

private def strOf(doc: Document[NodeData], id: Int): Option[String] =
  doc.getNode(id).map(_.data).collect { case NodeData.StringData(s) => s }
