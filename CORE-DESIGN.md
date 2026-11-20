# CORE-DESIGN.md — the minimal core

The smallest complete characterization of the platform: **editable,
computable trees, and nothing else.** Everything the wider design adds —
text surfaces, the store and pins, versions, sync, undo — is a *surface*
or a *service* layered over this core, deferred by construction rather
than by rescue. Companion to `DESIGN.md` (the whole platform) and
`GLOSSARY.md` (vocabulary); `foundation/means/DESIGN.md` is this core
being built.

## Trees

A document is a persistent tree of identity-stable nodes. A node's data
is either **leaf data** (a literal — today: string, int, float) or a
**reference** to the node declaring what this node is — its *construct*.

```
NodeId    = opaque, stable, never reused
Node      = { id: NodeId, data: NodeData, children: [NodeId] }
Document  = { root: NodeId, nodes: Map[NodeId, Node] }

NodeData  = StringData | IntData | FloatData        -- leaves hold data
          | InternalNodeRef | ExternalNodeRef       -- internal nodes name their construct
```

Refs name nodes: same-document (`InternalNodeRef`) or `url@version#node`
across documents (`ExternalNodeRef`). Content never spells a construct
by name — *everything by reference* — so identity, renaming, and
cross-document composition come free (`foundation/document/typed`).

## Definitions

A **definition** is the node a ref resolves to. It denotes a pair of
interpretations over nodes of its type:

```
Definition = { eval   : [Value] -> Value            -- evaluated children -> value
             , render : Node -> View                -- the node, projected
             , edit   : (Node, Gesture) -> [EditOp] -- gestures -> tree operations
             }
```

Two kinds:

- **Primitive** — the pair is bound in the substrate: the glossary's
  *foundational definitions* (arithmetic, quoting, leaf literals, view
  elements). The irreducible seed.
- **Derived** — the pair is computed. The definition is itself a
  document: its *meaning* — a term in the layer below — evaluates to
  the `eval` function, and its *pattern* derives the editor. A Lisp
  where special forms are user-defined documents and every special form
  carries its own editing surface.

There is exactly one dispatch in the whole system: **resolve the ref,
ask the definition.** To evaluate a node, ask for its `eval`; to show
or edit it, ask for its `render`/`edit`. Definitions being nodes, the
same dispatch handles the definition language itself — that recursion
is the design. The workbench is the fixpoint of two interpretations of
one tree.

## Evaluation

Depth-first, memoized by node id, staged through the ref:

```
eval(node, memo):
  case node.data:
    leaf(v) -> v
    Ref(r)  ->
      memo.getOrElseUpdate(node.id,
        handler(resolve(r))(node.children.map(c => eval(c, memo))))

handler(def) =
  def is primitive -> def.eval                        -- bound code
  def is derived   -> eval(def.meaning)               -- a term -> a function
```

- Children are positional arguments. A `use(a)` node (a placeholder
  splice) evaluates to the tree bound at slot `a` — bound-variable
  references need no separate machinery.
- A ref cycle returns a gap value at the edge that closes it rather
  than diverging — *Never broken* holds even in the core.
- The memo table is the incrementality seam. This DFS is the oracle
  form; the target re-evaluates only what a change reaches and keeps
  result identity — the same relation the incremental parser has to
  the full parse (`PRINCIPLES.md`). Starting with the batch form is
  sanctioned; designing away the seam is not.

Values are trees and literals — the meaning tree is the program, the
produced tree is the value.

## Editing

An editor is the second half of the pair: a projection of the node,
plus the map from gesture to tree operation.

```
render(node)         = resolve(node.data).render(node)   -- leaves: a literal editor
edit(node, gesture)  = apply(resolve(node.data).edit(node, gesture), document)

EditOp = SetData | InsertChild | RemoveChild | MoveChild | Splice
```

- **Generic floor.** A structural editor — the tree outliner — works
  for *any* node by dispatch alone: render each node through its
  definition, recurse into children. Bootstrap needs nothing more.
- **Derived surface.** A construct's pattern — the shape of its slots —
  renders as a form: a text box per leaf slot, a nested editor per
  child slot (`foundation/means`: a structural LHS is the canonical
  editor). Authored views come later as a refinement, never a
  requirement.
- **Text is a surface, not the core.** A purely structural editor
  cannot produce malformed trees — only empty slots — so *Never
  broken* simplifies to slot-filling, and the entire recovering-parser
  stack defers with the text surface.

Every edit produces a new document; the identity of what didn't change
survives (`commitContent`'s reconcile is the existing form).

## Bootstrap

The seed is the smallest set of primitives for which a definition
document can be authored, interpreted, and edited *in the platform*:

```
seed = { meaning terms : use, quote, literal, builtin, construct-application
       , view terms    : input, label, row, ...      -- the editor vocabulary
       , edit ops      : the EditOp set              -- already pure
       }
```

Bootstrap completes at the fixpoint: the definition language's own
definition is a document — authored through editors derived from
definitions, evaluated by the same `eval`. From there the deferred
capabilities layer on in dependency order: derived per-construct
editors, then the text surface (parse *and* print compiled from the
same rules), then store/pins/versions, then sync. Each is a projection
or a service over an unchanged core.

## What the core is not

No grammar, no store, no history, no sync — their absence is the point.
The core says what a document *is* and the two things you can *do* with
it: evaluate it and edit it. Every other capability of the platform is
an interpretation layered over those same trees.
