# `test/` — design

The pure core is the tested core: munit suites mirror the source
packages, and full recomputes serve as the oracles the incremental paths
are checked against (`PRINCIPLES.md`).

- `test/foundation/**` — document ops, the outline format, typed
  documents, grammar combinators, incremental parsing sessions, version
  LSCA. The incremental parser's guard is a property test: for any edit,
  an incremental reparse equals a full parse of the edited text — over a
  multi-rule grammar and the interpreted expression language, hundreds
  of random edits with rule changes interleaved (see
  [`../foundation/grammar/DESIGN.md`](../foundation/grammar/DESIGN.md)).
- `test/examples/**` — the expression and sheet languages, language
  defs, and their document encoding. Includes the 28-input battery
  holding a constructed `DefLanguage(exprDef)` identical to the
  `ExprLanguage` singleton (parse, print, eval) — the construction-path
  check for the definition machinery
  (see [`../examples/DESIGN.md`](../examples/DESIGN.md)).
- `test/collaboration/**` — `DocSession` convergence under real
  Automerge in Node.
- `test/ui/**` — editor, pane, and workbench update functions, the fp
  runtime, view trees.

Run the suite with `test.bat` / `test.sh` (they point `TEMP`/`TMPDIR` at
`./.tmp` so Node can resolve `node_modules` from the linked test
bundle); see *Build and test* in the root [`DESIGN.md`](../DESIGN.md).
