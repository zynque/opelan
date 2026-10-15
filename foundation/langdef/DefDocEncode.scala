package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.Detached._

// A LanguageDef as a document: a typed document (type = the
// language-definition language, langDefTypeRef) whose content carries
// the definition as data. Every construct is used by reference — the
// meta constructs (`kind`, `schema`, `op` …) are imported from
// lang: language, and references *within* the definition (an operator
// to the kind it builds, `use` to a named grammar) are internal refs to
// the defining node. Only the definition's own names and literal syntax
// are strings. Sets and op maps sort so the bytes are deterministic.
//
//   ext: lang: language              type ref on the root's data
//     &imports(title) "lang: expression"   title construct + text
//     imports                        kind, schema, op … (from lang: language)
//     definition
//       description  "the arithmetic-expression language"
//       name         "Expressions"
//       schema       kind("add", fixed(2))  kind("mul", fixed(2)) …
//       syntax       "expr"  phrase(named("expr", precedence(…)), "")
//       views        view("print", "expr-print") …
//       cells        language(&import, as-label)
def langDefDoc(d: LanguageDef): Document[NodeData] = encodeDef(d)._1

// The document plus each schema kind's node id. lang: language itself
// imports its constructs from its own schema — the bootstrap fixpoint —
// so its imports address the ids this very build assigns.
private[langdef] def encodeDef(
    d: LanguageDef): (Document[NodeData], Map[String, Int]) = {
  val doc0 = beginTyped(langDefTypeRef, pending("title"),
    NodeData.StringData(d.title),
    DetachedNode.leaf(pending("definition")))
  val cid = contentId(doc0).getOrElse(doc0.rootId)
  val (doc1, schemaId) = add(doc0, cid,
    n(pending("description"), sl(d.description)),
    n(pending("name"), sl(d.name)),
    n(pending("schema"), d.schema.map(kindNode)*))
  val kindIds = d.schema.map(_.name).zip(doc1.childrenOf(schemaId)).toMap
  val doc2 = add(doc1, cid, List(
    d.syntax.map(s => n(pending("syntax"), sl(s.lexer), grammarNode(s.grammar, kindIds))),
    Some(n(pending("views"), d.views.map(v =>
      n(pending("view"), sl(v.name), sl(v.builtin)))*)),
    d.cellParser.map(cp => n(pending("cells"), cellsNode(cp)))).flatten*)._1
  val metaIds =
    if (d.typeRef.documentUrl == langDefTypeRef.documentUrl) kindIds else metaKindIds
  val doc3 = resolvePending(linkUses(doc2, cid), name =>
    metaRefIn(metaIds, name).map(NodeData.ExternalNodeRef(_))
      .getOrElse(NodeData.GapData(name)))
  val doc4 = Seq(titleId(doc3), contentId(doc3)).flatten
    .foldLeft(doc3)((d, id) => internalizeRefs(d, id).getOrElse(d))
  (doc4, kindIds)
}

// Append subtrees under `parent`; returns the doc and the last one's id
// (a subtree's root is its last node).
private def add(
    doc: Document[NodeData], parent: Int,
    subtrees: DetachedNode[NodeData]*): (Document[NodeData], Int) =
  subtrees.foldLeft((doc, -1)) { case ((d, last), t) =>
    insertNode(t, parent, d.childrenOf(parent).length, d)
      .map(d2 => (d2, d2.nodes.length - 1)).getOrElse((d, last))
  }

private def kindNode(k: NodeKind): DetachedNode[NodeData] =
  n(pending("kind"), sl(k.name), k.shape match {
    case NodeShape.Leaf     => DetachedNode.leaf(pending("leaf"))
    case NodeShape.Any      => DetachedNode.leaf(pending("any"))
    case NodeShape.Fixed(a) => n(pending("fixed"), il(a))
  })

private def cellsNode(cp: CellParserDef): DetachedNode[NodeData] = cp match {
  case CellParserDef.Builtin(name) =>
    n(pending("builtin"), sl(name))
  case CellParserDef.Language(ref, fb) =>
    n(pending("language"), erl(ref), DetachedNode.leaf(pending(fb match {
      case CellFallback.Label => "as-label"
      case CellFallback.Hole  => "as-gap"
    })))
}
