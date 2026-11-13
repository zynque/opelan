package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.document.Detached._
import opelan.foundation.langdef._

// A LanguageDef as a document: a typed document (type = the
// language-definition language, langDefTypeRef) whose content carries
// the definition as data. Every construct is used by reference — the
// meta constructs (`term`, `vocabulary`, `definition` …) are imported
// from
// lang: language, and references *within* the definition (a `use`
// operand to its placeholder piece, a construct to its declaring
// meaning node) are internal refs to the defining node. Only the
// definition's own names and literal syntax
// are strings. Sets and op maps sort so the bytes are deterministic.
//
//   ext: lang: language              type ref on the root's data
//     ext: imports-declaration       the dependencies (title, term, …)
//     &imports(title) "lang: expression"   title construct + text
//     dialect
//       description  "the arithmetic-expression language"
//       name         "Expressions"
//       syntax       "expr"  rules(…)   — the operand fallback is the
//                              lexicon's recovery and stores nothing
//       views        view("print", "expr-print") …
//                    view("sheet", "sheet-grid", setting("currency", "$"))
//       inputs       language(&import, as-label)
//
// There is no `vocabulary` section: every term a meaning can use is
// ambient (foundation primitives + placeholders), imported (the
// `imports` declaration names the documents, the resolver reads their
// terms), or declared by the def's own rule patterns — each
// construct-application pattern declaration anchors the term's ref.
// The `vocabulary`/`term` meta constructs stay reserved for a future
// declaration form.
def langDefDoc(d: LanguageDef): Document[NodeData] = encodeDef(d)._1

// The document plus each vocabulary term's node id. lang: language
// itself imports its constructs from its own vocabulary — the bootstrap
// fixpoint — so its imports address the ids this very build assigns.
private[langdef] def encodeDef(
    d: LanguageDef): (Document[NodeData], Map[String, Int]) = {
  val doc0 = beginTyped(langDefTypeRef, pending("title"), pending("imports"),
    NodeData.StringData(d.title),
    DetachedNode.leaf(pending("dialect")))
  val cid = contentId(doc0).getOrElse(doc0.rootId)
  val hasRules = d.syntax.flatMap(_.rules).isDefined
  val (doc1, _) = add(doc0, cid,
    n(pending("description"), sl(d.description)),
    n(pending("name"), sl(d.name)))
  val doc2 = add(doc1, cid, List(
    d.syntax.map(s => n(pending("syntax"),
      sl(s.lexicon) +:
        s.rules.toList.map(rulesNode(_, d.typeRef.documentUrl, pending))*)),
    Some(n(pending("views"), d.views.map(viewNode)*)),
    d.inputParser.map(cp => n(pending("inputs"), inputsNode(cp)))).flatten*)._1
  val linked = linkRuleUses(doc2, cid)
  val termIds =
    if (hasRules) deriveTermIds(linked, cid) else Map.empty[String, Int]
  // Bare construct names in meanings bind to the ambient registry
  // (foundation primitives + placeholders).
  val doc3 = resolvePending(
    linkTerms(linked, cid, termIds, ambientRefs), name =>
    metaRef(name).map(NodeData.ExternalNodeRef(_))
      .getOrElse(NodeData.GapData(name)))
  (internalizeStandard(doc3), termIds)
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

private def viewNode(v: ViewBinding): DetachedNode[NodeData] =
  n(pending("view"), (sl(v.name) +: sl(v.builtin) +:
    v.settings.toList.sorted.map { case (k, value) =>
      n(pending("setting"), sl(k), sl(value))
    })*)

private def inputsNode(cp: InputParserDef): DetachedNode[NodeData] = cp match {
  case InputParserDef.Builtin(name) =>
    n(pending("builtin"), sl(name))
  case InputParserDef.Language(ref, fb) =>
    n(pending("language"), erl(ref), DetachedNode.leaf(pending(fb match {
      case InputFallback.Label => "as-label"
      case InputFallback.Gap  => "as-gap"
    })))
}
