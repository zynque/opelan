# `foundation/langdef/syntax/` — design

Grammar data → parser: the interpretation layer of a language def (see
[`../DESIGN.md`](../DESIGN.md)).

- **`Interpret`** — interprets a `GrammarDef` into a total, recovering
  `Parser`: each case is the data twin of a combinator in
  [`foundation/grammar`](../../grammar/DESIGN.md). Named grammars are
  bound in a lazy environment so `Ref` (recursion, e.g. parens reaching
  back to the expression level) resolves at call time; a `Ref` to an
  unknown name produces a gap rather than crashing — a def doc can
  name rules that don't exist yet (*Never broken*).
- **`DeriveVocabulary`** — a def's vocabulary derived from its rules:
  construct terms come from construct meanings (`add(a, b)` declares a
  two-child `add` term, and nested constructs declare theirs too); leaf
  terms are the lexical vocabulary the rules' `where` clauses name,
  sorted so encoding is deterministic.
- **`GrammarChanges`** — `namedRules`, `changedRules` (the rules whose
  definitions differ between two `GrammarDef`s), and `changedSince`
  (the diff plus a content migration — e.g. term tags re-pinning a
  bumped def version rather than reparsing). Supplies the names
  `ParseSession.changeRules` consumes; see *Incremental parsing* in
  [`../../grammar/DESIGN.md`](../../grammar/DESIGN.md).
- **`Leaves`** — the leaf vocabulary helpers `where` terms resolve
  against.
