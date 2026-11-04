# `foundation/` — design

The pure core: documents, versions, grammars, language machinery, and the
view vocabulary. Everything here is immutable data and total functions —
no DOM, no effects; `ui/` interprets what this layer computes (see
`AGENTS.md`, *Pure logic separated from effects*). Per the *streaming,
reactive, incremental* principle (`PRINCIPLES.md`), parsing, view
derivation, and document updates are incremental by default, with the
full recompute serving as the oracle tests check against.

Packages:

- [`document/`](document/DESIGN.md) — the persistent document tree, its
  node data, and the `Outline`/`Serial` formats
- [`document/store/`](document/store/DESIGN.md) — the versioned
  document store, name projection, and pin/pending-update machinery
- [`document/typed/`](document/typed/DESIGN.md) — typed-document layout,
  imports, everything-by-reference, and the commit reconcile
- [`version/`](version/DESIGN.md) — the version DAG and LSCA merge base
- [`grammar/`](grammar/DESIGN.md) — scannerless, recovering combinators
  and incremental packrat parsing
- [`language/`](language/DESIGN.md) — the `Language` trait, the text
  surface, and text/input sessions
- [`langdef/`](langdef/DESIGN.md) — language definitions as documents:
  the def model, format, interpretation, and views
- [`means/`](means/DESIGN.md) — the `means` rule notation for defining
  languages, its engine, and its text surface
- [`view/`](view/DESIGN.md) — the derived view-tree vocabulary

## Holes — *Never broken*

`GapData` marks text or structure that doesn't fit yet. Parsers are
total — every input parses, with holes holding the verbatim text that
didn't fit — so ongoing edits heal the tree instead of failing
(Hazel-inspired typed holes). Evaluation, rendering, and language
resolution degrade the same way: a hole or cycle blocks only what
depends on it.
