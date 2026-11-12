package opelan.examples.expr

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._
import opelan.foundation.langdef.syntax._
import opelan.foundation.langdef.doc.{langDefDoc, metaConsOf, termRefs}
import opelan.foundation.langdef.LeafKind._
import opelan.foundation.means._
import opelan.foundation.means.engine.{evalView, printViewWith, semanticsOf}
import opelan.foundation.means.text._

// The expression language's definition as data — what its `lang:`
// document encodes. `exprDef` IS the language: ExprLanguage is
// DefLanguage over it, so a stored lang: doc and the registry fallback
// run the same machinery. `langDefDoc` must round-trip it through
// `langDefFromDocument`. The def is paired with `exprImpls`, the Impls
// bundle holding the code-side semantics it names.
// The language's identity ref — the URL its `lang:` document lives at.
// A constant, not `ExprLanguage.typeRef`: the object interpreting this
// def would otherwise be read while the def initializes.
val exprTypeRef =
  ExternalNodeReference("opelan:languages/int-expression", 0, 0)

val exprDef = LanguageDef(
  typeRef = exprTypeRef,
  title = "lang: expression",
  name = "Expressions",
  description = "the arithmetic-expression language",
  // Declared terms derive: where-bound leaf terms plus
  // construct-application pattern declarations (DeriveVocabulary.scala).
  // The arithmetic constructs the rules use are ambient foundation
  // definitions — `add`/`mul`/… resolve by name through ambientRefs
  // with no vocabulary entry at all, so a decoded def needs no
  // code-side table.
  vocabulary = vocabularyFrom(exprRules),
  // The rules carry the syntax; the operand fallback they compile
  // over is the lexicon's recovering unrecognized run
  // (ExprLexicon's gap kinds).
  syntax = Some(SyntaxDef("expr", Some(exprRules))),
  views = List(
    ViewBinding("print", "expr-print"),
    ViewBinding("eval", "expr-eval")),
  inputParser = None)

// The demo's starting point for lang: expression: the `/` rule and its
// binding are missing, so the budget's per-person formula sits as a gap
// until the audience adds them to the definition document.
val demoExprDef = exprDef.copy(syntax =
  Some(SyntaxDef("expr", Some(withoutPattern(exprRules, "a / b")))))

// The demo's seeded document — exprDef's own encoding minus the `a / b`
// rule's subtree, every other node id intact, so the remaining
// declarations (`+`, `*`, `-`) keep exprDef's anchors: one anchor space
// across both encodings of the language (a `/` node lands only once the
// user commits the rule, anchored at its new piece in the updated doc).
lazy val demoExprDoc: Document[NodeData] = {
  val doc = langDefDoc(exprDef)
  val ruleId = doc.nodes.indices.find(id =>
    metaConsOf(doc, id).contains("rule") &&
    doc.subtreeIds(id).exists(k =>
      doc.getNode(k).exists(_.data == NodeData.StringData("/"))))
    .getOrElse(sys.error("exprDef encodes no / rule"))
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

// The code-side semantics the def names — kept beside it.
val exprImpls = Impls(
  lexicons = Map("expr" -> exprLexicon),
  // The leaf productions `where` terms bind to — the named leaf
  // vocabulary. Every leaf `where` doesn't bind is an operand
  // production; bound ones are narrowing names (`where a, b: int`).
  // Each names which token kinds it accepts and their leaf data;
  // alternatives try in order (`number` accepts float then int).
  leaves = {
    val numbers = LeafDef(List("float" -> FloatLit, "int" -> IntLit))
    Map(
      "literal"      -> numbers,
      "number"       -> numbers,
      "whole number" -> LeafDef(List("int" -> IntLit)),
      "int"          -> LeafDef(List("int" -> IntLit)),
      "float"        -> LeafDef(List("float" -> FloatLit)),
      "ref"          -> LeafDef(List("ref" -> RefLit("ref:"))))
  },
  views = Map(
    "expr-print" -> ((doc, id, ctx) =>
      printViewWith(exprRules, exprTag, exprViewLeaf,
        ctx.termName)(doc, id)),
    "expr-eval"  -> ((doc, id, ctx) =>
      evalView(semanticsOf(exprRules), exprTerms, ctx.termName)(doc, id))))

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
