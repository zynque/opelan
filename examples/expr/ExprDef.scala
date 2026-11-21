package opelan.examples.expr

import opelan.foundation.document._
import opelan.foundation.langdef._
import opelan.foundation.langdef.syntax._
import opelan.foundation.langdef.doc.{langDefDoc, langDefFromDocument}

// The expression language's definition as data — what its `lang:`
// document encodes. The document is the seed (PLAN, *The document
// becomes the seed*): `exprDef` is only the recipe `langDefDoc`
// encodes into it, and everything the code path needs — the def the
// runtime interprets, the rules, the term anchors — decodes from
// `exprDoc`, exactly as a stored version is read.
// The language's identity ref — the URL its `lang:` document lives
// at — a constant the recipe, the seed, and the decoded def share.
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

// The seed document — `exprDef`'s encoding, built once. The language
// interprets its decode, not the recipe.
val exprDoc: Document[NodeData] = langDefDoc(exprDef)

// The definition as the seed document reads it — what a stored
// lang: expression decodes to. The interpreted language (a DefLanguage
// over this) is built at the point of use, the same way a stored
// version is; the term anchors are the document's own node ids.
val decodedExprDef: LanguageDef =
  langDefFromDocument(exprTypeRef.documentUrl, exprDoc)

// The seed def's reading — the decoded rules and the term anchors the
// document declares, plus the ambient foundation refs.
val exprReading: RuleReading =
  ruleReading(decodedExprDef).getOrElse(
    throw new IllegalStateException("seed exprDef carries no rules"))

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
  exprReading.terms

// Node data applying a construct, inline — a document imports it when
// the tree is inserted (see makeTyped / internalizeRefs). A construct
// the language doesn't declare keeps its name as a gap — the same
// fallback DefLanguage.termTag gives.
def exprTag(term: String): NodeData =
  exprReading.tag(term)
