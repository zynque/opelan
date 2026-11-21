# `examples/` — design

The demonstration dialects and language resolution — the first
*layers* above the core (see *Towers* in the root
[`DESIGN.md`](../DESIGN.md)).

## Resolution is store-first (`Resolve.scala`)

`languageForRef` looks up the type ref's URL in the document store, and
if the document there is a `lang:` document, that document *is* the
dialect. Otherwise it falls back to the code registry (`allLanguages`)
— whose only resident is `DocLanguage`, the code-resident dialect that
`lang:` documents are themselves typed by, defined in
[`../foundation/langdef/Defs.scala`](../foundation/langdef/DESIGN.md);
that is where the regress stops. Every other dialect exists only as a
stored `lang:` document. Malformed defs degrade to gaps:
missing refs, cyclic grammars, and cyclic documents are all guarded,
and a URL holding no def-typed doc resolves to nothing.

## Expression dialect (`expr/`)

`ExprDef`/`ExprRules`, `DemoExpr` — the
demonstration dialect. It has int and float
literals (integer arithmetic stays integral, division and floats are
decimal, and displayed values drop float noise — `valueText`'s
rule); `+`, `-`, `*`, `/` with precedence-aware printing; `ref:N`
references stored as `InternalNodeRef` (so "go to definition" is the
ordinary ref jump); and grouping by tree structure. Its `edit`,
`print`, and `eval` views derive from the def's rules —
`printViewWith` and `evalView` (`foundation/means/engine`) — over a
total, recovering parser, and every piece of the view tree carries its
source id. Evaluation resolves refs through the
enclosing document — a referenced cell's own subtree is evaluated,
so formulas compose — and a visiting set reports ref cycles as gaps
instead of diverging. The leaf layer needs no per-dialect code:
`exprDef`'s syntax names `standard`, the shared leaf vocabulary
(`float`, `int`, `ref`, `?`, `unrecognized` in
`foundation/langdef/syntax/StandardLexicon`, its `where` terms
`number`/`ref`/… in `standardTerms`), and the literal tokens derive
from the rules — `exprTokens` composes them for direct parses
(`foundation/means/engine/LiteralLexicon`).

`ExprRules`/`ExprDef` are the recipe `langDefDoc` encodes into
`exprDoc` — the seed document (`PLAN`, *The document becomes the
seed*). The language is `DefLanguage(decodedExprDef)` — the def as
the document reads it — and `exprTerms`/`exprTag`/`exprReading`/
`demoExprDef`/`exprSampleDoc` all decode from documents, so a stored
`lang: expression` and the built-in run one path. There is no AST and
no tree↔code codec: parses and tests work on `NodeData` trees directly,
and `exprTag`/`exprTerms` map a construct name to the ref its
definition declares. The 28-input battery holding
a constructed `DefLanguage(exprDef)` identical to the interpreted seed
(parse, print, eval) checks the construction path of the definition
machinery (see [`test/`](../test/DESIGN.md)). Code knows an example
dialect's
constructs by the ids its canonical encoding assigns (`termRefs`,
`exprTerms`, `sheetTerms`).

## Sheet dialect (`sheet/`)

`SheetView`/`SheetCell`, `SheetDef`/`SheetLanguage` — the demo app: a
typed document whose content
subtree is `sheet > row/hrow > cell`. Label cells (plain string leaves)
pass through; every other cell is read as an expression and evaluated.
Cells name each other A1-style (`D5` = column D, row 5), and the
document fully reflects the semantics: a cell ref is a `ref` construct
holding its fields — `ref > column("B"), row(3)` — resolved
positionally against the live grid at read time, never a baked-in
node id: `resolveGridRefs` (`GridExpr`) projects the document for the
views, rewriting each `ref` node — id kept — into the `InternalNodeRef`
leaf naming the cell it resolves to, so the derived evaluator follows
it like any other reference; an out-of-grid name reads back as a gap.
A typed name commits as a gap or label leaf — the
structure text can't carry — and `linkCellRefs`, the sheet's
`commitLink`, materializes the `ref` subtree (`Language.linkCommitted`
runs on input commits as it does on text-surface ones). Refs print
back as the position they name; `ref:N` remains as a raw
`InternalNodeRef` escape hatch, displayed by position too (`CellRef`).
The `sheet` view
renders the computed grid; `formulas` renders each cell's expression
instead of its value. Cells parse with the expression dialect's
grammar, referenced by type ref — version-pinned, so a new `lang: expression`
version reaches sheet cells when the sheet's ref bumps to it, not when
the expression doc is saved (see
[`../foundation/document/store/DESIGN.md`](../foundation/document/store/DESIGN.md)).
Rejected text that mentions a cell (`B2`, `ref:N`) is kept as a gap rather
than a label: a formula the grammar can't read yet. Both views render a
*healed* sheet (`SheetHeal`): builtin views receive a `ViewContext` (the
view's settings plus the language's current input grammar), and
`healGaps` retries each gap cell's text against that grammar, so a
gap that now parses renders as the formula it reads as. The document
keeps the gap (what the user wrote) until the cell is committed again.
View settings are data in the def doc (`view(name, builtin,
setting(key, value)…)`); the sheet's `currency` setting formats computed
values. A gap or cycle in a referenced cell propagates only to the
cells that use it — the app recovers like the documents it is made of.
`sampleDoc` seeds the demo's household budget (`exprSampleDoc` seeds
the sidebar's expression sample).

## Markdown dialect (`markdown/`)

`MarkdownLanguage` — a document type for a small markdown subset:
`#`/`##` headings, `* ` list items, paragraphs, and `*italic*`/`**bold**`
inline runs — driven by its `definitions` like every other `lang:` doc.
`markdownRules` displays them: `# t means h1(t)`, `* t means item(t)`,
`*t* means italic(t)`, and so on, with `where` constraints binding the
impls' productions. `markdownParse` composes them to document level:
`blocks` (the block sequence), `runs` (a line's inline spans), and
`plain` (an emphasis operand) are `Impls.productions`/`leaves` built on
`sequenceRule` over those rules — strict, so an unmatched `*` stays
literal text and the tree round-trips the text. The stored tree holds
the surface constructs the patterns declare — `#`, `##`, `*` items —
with `bold`/`italic`/`para`/`list`/`runs`/`markdown` placeholder tags;
`h1`/`h2`/`item` are the constructs' meanings. `markdownLexicon` is
the authored `text` leaf under `markdownTokens`' derived markers.
`markdownLines` prints the tree back;
`markdownPreview` renders the formatted document read-only. The impls
register it all: `Impls.textParser` the commit path, `Impls.editSurface`
the `edit` view's `markdownLineMarks` (heading/item/paragraph line
kinds plus marker/bold/italic spans) beside the live `preview`.
`sampleDoc` seeds the sidebar's `Markdown notes`.
