# CORE-DESIGN.md — the minimal core

This is a small, complete characterization of the platform: **editable,
computable trees.** Everything the wider design adds, such as
text based editing, storage, history, collaborative editing, undo/redo, etc. is layered over this core.

## Trees

A document is a persistent tree of nodes with stable identities. A node's data
is either **primitive data** (string, int, float) or a
**type reference**: a pointer to the declaration of what this node is, its
*type*.

```
NodeId    = increasing sequence number, only reused upon compaction
Node      = { id: NodeId, data: NodeData, children: [NodeId] }
Document  = { root: NodeId, nodes: Map[NodeId, Node] }

NodeData  = StringData | IntData | FloatData        -- leaves hold data
          | NodeRef                                 -- internal nodes name their type

NodeRef   = InternalNodeRef | ExternalNodeRef
```

References name nodes: same-document (`InternalNodeRef`) or `url@version#node`
across documents (`ExternalNodeRef`). Nothing is ever referred to by name in the underlying data model, so identity, renaming, and cross-document composition come free.

The type reference is the node's **intrinsic type**: stored, singular, always
present. A document's type is its root's type (the header naming the
dialect); a content node is a use of the
type it references. Types a language assigns its own expressions are
**extrinsic**: derived by evaluating the language's rules
(`GLOSSARY.md`, *Type*). Leaves carry primitive types or
type references that require no children.

## Definitions

A **definition** is the node a type reference resolves to — the type
itself. It declares a shape and a map of named interpretations:

```
Definition = { shape    : Shape                -- what instances' children are
             , meanings : List[Meaning]        -- the interpretations it contributes to
             }
```

A **meaning** is a named interpretation of nodes, folded bottom-up:
each clause computes from the node itself plus its children's results
under the same meaning:

```
Meaning(I) = (node, children: [I]) -> I        -- the carrier I varies per meaning
```

Two default meanings exist for every
type whether or not the definition contributes any:

- **`eval`** — what the node denotes as a value. Default clause: the
  constructor applied to evaluated children — the node itself. Every
  node denotes some tree; a clause is needed only when instances
  *compute* (`mul` → arithmetic) rather than merely *are* content (a
  markdown paragraph).
- **`edit`** — the editor: a view fused with its edit behavior.
  Default clause: shape-derived. Every node is
  editable before anyone writes a view for it.

Beyond those, meanings are an open set — `print`, `compile`, `typecheck`,
`simulate`, whatever a dialect needs (*Meanings* below). A document
lacking a meaning simply has no interpretation under it.

Two kinds of definition:

- **Primitive** — shape and meanings bound in the platform: the
  glossary's *foundational definitions* (arithmetic, quoting, leaf
  literals, view elements). The irreducible seed.
- **Derived** — the definition is itself a document; its meanings are
  evaluated through the `eval` meaning — the staging point the
  whole dispatch stands on. Like a Lisp where special forms are
  user-defined documents and every special form carries its own
  editing surface.

One dispatch: **resolve the ref, ask for a named meaning** — `edit`
by default. Definitions being nodes, the same dispatch surfaces the
definition language itself. The workbench is the fixpoint of
surfacing one tree.

## Type shapes

A definition's shape declares what its instances' children look
like. Three shapes cover every type:

- **Product** — a fixed tuple of typed slots. An
  instance's children fill the slots positionally; its `eval` clause
  is a function of that many arguments of the respective types.
- **Sum** — a choice of variants (`Expr` = `Add | Mul | Lit`;
  the `Either` of typed languages). `eval` over a sum
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

Every meaning is a fold: compositional and bottom-up

```
| meaning                     | carrier             | computes
|-----------------------------|---------------------|---------------------------
| eval                        | Value               | what the node denotes
| edit, preview, formulas ... | Editor Segment      | projections you interact with
| print                       | (prec, String)      | text — precedence rides the carrier
| compile                     | tree of another signature | another language's terms
| typecheck                   | Type                | the node's extrinsic type
```

Two boundaries keep the model honest:

- **Meanings see the node itself,** not just folded children — an
  editor segment's `handle` must emit ops on the node's id, and views carry
  provenance back to their source node.
- **Meanings are node-level folds.** Whole-store questions — find
  usages, resolvers, pending-update scans — are *queries*, not
  meanings; they don't fit the meaning signature.

`edit` is the meaning the
workbench opens by default.

## Editor Segments

```
EditorSegment = { view   : Node -> View                       -- the node, projected
               , handle : (Node, Gesture) -> [EditOp] }       -- empty: read-only

EditOp  = SetData | InsertChild | RemoveChild | MoveChild | Splice
```

A editor segment is a projection fused with its edit behavior — what you see
is what you edit; a read-only view is an editor whose `handle` is
empty. A type may define several named surface meanings (markdown's
`edit` and `preview`, the sheet's `sheet` and `formulas`); in-place
editing and side-by-side editor+preview are the same machinery — one
editor that shows two panes.

An editor segment can compose with `eval`: a sheet's surface calls it to display
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
survives.

## Evaluation

`eval` is the meaning other meanings call to display results.
Depth-first, memoized by node id, staged through the ref:

```
eval(node, memo):
  case node.data:
    leaf(v) -> v
    Ref(r)  ->
      memo.getOrElseUpdate(node.id,
        meaning(resolve(r), eval)(node, node.children.map(c => eval(c, memo))))

meaning(def, name) =
  def.meanings[name] present -> the function it denotes   -- derived: eval the term
                                                         -- primitive: bound code
  absent                     -> name's default clause
```

## Incrementality

Writes are commands. Reads are folds. The event loop:

```
onGesture(g):
  ops  = editorAt(selection).handle(selection, g)   -- a command, not a fold
  doc' = apply(ops, doc)                            -- untouched ids stable
  mark(edited spine ∪ ref-dependents ∪ def-dependents)
  -- visible meanings re-fold their dirty cones, lazily
```

A meaning's result depends only
on its node and its children's results, so a subtree change
invalidates the path to the root and nothing else. The memo tables
simply survive the edit.

- **Stable ids are the memo keyspace.** Untouched subtrees keep
  their ids, so memo entries below the dirty cone stay valid — and
  editors for them keep DOM identity.
- **Edits to referenced nodes** Pending updates arrive as ordinary edits through the same loop.

The bookkeeping cost: a result memo alone cannot say what a
definition edit dirties — each fold step must also record its read
set (which clauses and foreign nodes it consulted; `GapRetry`'s
consulted-rules is the existing form). The batch fold stays the
oracle the incremental engine is property-tested against.

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
       , shapes    : product, sum, list             -- the shape vocabulary
       , editor terms : input, label, row, ...      -- the view vocabulary for `edit` meanings
       , edit ops      : the EditOp set
       }
```

Bootstrap completes at the fixpoint: the definition language's own
definition is a document — authored through `edit` meanings derived
from definitions, interpreted by the same `eval`. From there the
deferred capabilities layer on in dependency order: pattern-derived
and authored `edit` meanings, then the text surface (parse *and*
print compiled from the same rules), then the store, version pins,
and history; then sync. Each is a projection or a service over an
unchanged core.
