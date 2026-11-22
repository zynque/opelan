package opelan.examples.expr

import opelan.foundation.document._
import opelan.foundation.document.typed.makeTyped
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc.{
  docImportsRef, docTitleRef, langDefFromDocument}
import opelan.foundation.language.applyDocText

// The expression language's identity ref — the URL its `lang:`
// document lives at. A constant the document and its decoded def
// share.
val exprTypeRef =
  ExternalNodeReference("opelan:languages/int-expression", 0, 0)

// The seed document's text, exactly as the editor surface shows it
// (`docText` on a lang: doc): the meta outline — `dialect`,
// `description`, `name`, `syntax`, `definitions` — with the `means`
// rules written in their authored form. The document is the seed
// (PLAN, *The document becomes the seed*): everything the code path
// needs — the def the runtime interprets, the rules, the term
// anchors — decodes from it, exactly as a stored version is read.
val exprDocText = """dialect
  description
    "the arithmetic-expression dialect"
  name
    "Expressions"
  syntax
    "standard"
    definitions
      (a) means a
      a * b means mul(a, b)
      a / b means div(a, b)
      a + b means add(a, b)
      a - b means sub(a, b)
      +n means literal(n) where n: number
      -n means literal(neg(n)) where n: number
      n means n where n: number
      r means r where r: ref
      binding
        tier
          "a * b"
          "a / b"
        tier
          "a + b"
          "a - b"
      example
        "1 + 2 * 3"
        "1 + (2 * 3)"
""".replace("\r\n", "\n") // the literal picks up the file's endings

// The seed document — a typed skeleton whose content the text parses
// into through the same path a stored document's text surface commits
// (applyDocText: parse, commit, internalize the refs the parse wrote,
// relink `use` operands and term constructs to their nodes).
val exprDoc: Document[NodeData] =
  applyDocText(
    makeTyped(langDefTypeRef,
      NodeData.ExternalNodeRef(docTitleRef),
      NodeData.ExternalNodeRef(docImportsRef),
      NodeData.StringData("lang: expression"),
      DetachedNode.leaf(NodeData.GapData(""))),
    _ => Some(DocLanguage),
    exprDocText)
    .getOrElse(throw new IllegalStateException(
      "exprDocText failed to parse"))

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
    throw new IllegalStateException("seed exprDoc carries no rules"))

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
