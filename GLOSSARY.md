# Opelan

Vocabulary for the Open Language Application platform — a local-first
language workbench where programs, documents, languages, and their tooling
are all versioned structured trees (see `MANIFESTO.md`, `DESIGN.md`).

## Language

**Tower**:
The stack of languages the platform is built to support: the workbench's
base languages at the bottom, then increasingly domain-specific languages —
each defined in terms of the layer below — ending in a domain's artifacts
(e.g. a game) at the top.

**Layer**:
One language in a tower. A layer's rules are written only in constructs the
layer(s) below provide; it adds vocabulary, it never redefines.

**Extension**:
Adding rules to a layer expressed purely in constructs from the layers
below — strictly additive. Narrowing is by non-import, and a rule from below
is never redefined: needing to change one means the layer below wasn't
general enough, so the fix lands there as a new version.

**Opt-out**:
A layer or application declining a capability the platform provides
transitively (inspectability, editability, history). A projection and
deployment choice, not a different implementation — the document remains
the truth, and shipping without it is a build-time optimization.

**Pin**:
The `version` in an `url@version#node` reference. Execution resolves
the pinned version of a language document, so a dependency's new head
changes a dependent only when the dependent's pins are bumped.

**Pending update**:
The state of a document whose external refs pin versions behind a
store head. It is offered as a change proposal: the pins bumped and the
gaps the newer languages now read healed, together as one edit.
Accepting it makes a new version of the dependent, which may itself
pend the layer above.

**Change proposal**:
A proposed new version of a document, shown to the user as what would
change and recorded as a version only once accepted. The workbench
proposes for pending updates (pin bumps plus heals); other actors, such
as an LLM pass, can propose the same way.

**Auto-update**:
A per-document opt-in that accepts the change proposals of pending
updates automatically — still a recorded, versioned edit, never
resolved at head invisibly.

**State-as-document**:
The choice that a running application's state is itself a typed document,
so history, branching, sync, and inspection apply to it unchanged.
Time-driven change is versioned per meaningful action, not per frame.

**Foundation**:
The platform-provided floor beneath `lang: language` — machinery every
language gets that is not itself definable in documents: the `gap`
operand term, the recovery floor, and the foundational definitions.

**Foundational definitions**:
The fixed, minimal primitive vocabulary's own definition entries —
name, signature, documentation, worked examples — for the irreducible
primitives and effects (arithmetic, IO, randomness, rendering) that
cannot themselves be documents. Their form differs from authored defs:
behavior is bound to code rather than derived from rules, so an entry
declares the contract the substrate implements rather than defining it.
The Scala substrate is expected to shrink toward it over time.

## Surfaces

**Surface**:
One projection of a document, filling the pane or a section of it: the
structural editor, the text surface, or one of the language's derived
views (which shown alone is the "app" surface).

**Text surface**:
A document rendered as editable text — its language's print/parse
round-trip, or the outline format for raw documents. Edits reparse into
the content subtree, keeping the ids of what didn't change.

**Input**:
An editable text box a derived view projects over a document node — the
view tree's `input` element. Committing parses the text through the
language's input grammar and rewrites the node in place; a language
without an input grammar projects read-only views.

## Documents

**Gap**:
Text or structure that doesn't fit its language yet, kept verbatim in
the document so later edits heal it instead of failing — the *Never
broken* convention (Hazel's *typed holes*, made structural). Stored as
`GapData`, spelled `?text`, bound by the foundation `gap` operand term.

**Heal**:
A gap becoming content: once the language a document pins reads the
gap's text, the gap is replaced by the AST it now parses to, with its
construct uses by reference like any other content (`ref a / ref b`
becomes `div(ref a, ref b)`, plus an imports entry for `div`). Stored
content is never reparsed, so a new rule can only heal gaps, never
change what committed content means. A heal is an edit, carried by the
change proposal of the pending update that brought the rule in; until
it is accepted, views may preview it.

**Format**:
A paired conversion between two representations of the same content —
a document tree and its text (`Outline`, `Serial`), a def and its
`lang:` document, an AST and its document subtree — defined so the two
directions round-trip.
