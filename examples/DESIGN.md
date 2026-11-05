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

`ExprLanguage`, `Expr`/`ExprDoc`, `ExprLexicon`, `ExprParse`, `ExprView`
— the demonstration language. It has int and float literals (`Num`:
integer arithmetic stays integral, division and floats are decimal,
`Num.show` rounds away float noise for display); `+`, `-`, `*`, `/` with
precedence-aware printing; `ref:N` references stored as
`InternalNodeRef` (so "go to definition" is the ordinary ref jump); and
grouping by tree structure. It provides `print` and `eval` views and a
total, recovering parser. Views render through `LocExpr`, a located read
that keeps each node's id, so every piece of the view tree carries its
source id. Evaluation resolves refs through the enclosing document — a
referenced cell's own subtree is evaluated, so formulas compose — and a
visiting set reports ref cycles as gaps instead of diverging. The
parser also emits a **span map** (`Map[List[Int], Span]`, tree path →
source range), groundwork for mapping text edits onto nodes instead of
reparsing whole cells — superseded by per-node lengths under
[incremental parsing](../foundation/grammar/DESIGN.md). `Expr.print`
round-trips gaps.

`ExprRules`/`ExprDef`/`Terms` express the same language as `means`
rules and as a def, so `DefLanguage(exprDef)` can be held identical to
`ExprLanguage` (parse, print, and eval) on a 28-input battery — the
regression anchor for any change to the definition machinery (see
[`test/`](../test/DESIGN.md)). Code knows an example language's
constructs by the ids its canonical encoding assigns (`termRefs`,
`exprTerms`, `sheetTerms`).

## Sheet language (`sheet/`)

`Sheet`, `SheetLanguage` — the demo app: a typed document whose content
subtree is `sheet > row/hrow > cell`. Label cells (plain string leaves)
pass through; every other cell is read as an expression and evaluated,
with `InternalNodeRef` leaves addressing other cells. The `sheet` view
renders the computed grid; `formulas` renders each cell's expression
instead of its value. Cells parse with the expression language's
grammar, referenced by type ref — pinned, so a new `lang: expression`
version reaches sheet cells when the sheet's ref bumps to it, not when
the expression doc is saved (see
[`../foundation/document/store/DESIGN.md`](../foundation/document/store/DESIGN.md)).
Rejected text that mentions a cell (`ref:N`) is kept as a gap rather
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
`sampleDoc` seeds the demo's household budget.
