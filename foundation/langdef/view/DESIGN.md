# `foundation/langdef/view/` — design

The derived views of a `lang:` document — what an author sees instead of
the raw tree (see [`../DESIGN.md`](../DESIGN.md)). Each emits a
[view tree](../../view/DESIGN.md), never DOM.

- **`DefView`** — the `def` view: decodes the doc and lays the
  definition out readably — title, description, the vocabulary of
  terms, the grammar as indented combinator text, the means rules as
  editable lines, view bindings, and the input grammar's target.
- **`DefRuleView`** — the per-rule projection inside the def view:
  pattern, meaning, `where`, and the rule's worked examples.
- **`ExampleCheck`** — a worked example's check: `input means expected`
  — both texts read with the definition's own rules must produce the
  same tree. The marker text tells what the input actually read as (the
  derived printer); an example that can't be checked — no live language
  — is left unmarked rather than marked wrong. Worked examples as live
  checks (*Learnable and self documenting*, *Reactive*) — see
  [`../../means/DESIGN.md`](../../means/DESIGN.md).
