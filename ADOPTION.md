# Adoption

Why language workbenches and domain-specific languages have not spread to
domain experts — and how opelan intends to address each blocker. Companion to
`MANIFESTO.md` (principles), `STATUS.md` (what is built and what is not), and
`RELATED_WORK.md` (prior art).

JetBrains MPS is the reference point throughout: it is the most capable
language workbench ever shipped, it has real production successes (mbeddr,
tax and finance DSLs), and it still never escaped a niche. Its failures are
more instructive than its successes.

## The IDE problem

A DSL's author may tolerate a language workbench, but the DSL's *users* are
domain experts — and what MPS gives them is a heavyweight JVM IDE to install,
learn, and keep updated. The deliverable is a programmer's tool, not an
application.

**Opelan's answer** — *Available & Responsive*, *Transitive*: the platform is
a browser tab. A language built on it is meant to ship inside a task-shaped
application that inherits the platform's editing, history, and collaboration
by default — not an IDE plugin. Usability is central to Opelan's design.

## The language-authoring cliff

Defining a language in MPS means learning five aspect meta-languages
(structure, editor, constraints, typesystem, generator), then rebuilding and
redeploying. The fixed cost is so high that a DSL only pays off at
organizational scale. Casual, personal, and departmental languages —
arguably the majority of the opportunity — never get written.

**Opelan's answer** — *Frictionless*: languages are documents, edited in the
tool they run in, with no external toolchain. Opelan intends to make it trivial
for anyone to 'look under the hood'. Deriving a usable editor,
parser, and type rules from a language-definition document is the core open
problem of the bootstrap (see `STATUS.md`), and the project's make-or-break
technical bet.

## Frozen artifacts

When a language changes, every artifact written in it is stranded. MPS has
explicit, hand-written migration aspects; most workbenches have nothing.
Fear of breaking users' work freezes languages prematurely — or worse,
evolution just silently corrupts documents.

**Opelan's answer** — *Evolvable*, *Reactive*: a change to a language
definition should generate the migration tooling itself. Migrations apply
automatically by default — versioned artifacts mean applying one never
destroys the prior state, and deferring or reverting is always safe — with
opt-in review available for users, or for changes whose semantics warrant
it. `url@version#node` addressing already pins artifacts to exact language
versions, so old work never dangles.

## Weak collaboration and versioning

MPS collaboration means committing generated XML to an external VCS; model
diffs are notoriously poor and real-time co-editing essentially does not
exist. Every non-programmer application platform faces the same gap.

**Opelan's answer** — *Preserve History*, *Real Time Collaboration*:
two-layer versioning — an Automerge CRDT operational layer for live
convergence, and a curated version DAG (itself a document) for deliberate,
branchable history. Stable node ids enable semantic diffs that understand
meaning rather than lines.

## One notation per user

MPS supports multiple projections, but they are defined by the language
developer and largely fixed. Users who think differently get no choice, and
there is no graceful text round-trip.

**Opelan's answer** — *Polysyntactic*: many surfaces over one canonical
document — structural editor, text, and language-provided derived views —
selected per user, per task. Projection choice is a user preference, not a
language-design decision.

## Errors as walls

Parsed tools fail on incomplete input; work-in-progress is indistinguishable
from broken. (MPS avoids *parse* failure structurally, but has no semantics
for incomplete programs beyond red squiggles.)

**Opelan's answer** — *Never Broken*, *Immediately Typed*: typed holes keep
unfinished input first-class and editable, and the goal is Hazel-style type
checking per keystroke over holey trees, so every intermediate state is
valid and useful.

## DSLs that produce nothing

A language that cannot produce a running artifact is a document format. MPS
DSLs compile to real software; a workbench without an execution or
generation story builds beautiful dead ends.

**Opelan's status** — honest gap. The expression language has a minimal
`eval` view; interpretation, debugging, testing, and interop for
user-defined languages are unbuilt. This is a required piece, not an
optional one.

## Domain experts program inside apps

The market already voted: Excel, Airtable, and Notion put more "domain
expert programming" in the world than every workbench combined — because the
language lives inside a tool people already use for their actual work.

**Opelan's answer** — *Application*, *Transitive*: the target is not a
better IDE but platforms in which a DSL is embedded in a task-shaped
application — forms, tables, diagrams as projections. The workbench is how
those applications get made, not what the domain expert sees.

## Competition moved on

Tree-sitter, LSP, and VS Code made cheap textual DSLs easy, shrinking the
technical moat workbenches once held. Competing on "define a language
quickly" is a losing game.

**Opelan's answer** — compete where text tooling cannot: structure, holes,
semantic history, live sync, and languages that are applications. The moat
is the *user experience around* the language, not the language definition itself.

## LLMs change the calculus — both ways

Natural-language interfaces reduce the need for bespoke languages at all.
But structured trees with typed holes are arguably the best possible medium
for LLM-assisted authoring: edits are guaranteed well-formed, a hole is a
natural generation target, and a typed language can constrain and check
model output.

**Opelan's answer** — *Verifiable*: DSLs offer precision and verifiability
that natural language cannot; a constrained language is a constrained output
space, making LLMs more effective where they assist and unnecessary where
the language already makes intent unambiguous.

## Fragmentation

A workbench lowers the cost of creating a language — which means it can
supercharge proliferation. Scala is the cautionary tale: unsynchronized
expressiveness let every library invent its own idiom with no shared
substrate. The workbench version is worse — whole languages, each a thin
ecosystem. Fragmentation is fatal when there is no common ground underneath
the variants; Unix survived its own because text streams were a shared
currency.

**Opelan's answer** — one substrate: every language is documents over the
same tree model — same node types, store, versioning, and
`url@version#node` addressing — so artifacts reference each other
structurally rather than living in silos. *Transitively typed* adds shared
semantics; *Polysyntactic* lowers the cost of reading a foreign DSL;
*Evolvable* lets dialects converge instead of stranding users.

**Open** — deliberate mechanisms against proliferation: *language
composition* (extending a language document should be cheaper than
authoring one — mbeddr succeeded by extending C); a *canonical base
vocabulary* of shared language fragments; and *semantic interop* — a shared
value/evaluation model so DSLs compose, not merely link.

## What no tool fixes

The hardest blocker is not technical: DSL authoring is a design skill, and
"domain experts programming" fails on precision of thought more often than
on syntax. No workbench eliminates that.

**Opelan's answer** — lower the cost of iteration (*Reactive*,
*Never Broken*, *Immediately Typed*) and make every language learnable in
place (*Discoverable*, *Self Documenting*). The decisive proof is not a
feature list but a result: one real DSL, authored as a document, carried
end-to-end by a real domain expert.
