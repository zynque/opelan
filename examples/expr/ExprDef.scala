package opelan.examples.expr

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._
import opelan.foundation.langdef.syntax._
import opelan.foundation.langdef.doc.{langDefDoc, metaConsOf, termRefs}
import opelan.foundation.means._
import opelan.foundation.means.text._

// The expression language's definition as data — what its `lang:`
// document encodes. `exprDef` IS the language: ExprLanguage is
// DefLanguage over it, so a stored lang: doc and the registry fallback
// run the same machinery. `langDefDoc` must round-trip it through
// `langDefFromDocument`. The def's `exprImpls` is empty — every
// semantic it names is foundation-provided.
// The language's identity ref — the URL its `lang:` document lives at.
// A constant, not `ExprLanguage.typeRef`: the object interpreting this
// def would otherwise be read while the def initializes.
val exprTypeRef =
  ExternalNodeReference("opelan:languages/int-expression", 0, 0)

val exprDef = LanguageDef(
  typeRef = exprTypeRef,
  title = "lang: expression",
  name = "Expressions",
  description = "the arithmetic-expression dialect",
  // Declared terms derive: where-bound leaf terms plus
  // construct-application pattern declarations (DeriveVocabulary.scala).
  // The arithmetic constructs the rules use are ambient foundation
  // definitions — `add`/`mul`/… resolve by name through ambientRefs
  // with no vocabulary entry at all, so a decoded def needs no
  // code-side table.
  vocabulary = vocabularyFrom(exprRules),
  // The rules carry the syntax; "standard" selects the shared leaf
  // vocabulary (StandardLexicon.scala) — the operand fallback they
  // compile over is its recovering unrecognized run.
  syntax = Some(SyntaxDef("standard", Some(exprRules))),
  // No views section — `edit`, `print`, and `eval` all derive from
  // the def: the text surface edits, the rules print and evaluate.
  views = Nil,
  inputParser = None)

// The demo's starting point for lang: expression: the `/` rule and its
// binding are missing, so the budget's per-person formula sits as a gap
// until the audience adds them to the definition document.
val demoExprDef = exprDef.copy(syntax =
  Some(SyntaxDef("standard", Some(withoutPattern(exprRules, "a / b")))))

// The demo's seeded document — exprDef's own encoding minus the `a / b`
// rule's subtree, every other node id intact, so the remaining
// declarations (`+`, `*`, `-`) keep exprDef's anchors: one anchor space
// across both encodings of the language (a `/` node lands only once the
// user commits the rule, anchored at its new piece in the updated doc).
lazy val demoExprDoc: Document[NodeData] = {
  val doc = langDefDoc(exprDef)
  val ruleId = doc.nodes.indices.find(id =>
    metaConsOf(doc, id).contains("definition") &&
    doc.subtreeIds(id).exists(k =>
      doc.getNode(k).exists(_.data == NodeData.StringData("/"))))
    .getOrElse(sys.error("exprDef encodes no / definition"))
  doc.getNode(ruleId).flatMap(_.parentId)
    .flatMap(p => updateNodeChildIds(p,
      doc.childrenOf(p).filterNot(_ == ruleId), doc).toOption)
    .getOrElse(doc)
}

// A rule set minus one pattern — its rule and every binding entry.
private def withoutPattern(set: RuleSet, text: String): RuleSet = {
  val key = readPattern(text).key
  set.copy(
    rules = set.rules.filterNot(_.pattern.key == key),
    tiers = set.tiers.map(t => Tier(t.patterns.filterNot(_.key == key))))
}

// The code-side semantics the def names: none — the "standard"
// lexicon and the `number`/`ref` leaf vocabulary it tokenizes are
// foundation-provided (StandardLexicon.scala, Leaves.scala's
// standardTerms), so this def needs no impls wiring at all.
val exprImpls = Impls()

// The code-side def's reading — term anchors from `termRefs` since
// this def's construct terms are code-built rather than decoded.
val codeExprReading = RuleReading(exprRules, termRefs(exprDef))

// Construct identities of the language. Code names a term ("*") only
// to look up its vocabulary entry in the canonical encoding; documents
// store the entry's ref, and readers match on it — so a construct
// renamed in its lang: document keeps working (node ids are never
// reused). A `*`/`+` node refs the rule-declared literal piece inside
// the definition document — the rule's meaning interprets it to
// `mul`/`add` at eval. Refs anchored in a different document — the
// def doc a user edits, where new declarations land on fresh ids —
// resolve to their names through the document resolver
// (ViewContext.termName).
lazy val exprTerms: Map[String, ExternalNodeReference] =
  ambientRefs ++ termRefs(exprDef)

// Node data applying a construct, inline — a document imports it when
// the tree is inserted (see makeTyped / internalizeRefs). A construct
// the language doesn't declare keeps its name as a gap — the same
// fallback DefLanguage.termTag gives.
def exprTag(term: String): NodeData =
  exprTerms.get(term).map(NodeData.ExternalNodeRef(_))
    .getOrElse(NodeData.GapData(term))
