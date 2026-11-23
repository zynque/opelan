# CORE-DESIGN.md — the minimal core

This is a small, complete characterization of the platform: **editable,
computable trees.** Everything the wider design adds, such as
text surfaces, the store and version pins, history, sync, undo, etc. is layered over this core.

## Trees

A document is a persistent tree of identity-stable nodes. A node's data
is either primitive **leaf data** (string, int, float) or a
**type reference**: a pointer to the declaration of what this node is, its
*type*.

```
NodeId    = opaque, stable, never reused
Node      = { id: NodeId, data: NodeData, children: [NodeId] }
Document  = { root: NodeId, nodes: Map[NodeId, Node] }

NodeData  = StringData | IntData | FloatData        -- leaves hold data
          | InternalNodeRef | ExternalNodeRef       -- internal nodes name their type
```

References name nodes: same-document (`InternalNodeRef`) or `url@version#node`
across documents (`ExternalNodeRef`). Nothing is ever referred to by name in the underlying data model, so identity, renaming, and cross-document composition come free (`foundation/document/typed`).

The type reference is the node's **intrinsic type**: stored, singular, always
present. A document's type is its root's type (the header naming the
dialect); a content node is a *construct use* — an instance — of the
type it references. Types a language assigns its own expressions are
**extrinsic**: derived by evaluating the language's rules, never stored
(`GLOSSARY.md`, *Type*). Leaves carry no ref; their type is the
literal's sort — a foundational definition (`gap` for `GapData`).

## Definitions

A **definition** is the node a type reference resolves to — the type
itself. It declares a shape and a map of named interpretations:

```
Definition = { shape    : Signature                 -- what instances' children are
             , meanings : { Name -> Clause }         -- the interpretations it contributes to
             }
```

A **meaning** is a named interpretation of nodes, folded bottom-up:
each clause computes from the node itself plus its children's results
under the same meaning:

```
Clause(I) = (node, children: [I]) -> I              -- the carrier I varies per meaning
```

Two meanings have universal default clauses, so they exist for every
type whether or not the definition contributes one:

- **`eval`** — what the node denotes as a value. Default clause: the
  constructor applied to evaluated children — the node itself. Every
  node denotes some tree; a clause is needed only when instances
  *compute* (`mul` → arithmetic) rather than merely *are* content (a
  markdown paragraph).
- **`edit`** — the surface: a view fused with its edit behavior.
  Default clause: shape-derived (*Type shapes*). Every node is
  editable before anyone writes a view for it.

Beyond those, names are an open set — `print`, `compile`, `typecheck`,
`simulate`, whatever a dialect needs (*Meanings* below). A document
lacking a name simply has no interpretation under it.

Two kinds of definition:

- **Primitive** — shape and clauses bound in the substrate: the
  glossary's *foundational definitions* (arithmetic, quoting, leaf
  literals, view elements). The irreducible seed.
- **Derived** — the definition is itself a document; its clauses are
  terms evaluated through the `eval` meaning — the staging point the
  whole dispatch stands on. A Lisp where special forms are
  user-defined documents and every special form carries its own
  editing surface.

One dispatch: **resolve the ref, ask for a named meaning** — `edit`
by default. Definitions being nodes, the same dispatch surfaces the
definition language itself. The workbench is the fixpoint of
surfacing one tree.

## Type shapes

A definition's shape declares what its instances' children look
like. Three shapes cover every type:

- **Product** — a fixed tuple of typed slots (`term name, shape`). An
  instance's children fill the slots positionally; its `eval` clause
  is a function of that many arguments.
- **Sum** — a named choice of variants (`Expr` = `Add | Mul | Lit`;
  the `Either` of typed languages). A sum declares no slots of its
  own; membership is declared on the variant — a slot typed `Expr`
  accepts any node whose type claims `Expr`, so a new dialect can add
  a variant without editing the sum's declaration. `eval` over a sum
  slot just evaluates whatever filled it; a meaning that must
  inspect which variant it received uses a match construct in the
  meaning vocabulary.
- **List** — any number of children of one type; `eval` folds them.

Nodes stay uniform — `(type reference, children)`. The shape lives on
the declaration, not the node, and a slot's type is itself a type
reference, so the same machinery says what may fill it.

The shape alone derives the default `edit` clause: a product renders
its children's editors in sequence with slot names as labels; a sum
slot renders as a dropdown of variants followed by the chosen
variant's editor; a list renders add/remove/move rows. Slots not yet
filled render as *unfilled-slot placeholders*: the structural
editor's form of gap.

## Meanings

Every meaning is a fold: compositional and bottom-up, over a carrier
the name defines:

```
| meaning                     | carrier             | computes
|-----------------------------|---------------------|---------------------------
| eval                        | Value               | what the node denotes
| edit, preview, formulas ... | Surface             | projections you interact with
| print                       | (prec, String)      | text — precedence rides the carrier
| compile                     | tree of another signature | another language's terms
| typecheck                   | Type                | the node's extrinsic type
```

Two boundaries keep the model honest:

- **Clauses see the node itself,** not just folded children — a
  surface's `handle` must emit ops on the node's id, and views carry
  provenance back to their source node.
- **Meanings are node-level folds.** Whole-store questions — find
  usages, resolvers, pending-update scans — are *queries*, not
  meanings; they don't fit the clause signature.

`eval` is privileged among names: the clauses of derived definitions
are terms, made into functions *by* `eval`. It is the interpretation
that interprets the others — where the staging and the bootstrap
live. `edit` is privileged differently: it is the meaning the
workbench opens by default — the one you live in.

## Surfaces — the `edit` meaning

```
Surface = { view   : Node -> View                       -- the node, projected
          , handle : (Node, Gesture) -> [EditOp] }       -- empty: read-only

EditOp  = SetData | InsertChild | RemoveChild | MoveChild | Splice
```

A surface is a projection fused with its edit behavior — what you see
is what you edit; a read-only view is a surface whose `handle` is
empty. A type may define several named surface meanings (markdown's
`edit` and `preview`, the sheet's `sheet` and `formulas`); in-place
editing and side-by-side editor+preview are the same machinery — one
surface that embeds results, or two surfaces in two panes.

Surfaces compose with `eval`: a sheet's surface calls it to display
cell values, a preview surfaces the evaluated tree, an in-place
editor swaps between the two on the same node.

The `edit` clause for a type is resolved through a chain of defaults:

- **Structural outliner** — the floor: works for any node by dispatch
  alone, type metadata or none. Bootstrap needs nothing more.
- **Shape-derived** — rows for products, a dropdown for sums,
  add/remove for lists (*Type shapes*). The universal default clause.
- **Pattern-derived** — a rule's pattern renders as a form: a text box
  per leaf slot, a nested editor per child slot (`foundation/means`).
- **Authored** — clauses written in the view vocabulary, per type.
- **Text** — a surface like any other: parse and print compiled from
  the same rules. The whole recovering-parser stack defers with it —
  and structural editing cannot produce malformed trees, only
  unfilled slots, so *Never broken* simplifies to slot-filling in the
  core.

Every edit produces a new document; the identity of what didn't change
survives (`commitContent`'s reconcile is the existing form).

## Evaluation — the `eval` meaning

`eval` is the meaning other meanings call — surfaces to display
results, meaning terms to compose (`a % means a / 100`), and the
dispatch itself to turn a derived definition's clause into a function.
Depth-first, memoized by node id, staged through the ref:

```
eval(node, memo):
  case node.data:
    leaf(v) -> v
    Ref(r)  ->
      memo.getOrElseUpdate(node.id,
        clause(resolve(r), eval)(node, node.children.map(c => eval(c, memo))))

clause(def, name) =
  def.meanings[name] present -> the function it denotes   -- derived: eval the term
                                                         -- primitive: bound code
  absent                     -> name's default clause
```

- Children are positional arguments. A `use(a)` node (a placeholder
  splice) evaluates to the tree bound at slot `a` — bound-variable
  references need no separate machinery.
- A ref cycle returns a gap value at the edge that closes it rather
  than diverging — *Never broken* holds even in the core.
- The memo table is the incrementality seam (*Incrementality*
  below). This DFS is the oracle form; the target re-evaluates only
  what a change reaches and keeps result identity — the same
  relation the incremental parser has to the full parse
  (`PRINCIPLES.md`). Starting with the batch form is sanctioned;
  designing away the seam is not.

Values are trees and literals — the meaning tree is the program, the
produced tree is the value.

## Incrementality

Writes are commands, reads are folds — the event loop is small:

```
onGesture(g):
  ops  = surfaceAt(selection).handle(selection, g)   -- a command, not a fold
  doc' = apply(ops, doc)                           -- untouched ids stable
  mark(edited spine ∪ ref-dependents ∪ def-dependents)
  -- visible meanings re-fold their dirty cones, lazily
```

The read side needs no new machinery: a clause's result depends only
on its node and its children's results, so a subtree change
invalidates the path to the root and nothing else. The memo tables
simply survive the edit.

Three pieces already paid for turn out to *be* the mechanism:

- **Stable ids are the memo keyspace.** Untouched subtrees keep
  their ids, so memo entries below the dirty cone stay valid — and
  surfaces for them keep DOM identity: selection, scroll, and focus
  surviving a refold is the same mechanism as incrementality.
- **Staging unifies definition changes.** A use's fold depends on its
  type's folded clause, so def→use edges are ordinary fold edges —
  one dependency graph, one engine.
- **Version pins bound the invalidation graph.** A pinned ref cannot
  change underfoot; a remote head never reaches into the memo table.
  Pending updates arrive as ordinary edits through the same loop.

The bookkeeping cost: a result memo alone cannot say what a
definition edit dirties — each fold step must also record its read
set (which clauses and foreign nodes it consulted; `GapRetry`'s
consulted-rules is the existing form). The batch fold stays the
oracle the incremental engine is property-tested against.

Queries are not folds — find-usages, resolvers, pending-update scans
get their own incremental story: an inverse ref index maintained by
diffing the ref set on each edit.

Scheduling is priority tiers over one engine: visible `edit`
surfaces re-fold per frame (lazily — meanings not on screen cost
nothing), `eval` where surfaces display results, `compile` and
`typecheck` in the background. Compilation being a memoized fold,
incremental compilation is free — a rebuild re-folds only the dirty
cone — and deployable artifacts are derived data, content-addressed
by (term, meaning).

## Bootstrap

The seed is the smallest set of primitives for which a definition
document can be authored, surfaced, and interpreted *in the platform*:

```
seed = { eval terms    : use, quote, literal, builtin, construct-application
       , signatures    : product, sum, list          -- the shape vocabulary
       , surface terms : input, label, row, ...      -- the view vocabulary for `edit` clauses
       , edit ops      : the EditOp set              -- already pure
       }
```

Bootstrap completes at the fixpoint: the definition language's own
definition is a document — authored through `edit` clauses derived
from definitions, interpreted by the same `eval`. From there the
deferred capabilities layer on in dependency order: pattern-derived
and authored `edit` clauses, then the text surface (parse *and*
print compiled from the same rules), then the store, version pins,
and history; then sync. Each is a projection or a service over an
unchanged core.

## What the core is not

No grammar, no store, no history, no sync — their absence is the point.
The core says what a document *is* — a tree — and gives every node a
map of named *meanings*: `edit` is the one you live in, `eval` the one
everything composes through. Every other capability of the platform is
a new meaning or a service layered over those same trees.
