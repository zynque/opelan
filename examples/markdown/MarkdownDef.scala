package opelan.examples.markdown

import opelan.foundation.document._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc.termRefs
import opelan.foundation.langdef.syntax.vocabularyFrom
import opelan.foundation.language.EditSurface
import opelan.foundation.means.lexicalRefs
import opelan.foundation.view.ViewTree

// The markdown language's definition as data — what its `lang:`
// document encodes. `markdownDef` IS the language: MarkdownLanguage is
// DefLanguage over it, so a stored lang: doc and the registry fallback
// run the same machinery. `langDefDoc` must round-trip it through
// `langDefFromDocument`.

val markdownTypeRef =
  ExternalNodeReference("opelan:languages/markdown", 0, 0)

val markdownDef = LanguageDef(
  typeRef = markdownTypeRef,
  title = "lang: markdown",
  name = "Markdown",
  description = "a small markdown subset — headings, lists, emphasis",
  // The vocabulary the rules declare: the `where` leaf terms
  // (`blocks`, `runs`, `plain`) and the surface constructs (`#`, `##`,
  // `*` — construct-application patterns declare them). `markdown`,
  // `list`, `para`, `bold`, `italic` and the `h1`/`h2`/`item` meanings
  // are ambient placeholders — no vocabulary section encodes.
  vocabulary = vocabularyFrom(markdownRules),
  // The `definitions` the lang: doc shows — `d` binds the impls'
  // `blocks` production, and `surface` roots the compiled grammar
  // there: a document is one `blocks` parse, headings/lists/paragraphs
  // inside it through the `runs`/`plain` terms.
  syntax = Some(SyntaxDef("markdown", Some(markdownRules),
    surface = Some(lexicalRefs("blocks")))),
  // `edit` derives — its tile renders the text surface through the
  // impls' `print` builtin, and the pane's editor is the impls'
  // marked edit surface with `preview` beside it.
  views = List(ViewBinding("preview", "markdown-preview")),
  inputParser = None)

// The code-side semantics the def names — kept beside it. `blocks`,
// `runs`, `plain` are the productions the `where` clauses bind
// (MarkdownProductions.scala builds them from the rules); "print" is the
// document's text surface (docText renders it unlisted); `editSurface`
// the editor's marks plus the live preview pane.
val markdownImpls = Impls(
  lexicons = Map("markdown" -> markdownLexicon),
  leaves = Map("plain" -> plainLeaf),
  productions = Map(
    "blocks" -> blocksTerm,
    "runs"   -> runsTerm),
  views = Map(
    "print" -> ((doc, id, ctx) =>
      ViewTree.block(markdownLines(doc, id).map(ViewTree.txt)*)),
    "markdown-preview" -> ((doc, id, ctx) => markdownPreview(doc, id))),
  editSurface = Some(
    EditSurface(markdownLineMarks, preview = Some("preview"))))

// Construct identities of the language — ambient refs (the
// `markdown`/`list`/`para`/`runs`/`bold`/`italic` placeholders the
// meanings name) plus the declared constructs' term refs (`#`, `##`,
// `*` — anchored at their declaring pattern pieces). Documents store
// the ref; readers match on it — a construct renamed in its lang:
// document keeps working.
lazy val markdownTerms: Map[String, ExternalNodeReference] =
  ambientRefs ++ termRefs(markdownDef)

// Node data applying a construct, inline — a document imports it when
// the tree is inserted (see makeTyped / internalizeRefs).
def markdownTag(term: String): NodeData =
  NodeData.ExternalNodeRef(markdownTerms(term))
