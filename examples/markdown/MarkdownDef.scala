package opelan.examples.markdown

import opelan.foundation.document._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc.termRefs
import opelan.foundation.language.EditSurface
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
  // The block/inline constructs are placeholders — structural
  // vocabulary no definition document authors yet (the sheet's grid
  // terms the same). Listed so the def's names resolve to their refs;
  // they encode as no `vocabulary` section.
  vocabulary = markdownPlaceholders.map(t =>
    NodeTerm(t.name, t.shape, Some(t.ref))),
  // The text surface is code, not a rule set: `parse`/`print` are the
  // impls' builtins, so the def declares no `syntax` section.
  syntax = None,
  views = List(
    // `edit` binds the source-text builtin — the sidebar's edit tile
    // shows what the editor holds; the pane's edit view is the impls'
    // edit surface. `preview` renders the formatted document.
    ViewBinding("edit", "print"),
    ViewBinding("preview", "markdown-preview")),
  inputParser = None)

// The code-side semantics the def names — kept beside it. "print" is
// the document's text surface (docText renders it unlisted);
// `textParser` the commit path's parser; `editSurface` the editor's
// marks plus the live preview pane.
val markdownImpls = Impls(
  views = Map(
    "print" -> ((doc, id, ctx) =>
      ViewTree.block(markdownLines(doc, id).map(ViewTree.txt)*)),
    "markdown-preview" -> ((doc, id, ctx) => markdownPreview(doc, id))),
  textParser = Some(parseMarkdown),
  editSurface = Some(
    EditSurface(markdownLineMarks, preview = Some("preview"))))

// Construct identities of the language — the placeholder anchors, as
// the sheet's are (SheetDef). Documents store the ref; readers match
// on it — a construct renamed in its placeholder entry keeps working.
lazy val markdownTerms: Map[String, ExternalNodeReference] =
  termRefs(markdownDef)

// Node data applying a construct, inline — a document imports it when
// the tree is inserted (see makeTyped / internalizeRefs).
def markdownTag(term: String): NodeData =
  NodeData.ExternalNodeRef(markdownTerms(term))
