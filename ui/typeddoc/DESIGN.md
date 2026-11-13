# `ui/typeddoc/` — design

`TypedDoc`: the workbench's typed-document pane. It composes
[`DocumentEditor`](../editor/DESIGN.md) as a child and offers several
surfaces (`DocView`): **Editor** (structural), **Text** (a `Managed`
CodeMirror surface bound to `docText`/`applyDocText` —
[`foundation/language`](../../foundation/language/DESIGN.md)), and
**Derived** views offered by the document's language (`print`, `eval`,
`sheet`, `formulas`). When the language's impls declare an
`EditSurface` (`Language.editSurface`) the `edit` view is itself
an editing surface — the `docText` text in a decorated CodeMirror
(`editPane`), where the surface's marks paint what each line reads as
(`defDocLineMarks` for lang: docs, `markdownLineMarks` for markdown)
and every keystroke commits through `TextEdited` exactly like the
plain text surface. A surface may also name a preview view, rendered
beside the editor live (markdown's `preview`). Derived views also
stay live in a sidebar while editing. Shown alone, a derived view is the *app* surface — the
spreadsheet without the machinery — with two exits: **Raw** (this
document's representation) and **Definition** (F12: follow the type ref
to the language document — the app's implementation). Notable
mechanics:

- The editor child stays mounted in every view (`display:none`),
  preserving selection and undo across switches.
- `doc` vs `pushedDoc`: editor-originated changes update the mirror but
  are never pushed back down (that would reset the child's state);
  text-originated changes are pushed.
- `textEpoch` bumps remount the CodeMirror surface only when the text
  was re-derived externally, so typed input never echoes.
- The document `store` arrives as a prop and is the language-resolution
  context (see
  [`foundation/language`](../../foundation/language/DESIGN.md) and
  [`examples/Resolve`](../../examples/DESIGN.md)), so an edited `lang:`
  document takes effect immediately.
- The pane owns the incremental parse state — a `TextSession` for the
  text surface, `InputSessions` for projected inputs, and a `GapRetry`
  for stored gaps (see
  [`foundation/language`](../../foundation/language/DESIGN.md)).
- The pending-pin accept button and the `auto-update` toggle live here
  (`TypedDocView`, `TypedDocModel`, `TypedDocUpdate`); the save-time
  fixpoint cascade runs in the workbench (see *Pins select behavior* in
  [`foundation/document/store`](../../foundation/document/store/DESIGN.md)).

This pane is the working proof of *polysyntactic* editing: several
surfaces over one canonical document.
