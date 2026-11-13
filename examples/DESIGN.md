# `examples/` — design

The demonstration languages and the language registry — the first
*layers* above the core (see *Towers* in the root
[`DESIGN.md`](../DESIGN.md)).

## Resolution is store-first (`Resolve.scala`)

`languageForRef` looks up the type ref's URL in the document store, and
if the document there is a `lang:` document, that document *is* the
language. Otherwise it falls back to the code registry (`allLanguages`:
`ExprLanguage`, `SheetLanguage`, and `DocLanguage` — the code-resident
language that `lang:` documents are themselves typed by, defined in
[`../foundation/langdef/Defs.scala`](../foundation/langdef/DESIGN.md);
that is where the regress stops). Malformed defs degrade to gaps:
missing refs, cyclic grammars, and cyclic documents are all guarded, and
an old store with untyped `lang:` docs falls back to the registry.

## Expression language (`expr/`)

`ExprLanguage`, `Expr`/`ExprDoc`, `ExprLexicon`, `ExprView`
— the demonstration language. It has int and float literals (integer
arithmetic stays integral, division and floats are decimal, and
displayed values drop float noise — `valueText`'s rule); `+`, `-`,
`*`, `/` with precedence-aware printing; `ref:N` references stored as
`InternalNodeRef` (so "go to definition" is the ordinary ref jump); and
grouping by tree structure. It provides `print` and `eval` views and a
total, recovering parser. Both views are derived from the rule set —
`printViewWith` and `evalView` (`foundation/means/engine`) — and every
piece of the view tree carries its source id. Evaluation resolves refs
through the enclosing document — a referenced cell's own subtree is
evaluated, so formulas compose — and a visiting set reports ref cycles
as gaps instead of diverging. `Expr.print` round-trips gaps.

`ExprRules`/`ExprDef` define the language as `means` rules and a def —
`ExprLanguage` IS `DefLanguage(exprDef)`. The 28-input battery holding
a constructed `DefLanguage(exprDef)` identical to the singleton (parse,
print, eval) checks the construction path of the definition machinery
(see [`test/`](../test/DESIGN.md)). Code knows an example language's
constructs by the ids its canonical encoding assigns (`termRefs`,
`exprTerms`, `sheetTerms`).

## Sheet language (`sheet/`)

`Sheet`, `SheetLanguage` — the demo app: a typed document whose content
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
instead of its value. Cells parse with the expression language's
grammar, referenced by type ref — pinned, so a new `lang: expression`
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
`sampleDoc` seeds the demo's household budget (the expression language's
`sampleDoc` seeds the sidebar's expression sample).
