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

**State-as-document**:
The choice that a running application's state is itself a typed document,
so history, branching, sync, and inspection apply to it unchanged.
Time-driven change is versioned per meaningful action, not per frame.

**Instruction set**:
The fixed, minimal builtin vocabulary the rules regress bottoms out in —
the irreducible primitives and effects (arithmetic, IO, randomness,
rendering) that cannot themselves be documents. The Scala substrate is
expected to shrink toward it over time.
