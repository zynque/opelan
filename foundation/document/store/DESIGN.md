# `foundation/document/store/` — design

- **`Store`** — a pure, immutable, versioned document store keyed by URL.
  `put` appends a version (0, 1, 2, …), `resolve` dereferences
  `url@version#node` addresses, and heads are tracked per URL. It is
  append-only, so pinned refs never dangle. The `Store` resolves external
  refs and underlies multi-document workspaces. It is also the *curated
  layer* of history — deliberate, branchable, truncatable checkpoints
  over the operational CRDT log (see
  [`../../version/DESIGN.md`](../../version/DESIGN.md) and
  [`../../../collaboration/DESIGN.md`](../../../collaboration/DESIGN.md)).
- **Names** (`Names.scala`) — a name is a projection. `refName` resolves
  a reference node to its definition through the store, reading the
  head so a rename shows at once, and returns the definition's name: its
  first string child, or a document root's label.

## Pins select behavior

A ref's `url@version` pins execution, not only navigation: a document
runs against the language version its type ref pins, and a `lang:`
document reads the languages *its* refs pin — transitively. Saving an
edit to `lang: expression` appends a version there and nothing else: a
budget pinned to `lang: sheet@0` keeps running sheet v0, whose
input-grammar ref still pins expression v0. A definition edit can never
silently retarget a dependent (*Never broken* — the
[*Towers*](../../../DESIGN.md) opt-in made concrete). What a newer head
produces on a dependent is a *pending update*: any of its external refs
— the type ref, an imports entry, a def's input-grammar ref — that pins
behind the store's head. `pendingUpdates` finds them by scanning the
document's reachable `ExternalNodeRef`s against the heads; accepting
(`acceptUpdates`) rewrites the pins as one ordinary edit, which appends a
new version of the dependent and may in turn pend the layer above; since
every use routes through the imports section, one bumped entry retargets
all uses. (A pin whose node no longer resolves at the newer version is a
broken import, not an update.) A document opts into automatic updates
with a `setting(auto-update, true)` node under its root — ordinary data
the raw outline shows and edits (the flag machinery is
[`../../langdef/AutoUpdate.scala`](../../langdef/AutoUpdate.scala)). The
same recorded accept then happens without the click, in two places: in
the pane whenever a flagged open document has pending pins
([`ui/typeddoc`](../../../ui/typeddoc/DESIGN.md)), and in a fixpoint pass
on save that bumps every flagged dependent still pinned behind a head —
skipping the open document (its pane handles it) and synced documents
(their sessions own their text). Pins are still rewritten as a versioned
edit, never resolved at head invisibly. Go-to-definition opens the
pinned version, and name projections read names at head so a rename
shows at once — display, not semantics. Deriving content migrations for
dependents when a definition changes (*Evolvable*) — rewriting stored
subtrees, not just pins — is the deeper half of the same question and
stays open.
