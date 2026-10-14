# Opelan — Design

Opelan (**Op**en **L**anguage **A**pplicatio**n**) is a browser-based language
workbench: a tool for building software in which programs, documents, and the
tooling itself are all versioned, structured trees that can be viewed,
edited, and interpreted through multiple surfaces. It is written in
Scala.js, bundled with webpack, and runs local-first in the browser
(IndexedDB persistence, no server required).

This document covers the project's goals, lineage, and architecture.
Companion docs: `MANIFESTO.md` (principles), `PRINCIPLES.md` (the reasoning
behind them), `ADOPTION.md` (why workbenches haven't spread, and how this
one means to), `RELATED_WORK.md` (survey of related systems), `DEMO.md`
(plan for a shareable demo), `README.md` (build/run), `USAGE.md` (how to
drive the workbench), `STATUS.md` (current state and next steps), and
`AGENTS.md` (code conventions).

## 1. Lineage: the olw-p* prototypes

The repo keeps four earlier prototypes under `old-olws/` (also published as
`zynque/olw-p1` … `olw-p4`). Each explored a piece of the design:

| Prototype | Tech | What it explored | What survived |
|-----------|------|------------------|---------------|
| **olw-p1** "FSOLP" | F# / WPF | *Phrase graph*: every phrase has a type (first phrase-child), parents, contents, and **interpretations/quotations keyed by Context** — the origin of "one node, many representations". First-class deltas/changesets with dirty-flag propagation for re-render and undo. | The typed-document convention (type = first child); deltas → today's persistent-document ops + undo stacks |
| **olw-p2** | CoffeeScript | Immutable document store: append-only node array, copy-on-write path updates yielding a new `rootId` per version; explicit version lists; a *live repository* façade over the immutable store; a document **header URL selects its interpreter** | Immutable-node-array document; versions as data; type-directed interpretation |
| **olw-p3** | Scala.js client/server | `Document[T]` / `NavigableDocument` shared model (persistent tree + parent index); editable text segments, tree editors, virtual-dom experiments | Document shape refined; lesson that a hand-grown ad-hoc UI layer becomes unmanageable — motivation for `ui/fp` |
| **olw-p4** | Elm | The most mature model: persistent array-backed `Document`, detached subtrees, per-node versions, and a **version tree represented as a document** with LSA-based merge-base computation (Fischer & Huson LSCA); manifesto encoded as data | Directly ported: `foundation/document` and `foundation/version` are the Scala.js ports of `Olw/Document/*` and `Olw/Version/Version.elm` |

Lessons carried forward:

- *Documents all the way down.* A repository, a directory tree, a version
  tree, a language definition — all are documents (olw-p3 `notes.txt`).
  The manifesto itself ships as a sample document (`EditorSample`).
- *Views are derived, not stored.* p1's per-context interpretations became
  `Language.views` — renderings computed from the canonical tree.
- *Keep the UI layer formally simple.* After p3 collapsed under UI
  complexity, the current UI is a small Elm-style runtime, not a stack of
  frameworks.

## 2. Core data model (`foundation/document`)

The substrate everything is built on — a direct port of olw-p4, extended:

- **`Document[A]`** — a persistent tree stored as `rootId` + `Vector[Node[A]]`.
  Node ids are indexes into the vector and are **never reused**. Each node
  carries `version`, `data`, `childIds`, and `parentId`. Pure, immutable,
  and cheap to snapshot — which is what makes undo trivial.
- **`DetachedNode[A]`** — an id-less tree for building content before
  insertion. `buildDocument` assigns ids post-order, so each subtree
  occupies a contiguous id range. The `Detached` object provides compact
  builder syntax (`n(s("a"), sl("b"), il(2))`).
- **`Edit.scala`** — pure edit operations returning
  `Either[String, Document[A]]`: `insertNode`, `cutNode` (unlinks; the
  node stays in the vector), `pasteNode` (re-attaches, keeping identity),
  `moveNode` (atomic cut+paste with a cycle check), `extractSubtree`,
  `updateNodeData`, `updateNodeChildIds`, `reachableIds`, `compact`, and
  `validate` (structural integrity check).
- **`compact`** — garbage-collects unreachable nodes, rewriting ids and
  returning an old→new id map. It is a deliberate **lineage boundary**:
  callers must remap any `InternalNodeRef`s in node data.
- **`NodeData`** — the concrete payload type: `StringData`, `IntData`,
  `FloatData`, `InternalNodeRef(nodeId)`, `ExternalNodeRef(ref)` — a
  transclusion-style cross-document reference whose
  `ExternalNodeReference(documentUrl, documentVersionId, nodeId)` reads
  as `url@version#node` — **`GapData(text)`** (a hole, §4), and
  **`SourceRef(nodeId)`**, a provenance back-pointer meaningful only in
  derived trees (§4): the node in the *source* document this node was
  produced from. Unlike `InternalNodeRef` it is never remapped by
  `compact` — the source document is a different address space.
- **`Outline`** — a text codec for raw documents: one line per node,
  indentation for nesting, and surface syntax for each `NodeData` variant
  (`"abc"`, `42`, `1.5`, `ref:5`, `ref:url@v#n`, `?text`). It doubles as
  the serialization format for stored documents.
- **`Store`** — a pure, immutable, versioned document store keyed by URL.
  `put` appends a version (0, 1, 2, …), `resolve` dereferences
  `url@version#node` addresses, and heads are tracked per URL. It is
  append-only, so pinned refs never dangle. The `Store` resolves external
  refs and underlies multi-document workspaces.
- **`Show`** — debug rendering, including node versions and links.

## 3. Versioning (`foundation/version`)

Version history is itself a document: a `Document[Version[A]]` is a DAG in
which each node has 0, 1, or 2 parents (the second via merge).

- `appendVersion` adds a child version; `mergeVersions` adds a merge node
  pointing at both parents.
- The merge base is the **LSCA** (lowest single common ancestor), found
  via a maintained **LSA** (lowest single ancestor) link per node —
  Fischer & Huson, *Information Processing Letters* 110.8-9. `getLsca`
  walks two root paths in O(h).

**Where it is used.** The editor's history is this DAG. For an unsynced
document, `EditorModel` holds a session-local
`Document[Version[EditorSnapshot]]` plus a `versionId` cursor: every edit
records a version, undo branches rather than truncates (`EditorHistory`),
and a history column renders the tree with click-to-jump
(`EditorHistoryView`, `GoToVersion`). For a synced document the DAG is
rebuilt from the shared Automerge change graph (`EditorSynced`), so every
peer sees the same author-tagged tree, and two-dependency changes become
real merge versions via `mergeVersions` (§7). Unsynced session history is
not yet persisted.

History is designed as two layers (per `RELATED_WORK.md`):

- **Operational layer** — a CRDT (Automerge) for live multi-user
  convergence; its change graph doubles as the durable draft history.
- **Curated layer** — deliberate, branchable, truncatable checkpoints
  (Upwelling/Patchwork-style, with git's objects/refs/working-tree split
  as the structural template), realized today as the `Store`'s
  append-only `url@v` versions.

## 4. Typed documents, languages, and holes

**Typed documents** (`foundation/document/Typed.scala`): a document's *type*
is its root's first child — an `ExternalNodeRef` naming the document that
defines it — and the second child is the *content* subtree (the AST). This
is the same convention as olw-p1/p2's header child. Untyped documents are
"raw".

**Languages** (`foundation/language/Language.scala`): a `Language` gives
meaning to a type reference:

```scala
trait Language {
  def typeRef: ExternalNodeReference          // identity
  def name: String
  def views: List[String]                     // derived views, e.g. "print", "eval"
  def defaultView: Option[String]             // first view by default
  def render(view, doc): Either[String, DetachedNode[NodeData]]  // a view tree
  def parse(text): Option[DetachedNode[NodeData]]      // optional text syntax
  def parseCell(text): Option[DetachedNode[NodeData]]  // optional cell grammar
  def langDoc: Document[NodeData]             // the document typeRef names
}
```

**View trees.** A derived view is itself document data: a detached tree in
a minimal presentational vocabulary (`foundation/view`) — `block`, `flow`,
`heading`, `grid`/`row`/`cell`/`hcell` containers, scalar leaves as text
runs, `GapData` as holes, and `input`, an editable text region. A single
recovering interpreter (`ui/view/ViewTreeView`) turns any view tree into
an fp `View`, so languages emit data, never DOM; unrecognized structure
degrades to hole chips rather than failing. A `SourceRef` leaf as an
element's first child tags it with provenance — the source node it came
from — so "click a rendered value to select its producer" is uniform
machinery, not per-language behavior.

`input` reuses that convention with a stronger meaning: its `SourceRef`
names the node the text box *edits*. A commit parses the text with the
language's cell grammar (`parseCell`) and rewrites that node in place
(`replaceNodeContent`; the node keeps its id, so refs into it keep
resolving). This is the workbench's first projected editor: the view is
not only derived from the document, it writes back. The same vocabulary
is what a language document's view spec will eventually encode, and a
stored view tree can be inspected or edited like any other document.

**Holes** (*Never broken*). `GapData` marks text or structure that doesn't
fit yet. Parsers are total — every input parses, with holes holding the
verbatim text that didn't fit — so ongoing edits heal the tree instead of
failing (Hazel-inspired typed holes). Evaluation, rendering, and language
resolution degrade the same way: a hole or cycle blocks only what depends
on it.

**Language definitions as documents** (`foundation/langdef`) — the
bootstrap path, and *Frictionless* in practice. A `LanguageDef` is a
language as data: identity, a schema of node kinds, a compositional
grammar (`GrammarDef`, the data twin of the `foundation/grammar`
combinators), view bindings, and a cell grammar. `langDefDoc` encodes a
def as a typed `lang:` document; `langDefFromDocument` reads it back
recoveringly; `interpret` turns grammar data into a total parser; and
`DefLanguage` turns a def into a live `Language`. Semantics the def names
by string — lexers, view renderers, builtin cell parsers — resolve
through an `Impls` bundle: honest scaffolding while behavior still lives
in code.

Resolution is store-first (`examples/Resolve.scala`): `languageForRef`
looks up the type ref's URL in the document store, and if the head there
is a `lang:` document, that document *is* the language. Otherwise it
falls back to the code registry (`allLanguages`: `ExprLanguage`,
`SheetLanguage`, and `DocLanguage`, the code-resident language that
`lang:` documents are themselves typed by — where the regress stops).
Saving an edited `lang:` document therefore re-interprets every dependent
document on its next render (*Reactive*); deriving migrations for those
dependents when a definition changes (*Evolvable*) is open. Malformed defs
degrade to holes: missing refs, cyclic grammars, and cyclic documents are
all guarded.

**Expression language** (`ExprLanguage`, `Expr`/`ExprDoc`, `ExprLex`,
`ExprParse`, `ExprView`): the demonstration language. It has int and
float literals (`Num`: integer arithmetic stays integral, division and
floats are decimal, `Num.show` rounds away float noise for display);
`+`, `-`, `*`, `/` with precedence-aware printing; `ref:N` references
stored as `InternalNodeRef` (so "go to definition" is the ordinary ref
jump); and grouping by tree structure. It provides `print` and `eval`
views and a total, recovering parser. Views render through `LocExpr`, a
located read that keeps each node's id, so every piece of the view tree
carries its source id. Evaluation resolves refs through the enclosing
document — a referenced cell's own subtree is evaluated, so formulas
compose — and a visiting set reports ref cycles as holes instead of
diverging. The parser also emits a **span map** (`Map[List[Int], Span]`,
tree path → source range), groundwork for mapping text edits onto nodes
instead of reparsing whole cells. `Expr.print` round-trips holes.

**Sheet language** (`Sheet`, `SheetLanguage`): the demo app — a typed
document whose content subtree is `sheet > row/hrow > cell`. Label cells
(plain string leaves) pass through; every other cell is read as an
expression and evaluated, with `InternalNodeRef` leaves addressing other
cells. The `sheet` view renders the computed grid; `formulas` renders
each cell's expression instead of its value. Cells parse with the
expression language's grammar, referenced by type ref, so editing
`lang: expression` changes sheet cells too. A hole or cycle in a
referenced cell propagates only to the cells that use it — the app
recovers like the documents it is made of. `sampleDoc` seeds the demo's
household budget.

**Text surface** (`DocText.scala`): typed documents use their language's
`parse`/`print` for the content; everything else falls back to
`Outline`. `holeCount` feeds the status bar.

### Defining languages by MEANS rules (design, not yet built)

Today a language's meaning is scattered: the grammar maps `"*"` to the tag
`"mul"` inside a combinator tree, the schema separately declares `mul` as
`Fixed(2)`, `Expr.pp` re-encodes precedence for printing, and eval matches
`Mul` in Scala — four artifacts held together by a string. Authoring one
requires knowing parser combinators (`Signum`, `Junk`, `Phrase`, stop
sets), much of which is recovery machinery leaking into the definition.

The target is the way mathematics is *taught*: a notation is introduced by
saying what it means in terms of something already understood ("3 × 4
means 4 + 4 + 4"; "5% means 5 per hundred"). Following OMeta and VPRI
STEPS — parsing and tree rewriting as one kind of pattern match, and
definitions small enough to describe themselves — a language becomes a
set of rules:

```
a + b    MEANS add(a, b)
a - b    MEANS sub(a, b)
a * b    MEANS mul(a, b)
a / b    MEANS div(a, b)
-a       MEANS neg(a)
(a)      MEANS a
|a|      MEANS abs(a)
a %      MEANS a / 100

binding, tightest first:
  -a
  a %
  a * b,  a / b
  a + b,  a - b

examples:
  1 + 2 * 3   MEANS 1 + (2 * 3)
  8 - 3 - 2   MEANS (8 - 3) - 2
```

**One rule, every concern.** Read left to right, a rule is a production;
its meaning names a node kind and its arity (the schema); read right to
left, a constructor meaning is the printer — minimal-parenthesis printing
follows from the binding list, replacing hand-written `Expr.pp`. Several
surfaces may mean the same tree (`a × b`, `a times b`, `product of a, b`
all `MEANS mul(a, b)`), so choosing a notation means choosing which rule
set prints the view — *Polysyntactic* as data.

**Precedence is an ordered list**, tightest first — the PEMDAS card, not
binding-power numbers and not inferred. Associativity defaults to left and
is overridden by example (`a ^ b ^ c MEANS a ^ (b ^ c)`). Worked examples
are live checks (*Learnable and self documenting*, *Reactive*). Grouping
needs no special combinator: `(a) MEANS a` is an ordinary rule.

**Placeholders and WHERE.** In `a + b`, `a` and `b` are placeholders; the
rest is literal. A WHERE clause constrains what may fill them:
`ref:c,r MEANS cell(c, r) WHERE c, r: whole number`. A projectional
surface distinguishes placeholders structurally (styled chips, as math
italicizes variables), so stored rules need no escaping; the text surface
needs a convention (open question). Under *Adjustable verbosity*, WHERE
is detail: hidden at the terse level, revealed on zoom-in (§6). Hidden is
never absent:

- a WHERE implied by the layer below (`add` takes numbers ⇒
  `a, b: number`) shows nothing tersely, and appears marked as inferred
  when zoomed in;
- a WHERE narrower than inference leaves a marker in the terse view, like
  a fold indicator;
- a violated WHERE is enforced at every zoom level and surfaces as a hole.

Detail-level zoom stays within the rule's own layer and never descends
into the meaning's implementation; that is the separate *implementation*
axis (§6).

**Layers form a DAG.** A meaning may refer only to things lower in the
dependency graph, never back up. Dependencies are between *rules*, not
languages, so one language may bundle several layers: `a % MEANS a / 100`
sits above the `/` rule, which sits above `div`, which bottoms out in a
builtin (today's `Impls` registry — the "already understood" arithmetic).
Authors don't number layers; a rule's layer is one above the highest rule
its meaning uses. Show/hide implementation walks this graph downward.

- *Prevention in the interface*: completion on a meaning offers only rules
  already below it, so the structural editor cannot author a cycle
  (*Discoverable*, *Immediately typed*).
- *Tolerance in the model*: text edits and CRDT merges can still produce
  one — two collaborators each adding a rule that uses the other's merge
  cleanly in Automerge. A cycle renders as a hole at the edge that closes
  it, as ref cycles already do in evaluation (*Never broken*).

**Documents keep what the user wrote.** `5%` is stored as `percent(5)`,
not `div(5, 100)`. Lower layers are derived, consistent with views being
derived rather than stored, and printing round-trips the user's notation.

**When a meaning resolves.** In the sheet, `ref:3,4` (or `C4`) means "the
cell at column 3, row 4" — but storing the position would retarget the
ref when a row is inserted, the classic spreadsheet bug. Instead the
meaning resolves at parse time to the cell's stable node identity
(today's `InternalNodeRef`) and prints back as its current position.
Rules need a way to mark parse-time versus evaluation-time meanings.

**Engine.** A rule that begins with a literal (`-a`, `|a|`, `if …`) is
prefix; one that begins with a placeholder (`a + b`, `a %`) is infix or
postfix. That is exactly Pratt parsing: the binding list supplies binding
powers, and left recursion is a non-issue. Recovery lives in the engine
(a missing operand folds to a hole, as `chainLeft` does today), keeping
`Junk`/`Phrase` out of definitions. The literal symbols in the rules
define the tokens (scannerless, as in Ohm), retiring the builtin `expr`
lexer.

**Path in this codebase.**

1. Compile rules plus the binding list to a Pratt parser producing
   `PTree`s. The rule form of `exprDef` must behave identically to
   `ExprLanguage` — the existing regression anchor.
2. Derive the schema (`NodeKind`) and the printer from the same rules.
3. Encode rules in `lang:` documents as `means(pattern, meaning)` nodes.
   The MEANS notation becomes the text surface of the language-definition
   language, eventually described by its own rules.
4. Bind semantics to builtins first (`add(a, b) MEANS builtin num-add`),
   moving to rule-defined meanings where possible.

More examples the notation should handle: comparison chains
(`a < b < c MEANS a < b and b < c`), juxtaposition
(`n x MEANS n * x WHERE n: number literal`), units
(`n kg MEANS quantity(n, kilogram)`), mixfix
(`if c then a else b MEANS choose(c, a, b)`), sheet ranges
(`a .. b MEANS range(a, b)`), and non-textual input
(`drag cell x onto cell y MEANS set y to ref(x)`).

Open questions: the plain-text placeholder convention; how parse-time and
evaluation-time meanings are marked; and whether alternative notations
live in one language document or as notation documents layered over a
shared one.

## 5. UI architecture

### `ui/fp` — the component runtime

A small Elm/Tyrian-style framework, built after p3's lesson:

- `Component[I, O]` — `init`, `update` (pure: state + input →
  `Update(state, outputs)`), `view` (the desired `View[I]` tree), and
  `children` (desired keyed child descriptions). One input channel carries
  everything: view events, parent-pushed props, and routed child outputs.
  Effects are *output values*, interpreted by whoever mounts the component.
- `Runtime` — a single FIFO dispatch queue (no re-entrancy). The queue is
  the intended hook for an input log — itself, per the *Language*
  principle, a document.
- `Render`/`Patch` — `View` → DOM, then positional patching that reuses
  nodes where the shape matches, which keeps focus and in-progress input
  alive across re-renders. Event handlers resolve through a per-element
  table, so listeners are never re-bound. `data-focus`/`data-scroll` give
  declarative post-render focus and scroll.
- `Reconcile` — keyed child instances: same key and component → reuse and
  push changed input; new keys mount; missing keys are destroyed.
- `View.Managed` — the escape hatch for imperative widgets that own their
  DOM (CodeMirror); keyed, and remounted when the key changes.
- `Handle`/`Subject` — the external face of a mounted tree: push inputs,
  subscribe to outputs.

### `ui/editor` — `DocumentEditor`

An outliner-style structural editor for raw `Document[NodeData]`, built as
an fp component and split one concern per file (`EditorModel`,
`EditorUpdate`, `EditorSession`, `EditorDocOps`, `EditorClip`,
`EditorKeys`, `EditorHistory`, `EditorHistoryView`, `EditorSynced`,
`EditorView`, `EditorRow`, `EditorSample`). It is keyboard-driven:
Enter/Shift+Enter insert, Tab indents, F2 or double-click edits inline,
subtrees cut/copy/paste, `Compact` garbage-collects, and undo/redo walk
the branching version history (§3). The sample document is the manifesto
rendered as a tree — dogfooding.

### `ui/typeddoc` — `TypedDoc`

The workbench's typed-document pane. It composes `DocumentEditor` as a
child and offers several surfaces (`DocView`): **Editor** (structural),
**Text** (a `Managed` CodeMirror cell bound to `docText`/`applyDocText`),
and **Derived** views offered by the document's language (`print`,
`eval`, `sheet`, `formulas`). Derived views also stay live in a sidebar
while editing. Shown alone, a derived view is the *app* surface — the
spreadsheet without the machinery — with two exits: **Raw** (this
document's representation) and **Definition** (F12: follow the type ref
to the language document — the app's implementation). Notable mechanics:

- The editor child stays mounted in every view (`display:none`),
  preserving selection and undo across switches.
- `doc` vs `pushedDoc`: editor-originated changes update the mirror but
  are never pushed back down (that would reset the child's state);
  text-originated changes are pushed.
- `textEpoch` bumps remount the CodeMirror cell only when the text was
  re-derived externally, so typed input never echoes.
- The document `store` arrives as a prop and is the language-resolution
  context (§4), so an edited `lang:` document takes effect immediately.

This pane is the working proof of *polysyntactic* editing: several
surfaces over one canonical document.

### `ui/text/CodeMirror`

A thin dynamic-`JSImport` wrapper over CodeMirror 6 (updateListener →
`onChange` per document-changing transaction). A comment documents the
6.x-only package constraint.

### `ui/workbench` — `Workbench`

`Workbench` is the application shell mounted by `Main`. Every decision —
open, save, follow, sync, branch — is a pure transition over
`WorkbenchModel` (`WorkbenchUpdate`, `WorkbenchEvents`, `WorkbenchDocs`),
and every effect is a declared child: invisible *seam* components in
`ui/fx` (`StorageHub`, `Prompt`, `Random`, `SyncHub`) receive requests as
props and report results back as inputs. Document-store decisions live in
the pure `Workspace` value; markup lives in `WorkbenchView`;
`WorkbenchShell` is the browser edge.

The view is a sidebar listing stored document heads (with New), a
toolbar (a switch between the **document** pane hosting `TypedDoc` and a
**components** demo pane, plus Save, Sync, and Reset), and a status bar;
the expression and sheet samples load from the pane's own toolbar. On first
load, `DemoDocs` seeds each registered language's `langDoc` at the URL
its type ref names, plus the household budget — never overwriting a URL
the store already holds. Ctrl+Enter on a ref node follows it: internal
refs select their target in place; external refs bubble up, resolve
through the `Store`, and open the pinned version at the referenced node.

## 6. Navigation

Navigation follows from two manifesto principles: *polysyntactic* (the same
gesture is interpreted per surface and language) and *language*
(keystrokes and clicks are a language the software interprets). The
design has two layers:

- *Mechanism* — uniform. Every jump reduces to moving a selection within a
  document or following a ref, and refs are first-class (`InternalNodeRef`,
  and `ExternalNodeRef` with `url@version#node` addressing resolved
  through the `Store`). A *nav target* is a `(document address, node,
  view, jump|peek)` tuple, so landing in a particular surface — or peeking
  inline — uses the same machinery, not a special case.
- *Relations* — per-language. In a typed document the cursor sits on a
  *name*, and each command asks a different semantic question: what
  defines it, what types it, what uses it, what exemplifies it. `Language`
  therefore grows a resolver capability — `resolve(relation, doc,
  position): List[NavTarget]` alongside `views`/`render`/`parse`. Raw
  documents are the degenerate case: the "resolver" is the ref literally
  stored in the node (today's Ctrl+Enter).

The relation vocabulary has two tiers. A *foundational* set is fixed by the
workbench — named, keybound, and guaranteed across languages (definition,
usages, type-of, examples). The language supplies the implementation, but
the platform owns the vocabulary, so applications inherit it (*Transitive*).
An *open* set is advertised per language, like `views` — "go to test",
"go to migration" — and listed by the command palette (*Discoverable*).
Both tiers resolve through the same interface and return ordinary nav
targets, so peek, jump history, and results-as-documents apply uniformly
to language-invented jumps.

**Spatial navigation** — depends on the active surface's syntax:

- *Forward/backward* (left/right) and *up/down* move through the
  projection: by token or line in text, by sibling or parent in the
  structural editor. Today arrow up/down traverse the outline linearly;
  collapse-to-parent and expand-to-child on left/right are the natural
  additions.
- *Structural jumps*: go to parent/child, next/previous sibling, and
  next/previous node of the same type or category (next definition, next
  hole). The raw-document editor is just another surface, so these
  primitives apply everywhere — raw editing is not a special mode.
- *Expand/shrink selection* — the selection itself walks the tree.

**Semantic navigation** — distinct commands over the resolver interface:

- *Go to definition* / *find usages*: the binding relation and its
  inverse. Definition is a resolver query (name under cursor → defining
  node); usages come from an inverse ref index over stored documents —
  meaning-aware, since refs are structured rather than found by text
  search.
- *Go to examples*: under *Learnable and self documenting*, examples live
  in documents linked by refs. Links may be stored (a definition document
  references its examples) or discovered (the inverse index filtered by
  "example-ness"). Multi-valued relations like usages and examples return
  a list of targets, presented as its own document whose entries are
  themselves nav targets.
- *Go to type*: two readings. The *document's* type is its header ref
  (mechanism-level follow-ref on the first child — today's F12); the type
  of the *named entity* under the cursor is a semantic `type-of` query.
  Revealing or hiding types is a projection concern (detail level, below).
- *Next/previous hole or error*: holey documents (`GapData`) make "next
  problem" a first-class jump; `holeCount` already shows in the status
  bar.
- *Provenance navigation*: click a value in a derived view to jump to the
  node that produced it — upstream along the derivation. Implemented for
  view trees via `SourceRef`.

**Cross-surface navigation** — the selection follows the user across views:

- *Go to/from rendering* re-projects the same node into a different view.
  It is a nav target carrying a `view`, so it belongs to the mechanism
  layer rather than being a semantic query. Position follows via stable
  node identity plus the parser's span map: a node selected in the
  structural editor lands on the corresponding text range, not the top of
  the cell — and vice versa.
- *Peek*: `ExternalNodeRef` is already transclusion-style; peek-definition
  renders the target inline instead of navigating away.

**Zoom navigation** — three orthogonal axes, all per-user projections of
the same document, never edits:

- *Detail level* (terse/verbose — the *Adjustable verbosity* principle):
  zooming out elides — types and constraints hidden, signatures only;
  zooming in reveals. It stays at the current layer of meaning: more of
  *what* the content says, never *how* it is carried out. Fold/collapse is
  the degenerate local case.
- *Implementation* (show/hide implementation): descend from a construct to
  what it means in the layer below — a rule to its meaning, a meaning to
  its builtin (§4, *Defining languages by MEANS rules*), an app to its
  language document (today's Definition/F12 exit). Orthogonal to detail
  level: an implementation can itself be shown tersely or verbosely.
- *Scope* (hoist/focus): a subtree becomes the temporary root of the
  view — the outliner's zoom. Orthogonal to both.

**History** — navigation state is data, and eventually a document:

- *Jump history*: back/forward across jumps (like browser back or an
  IDE's navigate-back). "Return from example/rendering" is a special case;
  a general stack makes every jump non-destructive.
- *Version navigation*: the version DAG is a document (§3), so
  previous/next version, go to branch point or merge, and "next change in
  a diff" can reuse the same traversal machinery. Today: click-to-jump in
  the history column.

**Addressing and search**

- *Go to symbol / quick open / command palette*: fuzzy matching over
  names, document URLs, and node ids. Every node is already addressable as
  `url@v#n`, so a jump target is literally a parsed ref (*Discoverable*).
- *Find text*, *bookmarks*, *breadcrumbs* (a clickable root→selection
  path): the mundane floor.

**Collaborative navigation** — once presence exists (§7), "follow a
collaborator's cursor" and shared selections for pairing fall out of
presence being broadcast state.

## 7. Persistence and collaboration (`data/`, `collaboration/`)

- **`IndexedDBStore`** — Future-based access to four object stores
  (`projects`, `automerge_docs`, `settings`, `documents`) using
  `js.Dynamic` payloads, so the store stays agnostic about what it
  persists. **`DocumentRepo`** maps the `documents` store to the `Store`:
  one record per `url@version` holding outline text; `loadAll` re-parses
  every record into a `Store` and derives heads as the max version per
  URL — no separate index to keep consistent. **`AutomergeRepo`** keeps
  each synced document's Automerge bytes in `automerge_docs`.
- **Automerge** — the operational layer. `DocSession` holds each open
  document's outline text in an Automerge `{text}` doc (3.x strings merge
  per character), exchanges sync messages pairwise with each peer over a
  `SyncTransport`, and persists the bytes so a reloading tab resumes
  rather than restarts. Synced text is reparsed into `Document[NodeData]`
  on arrival: the CRDT syncs the serial form, and the document model stays
  canonical.
- **`collaboration/`** — `automerge/` (the facade, plus change info and
  revert computation), `backends/` (`SyncTransport` and
  `BroadcastTransport`: tab-to-tab sync with no server), `DocSession` (the
  session logic), `SessionUndo` (per-actor undo stacks), and `Identity`
  (session actor ids and names). Signaling (for real P2P across machines)
  and presence (cursors, selections) are not built yet.

**Backup and sharing are separate toggles.** Both use the same mechanism —
Automerge pairwise sync treats every counterparty (a server, a sibling
tab, a collaborator) as a peer with its own sync state — and differ only
in policy:

- **Backup** is *account-level* infrastructure: once signed in, a private
  server peer absorbs each document's draft layer. It never initiates
  changes, so it adds no history entries; restoring a device is the
  existing resume-from-saved-bytes path. Because Automerge sync is
  transitive — anything received flows to every peer — the server must
  *not* relay between clients; a per-document ACL keeps "private" from
  leaking, enforced by the server, not the sync layer.
- **Sharing** is a *per-document* act: inviting a peer onto a document
  (its Automerge doc id is the capability) opens sync with that peer.
  Today's Sync toggle, which only controls tab-to-tab BroadcastChannel
  sync, becomes this per-document switch once real transports exist.
- **Local-only** remains available: backup off and not shared reproduces
  today's toggle-off state, minus durability.

A user experimenting privately runs backup on, sharing off: off-device
durability without visibility to collaborators. The server peer can be
built two ways: speaking the sync protocol live (incremental and safe
across devices — the intended direction) or, as a stepping stone,
periodic `Automerge.save` snapshots (simple, but last-writer-wins across
devices). Encrypting the synced bytes would keep the server unable to read
them, but demotes it to the snapshot model, since it could no longer take
part in merging.

**Unified history** (implemented for tab sync). A synced document has one
history, not one per tab: the *Automerge change graph itself* is projected
as the history. Every change carries a hash, an actor id, dependency heads,
and a timestamp, and all peers converge on the same graph by construction.
History entries are `(hash, actor, time, label)` references;
document-at-version is derived from the graph rather than stored as
snapshots. Concurrent edits produce divergent heads, and the change that
joins them is a genuine two-parent version (`mergeVersions`, §3).

Consequences:

- *Undo is scoped to one's own changes.* Ctrl+Z means "take back *my* last
  change," never "rewind the shared log" — the settled answer across
  collaborative editors (Google Docs transforms inverse ops forward, Yjs
  un-deletes tombstoned items, Figma scopes undo per user; Etherpad's
  global undo is the cautionary counterexample). The session keeps a
  stack of the changes it authored; undo appends a revert change computed
  as an *inverse splice at the current frontier*. The change's
  before/after texts give its span (longest common prefix/suffix),
  positions are carried forward through Automerge cursors, and the revert
  is declined if the span no longer holds exactly what the change
  inserted — deleting a peer's text inside it would be worse than
  refusing. This handles the frontier and interleaved cases uniformly. (An
  anchored revert via `changeAt` was considered and dropped: setting the
  pre-change text at the old heads is a no-op.) Each undo is an ordinary
  labeled change — visible to peers, itself undoable, no sync disruption;
  repeated undos keep walking back one's own changes, and an undo feeds
  the redo stack rather than the undo stack. Stacks are session-local and
  empty on resume; rebuilding them from actor-tagged history is future
  work.
- *Navigation is local.* Clicking a version moves the editor's view only —
  a `DocViewed` output, distinct from `DocChanged`, that sends nothing to
  peers. The pin survives incoming remote edits via hash-stable version
  identity, so browsing isn't disturbed while a peer types.
- *Editing a checked-out version forks.* A jump into history followed by
  an edit means "continue from here," realized as a *branch*: a separate
  draft seeded at that version's text with a back-ref to its origin,
  syncing on its own channel (Upwelling's private-draft pattern — branches
  sidestep shared-undo ambiguity entirely). The main document is
  untouched; collaborators get a "branched at #n — rejoin?" prompt and opt
  in rather than watching their document revert under them. Merge-back is
  a deliberate later gesture (manual today). The gesture carries the
  intent, so no distance heuristic is needed: Ctrl+Z is always
  incremental; checkout + edit is always a fork.
- *Identity.* Every session takes a *unique* actor id on attach — tabs
  resuming the same persisted bytes would otherwise collide on
  `(actor, seq)` — plus a generated display name, both kept per tab in
  `sessionStorage`. Each change carries its writer's name as its message,
  so every peer renders the same labels. Once accounts exist, an
  `actorId → name` map synced inside the document can re-key names
  without changing the mechanism.
- *Granularity* (planned). A history entry is an Automerge change, not a
  keystroke. `DocSession` should coalesce rapid consecutive local edits
  into one change on a short debounce, so a typing burst becomes one
  undoable unit; a remote message, a persist, or session close would
  flush pending edits immediately.
- *Visibility.* Peers sharing a document see its full draft history;
  checkpointing is not a visibility boundary — only sharing is. Ephemeral
  state (peer cursors, selections, eventually keystroke-level live
  preview) belongs to the future presence channel, which broadcasts state
  without touching the change graph.

**Curated checkpoints.** The `Store`'s append-only `url@v` versions are
the git-like commit layer over the operational log: a save is a labeled,
deliberate, addressable checkpoint (`url@version#node` refs already
resolve them). The operational history is complete but unbounded — the op
log may be compacted between checkpoints — while the curated layer holds
the meaningful, branchable story.

**History of history.** Operations that change history itself — restores,
branch creation, prunes, compactions, curated checkpoints — need a synced
meta-log, not unilateral deletion: a locally pruned change is simply
re-offered by any peer that still holds it, so "prune below hash X" must
itself be a shared, versioned operation that peers honor. Ephemeral
navigation never enters this log; it is the audit trail that makes
cleanup safe under sync.

## 8. The retired scaffold

The first commits landed a scaffold layer — `foundation/project`
(`Project`/`Definition`/`Workspace`), `foundation/structure`
(`Schema`/`SchemaRegistry`), `foundation/typing`
(`TypedGap`/`TypedGapManager`), `foundation/dsl` (`Expression`/`DSLParser`),
plus `DiagramRenderer` and a `CollaborationManager` stub — written in a
`js.Dynamic`-heavy, mutable-global style that never interoperated with
`Document[NodeData]`. It was deleted rather than integrated, since the
current design supersedes each piece:

| Retired scaffold | Superseded by |
|---|---|
| `TypedGap`/`TypedGapManager` | `GapData` holes in `foundation/document`, recovering parsers in `foundation/grammar` |
| `Expression`/`DSLParser` | `Expr`/`ExprParse` in `examples/`, `Language` and `foundation/langdef` |
| `Schema`/`SchemaRegistry` | Schemas as documents — `LanguageDef` node kinds today (descriptive; not yet checked) |
| `Project`/`Definition`/`Workspace` | The document `Store`, the pure `Workspace` value, and the documents sidebar |
| `CollaborationManager` stub | `DocSession` over real Automerge |
| `DiagramRenderer` | Document-driven visualization as derived views — future work |

When reading early history, treat anything `js.Dynamic`-heavy with mutable
global singletons and "basic implementation — can be extended"
placeholders as belonging to this removed generation.

## 9. Build and test

- Scala 3.9 + Scala.js via Scala CLI (`project.scala`) and scalajs-dom; ES
  module output → `app.js` → webpack → `bundle.js`, loaded by
  `index.html`.
- `build.bat` / `build.sh` run the full pipeline (`--force` packaging is
  handled by deleting `app.js` first); `test.bat` / `test.sh` run the
  suite. The test scripts point `TEMP`/`TMPDIR` at `./.tmp` so Node can
  resolve `node_modules` from the linked test bundle.
- Tests: ~255 munit tests across `test/foundation/**` (document ops,
  outline codec, typed documents, grammar combinators, version LSCA),
  `test/examples/**` (expression and sheet languages, language defs and
  their document encoding), `test/collaboration/**` (DocSession
  convergence under real Automerge in Node), and `test/ui/**` (editor,
  pane, and workbench update functions, fp runtime, view trees) — the
  pure core is the tested core.
- npm dependencies: CodeMirror 6 packages and `@automerge/automerge`.

For what currently works, what's in progress, open questions, and next
steps, see `STATUS.md`.
