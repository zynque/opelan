# `foundation/langdef/syntax/` — design

The syntax half of a language def (see [`../DESIGN.md`](../DESIGN.md)):
what its `means` rules compile over, and what a language change
invalidates.

- **`DeriveVocabulary`** — a def's vocabulary derived from its rules:
  construct terms come from construct meanings (`add(a, b)` declares a
  two-child `add` term, and nested constructs declare theirs too); leaf
  terms are the lexical vocabulary the rules' `where` clauses name,
  sorted so encoding is deterministic.
- **`GrammarChanges`** — `syntaxChanges` (the memo names a rule-set
  change drops) and `retagBy` (a content migration — e.g. term tags
  re-pinning a bumped def version rather than reparsing). Supplies the
  names `ParseSession.changeRules` consumes; see *Incremental parsing*
  in [`../../grammar/DESIGN.md`](../../grammar/DESIGN.md).
- **`Leaves`** — the leaf vocabulary helpers `where` terms resolve
  against.
