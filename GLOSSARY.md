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
_Avoid_: override, shadow, monkey-patch

**Opt-out**:
A layer or application declining a capability the platform provides
transitively (inspectability, editability, history). A projection and
deployment choice, not a different implementation — the document remains
the truth, and shipping without it is a build-time optimization.
_Avoid_: disable, strip

**Pin**:
The `version` in an `url@version#node` reference. Execution resolves
the pinned version of a language document, so a dependency's new head
changes a dependent only when the dependent's pins are bumped.

**Pending update**:
The state of a document whose external refs pin versions behind a
store head. Accepting pending updates rewrites the pins as one ordinary
edit — a new version of the dependent, which may itself pend the layer
above.

**Auto-update**:
A per-document opt-in that accepts pending updates automatically — the
pins are still rewritten as a recorded, versioned edit, never resolved
at head invisibly.

**State-as-document**:
The choice that a running application's state is itself a typed document,
so history, branching, sync, and inspection apply to it unchanged.
Time-driven change is versioned per meaningful action, not per frame.

**Instruction set**:
The fixed, minimal builtin vocabulary the rules regress bottoms out in —
the irreducible primitives and effects (arithmetic, IO, randomness,
rendering) that cannot themselves be documents. The Scala substrate is
expected to shrink toward it over time.

## Surfaces

**Surface**:
One projection of a document, filling the pane or a section of it: the
structural editor, the text surface, or one of the language's derived
views (which shown alone is the "app" surface).

**Text surface**:
A document rendered as editable text — its language's print/parse
round-trip, or the outline codec for raw documents. Edits reparse into
the content subtree, keeping the ids of what didn't change.
_Avoid_: text cell

**Input**:
An editable text box a derived view projects over a document node — the
view tree's `input` element. Committing parses the text through the
language's input grammar and rewrites the node in place; a language
without an input grammar projects read-only views.
_Avoid_: cell — "cell" is reserved for the worksheet's grid entries
(the sheet example) and table cells in view-tree grids, not for
projected text boxes.
