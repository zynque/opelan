# Opelan — Design

Opelan (**Op**en **L**anguage **A**pplicatio**n**) is a browser-based language
workbench: a tool for building software in which programs, documents, and the
tooling itself are all represented as versioned, structured trees that can be
viewed, edited, and interpreted through multiple surfaces. It is implemented in
Scala.js, bundled with webpack, and targets local-first operation in the
browser (IndexedDB persistence, no required server).

This document summarizes the project's goals, lineage, and architecture.
Companion docs: `MANIFESTO.md` (principles), `PRINCIPLES.md` (rationale
behind them), `ADOPTION.md` (why
workbenches haven't spread, and the intended answers), `RELATED_WORK.md`
(survey of related systems), `README.md` (build/run), `STATUS.md` (current
state and next steps), `AGENTS.md` (code conventions).

## 1. Vision

`MANIFESTO.md` defines the product vision in three groups of principles:

**Open**

- *Open source* — the tool shares its code.
- *Frictionless* — the environment is modifiable from within itself; no
  external toolchain required to change the tool you are using.
- *Transitive* — applications built on the platform inherit the platform's
  capabilities (editability, history, collaboration) by default.

**Language oriented**

- *Language* — all user input (keystrokes, gestures, clicks) is a language the
  software interprets.
- *Programmable* / *Discoverable* — the language is scriptable and offers
  choices (completion, menus).
- *Immediately typed* — validation happens as code is typed, not at compile or
  run time.
- *Transitively typed* — applications built on the platform inherit its type
  system (or a domain-specific subset).
- *Polysyntactic* — many surface representations over one document; users pick
  their preferred notation.

**Application**

- *Available & responsive*, *online/offline* — web-first, fully functional
  offline, syncs when online.
- *Preserve history* — version control is built into every application;
  diffs are meaning-aware.
- *Reactive* — effects of a change are visible immediately (live results,
  dependency-change notifications).
- *Real-time collaboration* — multi-user editing.
- *Self documenting* — documentation and examples live alongside the code.

## 2. Lineage: the olw-p* prototypes

The repo keeps four earlier prototypes under `old-olws/` (see also the public
repos `zynque/olw-p1` … `olw-p4`). Each explored a piece of the design:

| Prototype | Tech | What it explored | What survived |
|-----------|------|------------------|---------------|
| **olw-p1** "FSOLP" | F# / WPF | *Phrase graph*: every phrase has a type (first phrase-child), parents, contents, and **interpretations/quotations keyed by Context** — the origin of "one node, many representations". First-class deltas/changesets with dirty-flag propagation for re-render and undo. | The typed-document convention (type = first child); deltas → today's persistent-document ops + undo stacks |
| **olw-p2** | CoffeeScript | Immutable document store: append-only node array, copy-on-write path updates yielding a new `rootId` per version; explicit version lists; a *live repository* façade over the immutable store; a document **header URL selects its interpreter** | Immutable-node-array document; versions as data; type-directed interpretation |
| **olw-p3** | Scala.js client/server | `Document[T]` / `NavigableDocument` shared model (persistent tree + parent index); editable text segments, tree editors, virtual-dom experiments | Document shape refined; lesson that a hand-grown ad-hoc UI layer becomes unmanageable — motivation for `ui/fp` |
| **olw-p4** | Elm | The most mature model: persistent array-backed `Document`, detached subtrees, per-node versions, and a **version tree represented as a document** with LSA-based merge-base computation (Fischer & Huson LSCA); manifesto encoded as data | Directly ported: `foundation/document` and `foundation/version` are the Scala.js ports of `Olw/Document/*` and `Olw/Version/Version.elm` |

Design lessons carried forward:

- *Documents all the way down.* A repository, a directory tree, a version
  tree — all are documents (olw-p3 `notes.txt`). The manifesto itself ships as
  a sample document (`EditorSample`).
- *Contexts / views are derived, not stored.* p1's interpretation maps become
  today's `Language.views` — renderings computed from the canonical tree.
- *Keep the UI layer formally simple.* After p3's collapse under UI
  complexity, the current UI is a small Elm-style runtime, not a framework
  soup.

## 3. Core data model (`foundation/document`)

The substrate everything is built on — a direct port of olw-p4, extended:

- **`Document[A]`** — a persistent tree stored as `rootId` + `Vector[Node[A]]`;
  node ids are indexes into the vector and are **never reused**. Each node
  carries `version`, `data`, `childIds`, `parentId`. Pure, immutable, cheap to
  snapshot — which is what makes undo trivial.
- **`DetachedNode[A]`** — an id-less tree used to construct content before
  insertion. `Build.buildDocument` assigns ids post-order so each subtree
  occupies a contiguous id range. The `Detached` object provides compact
  builder syntax (`n(s("a"), sl("b"), il(2))`).
- **`Edit`** — pure edit ops returning `Either[String, Document[A]]`:
  `insertNode`, `cutNode` (unlinks; the node stays in the vector),
  `pasteNode` (re-attach, keeping identity), `moveNode` (atomic cut+paste
  with a cycle check), `extractSubtree`, `updateNodeData`,
  `updateNodeChildIds`, `reachableIds`, `compact`, and `validate`
  (structural integrity check).
- **`compact`** — garbage-collects unreachable nodes, rewriting ids and
  returning an old→new id map. It is explicitly documented as a **lineage
  boundary**: callers must remap `InternalNodeRef`s in node data.
- **`NodeData`** — the concrete payload type: `StringData`, `IntData`,
  `FloatData`, `InternalNodeRef(nodeId)`, `ExternalNodeRef(documentUrl,
  versionId, nodeId)` (transclusion-style cross-document references), and
  **`GapData(text)`** — a hole (§5).
- **`Outline`** — a text codec for raw documents: one line per node,
  indentation encodes nesting, with surface syntax for each `NodeData`
  variant (`"abc"`, `42`, `1.5`, `ref:5`, `ref:url@v#n`, `?text`). Doubles
  as the serialization format for stored documents.
- **`Store`** — a pure, immutable, versioned document store keyed by URL:
  `put` appends a version (0, 1, 2, …), `resolve` dereferences
  `ExternalNodeReference`'s `url@version#node` addressing, and heads are
  tracked per URL. Append-only, so pinned refs never dangle. This is the
  resolution half of external refs and the substrate for multi-document
  workspaces.
- **`Show`** — debug rendering including node versions and links.

## 4. Versioning (`foundation/version`)

Version history is itself a document: a `Document[Version[A]]` is a DAG in
which each node has 0, 1, or 2 parents (the second via merge).

- `VersionTree.update` appends a child version; `VersionTree.merge` creates a
  merge node pointing at both parents.
- Merge-base uses the **LSCA** (lowest single common ancestor) via a
  maintained **LSA** (lowest single ancestor) link per node — Fischer &
  Huson, *Information Processing Letters* 110.8-9. `getLsca` walks two
  root-paths in O(h).

Status: wired into the editor as in-session history — `EditorModel` holds
a `Document[Version[EditorSnapshot]]` plus a `versionId` cursor; every edit
records a version node, so undo branches rather than truncates
(`EditorHistory`), and a history column renders the tree with click-to-jump
(`EditorHistoryView`, `GoToVersion`). Remote sync edits record versions
too, so they undo like local edits — but the DAG is per-session and
unpersisted, so two synced tabs still see different histories; the unified
model (§8) makes history a projection of the shared Automerge change graph
instead. `merge` is still unused — concurrent edits producing two-parent
versions await that wiring. The design (per `RELATED_WORK.md`) is two
layers:

- **Operational layer** — a CRDT (Automerge) for live multi-user
  convergence; its change graph doubles as the durable draft history;
- **Curated layer** — this version DAG for deliberate, branchable,
  truncatable history (Upwelling/Patchwork-style; git's
  objects/refs/working-tree split is the structural template), realized
  today as the `Store`'s append-only `url@v` checkpoints.

## 5. Typed documents, languages, and holes

**Typed documents** (`foundation/document/Typed.scala`): a document's *type*
is its root's first child — an `ExternalNodeRef` naming the document that
defines it — and the second child is the *content* subtree (the AST). Same
convention as olw-p1/p2's header child. Untyped documents are "raw".

**Languages** (`foundation/language/Language.scala`): a `Language` gives
meaning to a type reference:

```scala
trait Language {
  def typeRef: ExternalNodeReference          // identity
  def name: String
  def views: List[String]                     // derived views, e.g. "print", "eval"
  def render(view, doc): Either[String, String]
  def parse(text): Option[DetachedNode[NodeData]]   // optional text syntax
}
```

`Languages` is a registry keyed by `typeRef`. Language implementations are
Scala code today; `typeRef` values like `opelan:languages/int-expression`
name the *future* documents that will hold the definitions — the bootstrap
path toward languages-defined-as-documents.

**Expression language** (`ExprLanguage`, `Expr`, `ExprLex`, `ExprParse`): the
demonstration language — integer literals, `+`, `−`, grouping by tree
structure. Provides `print` and `eval` views and a **total, recovering
parser**: every input parses; text that fits no production becomes a
`Hole`/`GapData` node holding the verbatim text, so ongoing edits "heal" the
tree rather than fail (Hazel-inspired typed holes). The parser emits a
**span map** (`Map[List[Int], Span]` — tree path → source range), the
foundation for mapping future text edits onto nodes instead of reparsing
whole cells. `Expr.print` round-trips holes.

**`DocText`** unifies the text surface: typed documents use their language's
`parse`/`print` for the content cell; everything else falls back to
`Outline`. `holeCount` feeds the status bar.

## 6. UI architecture

### `ui/fp` — the component runtime

A small Elm/Tyrian-style framework (custom-built post-p3):

- `Component[I, O]` — `init`, `update` (pure: state + input → `Update(state,
  outputs)`), `view` (desired `View[I]` tree), `children` (desired keyed
  child descriptions). One input channel for everything: view events, parent-pushed
  props, routed child outputs. Effects are *output values* interpreted by
  whoever mounts the component.
- `Runtime` — a single FIFO dispatch queue (no re-entrancy); the queue is the
  noted future hook for an input log — itself, per the manifesto, a document.
- `Render`/`Patch` — `View` → DOM, then positional patching that reuses nodes
  where shape matches (what keeps focus and in-progress input alive across
  re-renders). Event handlers resolve through a per-element table so
  listeners are never re-bound. `data-focus`/`data-scroll` give declarative
  post-render focus/scroll.
- `Reconcile` — keyed child instances: same key + same component reuses and
  pushes changed input; new keys mount; missing keys destroy.
- `View.Managed` — escape hatch for imperative widgets that own their DOM
  (CodeMirror); keyed, remounts on key change.
- `Handle`/`Subject` — the external face of a mounted tree: push inputs,
  subscribe to outputs.

### `ui/editor` — `DocumentEditor`

An outliner-style structural editor for raw `Document[NodeData]`, built as an
fp component and split per the one-concern-per-file convention
(`EditorModel`, `EditorUpdate` dispatch, `EditorSession`,
`EditorDocOps`, `EditorClip`, `EditorKeys`, `EditorView`, `EditorRow`,
`EditorSample`). Keyboard-driven (Enter/Shift+Enter insert, Tab indent,
F2/double-click inline edit, cut/copy/paste of subtrees, undo/redo over
document values, `Compact` for GC). The sample document is the manifesto
rendered as a tree — dogfooding content.

### `ui/typeddoc` — `TypedDoc`

The workbench's typed-document pane, composing `DocumentEditor` as a child
and adding view modes: **editor** (structural), **text** (a `Managed`
CodeMirror cell bound to `DocText`), and **derived views** offered by the
document's language (`print`, `eval`). Notable mechanics:

- The editor child stays mounted in every mode (`display:none`), preserving
  selection/undo across view switches.
- `doc` vs `pushedDoc` mirror split: editor-originated changes update the
  mirror but are never pushed back down (would reset the child's state);
  text-originated changes do push.
- `textEpoch` bumps remount the CodeMirror cell only when text was
  re-derived externally — typed input never echoes.

This pane is the working proof of *polysyntactic* editing: three surfaces
over one canonical document.

### `ui/text/CodeMirror`

Thin dynamic-`JSImport` wrapper over CodeMirror 6 (updateListener →
`onChange` per doc-changing transaction). Comment documents the 6.x-only
package constraint.

### `ui/editor/Workbench`

`Workbench` is the application shell mounted by `Main`: a sidebar listing
stored document heads and a toolbar switching between the **document**
view (hosts `TypedDoc`) and a **components** view (fp demo). Markup lives
in `WorkbenchLayout`; document-store decisions live in the pure `Workspace`
value (open/save/follow-ref) with the DOM and async plumbing in
`WorkbenchDocs`. Ctrl+Enter on a ref node follows it: internal refs select
their target in place, external refs bubble up as an output, resolve
through the `Store`, and open the pinned version at the referenced node —
the first real use of `ExternalNodeRef`. The scaffold-era views it once
hosted — a textarea DSL stub, a static schema panel, a canvas
`DiagramRenderer` — were removed along with the scaffold model types they
were built on.

## 7. Navigation

Navigation follows from two manifesto principles — *polysyntactic* (the same
gesture interpreted per surface/language) and *language* (keystrokes and
clicks are a language the software interprets). The design has two layers:

- *Mechanism* — uniform. Every jump reduces to moving a selection within a
  document or following a ref, and refs are first-class (`InternalNodeRef`,
  `ExternalNodeRef` with `url@version#node` addressing resolved through the
  `Store`). A *nav target* is a `(document address, node, view, jump|peek)`
  tuple, so landing in a particular surface — or peeking inline — is the
  same machinery, not a special case.
- *Relations* — per-language. In a typed document the cursor sits on a
  *name*, and each command asks a different semantic question: what defines
  it, what types it, what uses it, what exemplifies it. `Language`
  therefore grows a resolver capability — `resolve(relation, doc,
  position): List[NavTarget]` alongside `views`/`render`/`parse`. Raw
  documents are the degenerate case: the "resolver" is the ref literally
  stored in the node (today's Ctrl+Enter).

The relation vocabulary comes in two tiers. A *foundational* set is fixed
by the workbench — named, keybound, and guaranteed across languages
(definition, usages, type-of, examples): the language supplies the
implementation, but the platform owns the vocabulary, so applications
inherit it per the *transitive* principle. An *open* set is advertised per
language like `views` — "go to test", "go to migration" — and enumerated by
the command palette (*discoverable*). Both tiers resolve through the same
interface and return ordinary nav targets, so peek, jump history, and
results-as-documents apply uniformly to language-invented jumps.

**Spatial navigation** — syntax-dependent, per the active surface:

- *Forward/backward* (left/right) and *up/down* move through the projection:
  by token/line in text, by sibling/parent in the structural editor. Current
  state: arrow up/down do linear outline traversal; collapse-to-parent and
  expand-to-child on left/right are the natural additions.
- *Structural jumps*: go to parent/child, next/previous sibling,
  next/previous node of the same type or category (next definition, next
  gap). A raw-document editor is just another surface, so these primitives
  apply everywhere — raw editing is not a special mode.
- *Expand/shrink selection* — the selection itself walks the tree.

**Semantic navigation** — distinct commands over the resolver interface:

- *Go to definition* / *find usages*: the binding relation and its inverse.
  Definition is a resolver query (name under cursor → defining node);
  usages are an inverse ref index over stored documents — meaning-aware,
  since refs are structured, not text search.
- *Go to examples*: the *self-documenting* principle puts examples in
  documents linked by refs. Links may be stored (a definition doc
  references its examples) or discovered (inverse index filtered by
  "example-ness"). Multi-valued relations like usages/examples return a
  list of targets — presented as its own document, each entry itself a nav
  target (documents all the way down).
- *Go to type*: two readings — the *document's* type is its header ref
  (mechanism-level follow-ref on the first child), while the type of the
  *named entity* under the cursor is a semantic `type-of` query.
  *Reveal/hide types* is a projection concern (detail levels below).
- *Next/previous hole or error*: holey documents (`GapData`) make "next
  problem" a first-class jump; `holeCount` already surfaces in the status
  bar.
- *Provenance navigation*: click a value in an `eval`/rendered view to jump
  to the node that produced it — navigation upstream along the derivation.

**Cross-surface navigation** — the selection follows the user across views:

- *Go to/from rendering* re-projects the same node into a different view —
  a nav target carrying a `view`, so it lives at the mechanism layer rather
  than being a semantic query. Position follows via stable node identity
  plus the parser's span map (tree path → source range): a node selection
  in the structural editor lands on the corresponding text range, not the
  top of the cell — and vice versa.
- *Peek*: `ExternalNodeRef` is already transclusion-style; peek-definition
  renders the target inline rather than navigating away.

**Zoom navigation** — two orthogonal axes, both unique-ish to this design:

- *Detail level* (terseness/verbosity): zoom out elides — types hidden,
  signatures only, bodies folded; zoom in reveals. A per-user projection of
  the same document, not an edit. Fold/collapse is the degenerate local
  case.
- *Scope* (hoist/focus): a subtree becomes the temporary root of the view —
  the outliner's zoom. Orthogonal to detail level.

**History** — navigation state is data, and eventually a document:

- *Jump history*: back/forward across jumps (browser-back, IDE
  navigate-back). "Return from example/rendering" is a special case; a
  general stack makes every jump non-destructive.
- *Version navigation*: the version DAG is a document, so prev/next
  version, go to branch point/merge, and "next change in a diff" reuse the
  same traversal machinery once §4 is wired in.

**Addressing & search**

- *Go to symbol / quick open / command palette*: fuzzy match over names,
  document URLs, and node ids — every node is already addressable as
  `url@v#n`, so a jump target is literally a parsed ref. Serves the
  *discoverable* principle.
- *Find text*, *bookmarks*, *breadcrumbs* (clickable root→selection path):
  the mundane floor.

**Collaborative navigation** — once `collaboration/presence` exists,
"follow collaborator's cursor" and shared selections for pair navigation
fall out of presence being broadcast state.

## 8. Persistence and collaboration (`data/`, `collaboration/`)

- **`IndexedDBStore`** — Future-based access over four object stores
  (`projects`, `automerge_docs`, `settings`, `documents`) using
  `js.Dynamic` payloads, so the store stays agnostic about what it
  persists. **`DocumentRepo`** maps the `documents` store to the `Store`:
  one record per `url@version` holding `Outline.render` text; `loadAll`
  re-parses every record into a `Store` and derives heads by taking the
  max version per URL — no separate index to keep consistent.
- **Automerge** — the operational layer is real: `DocSession` holds each
  open document's outline text in an Automerge `{text}` doc (3.x strings
  merge per character), exchanges sync messages pairwise per peer over a
  `SyncTransport`, and persists the bytes to `automerge_docs` so a
  reloading tab resumes rather than restarts. Synced text reparses into
  `Document[NodeData]` on arrival — the CRDT syncs the serial form, the
  document model stays canonical. (The scaffold-era `CollaborationManager`
  stub never touched the real library; it was deleted, not integrated.)
- **`collaboration/`** — `automerge/` (the facade), `backends/`
  (`SyncTransport` + `BroadcastTransport`, tab-to-tab sync with no
  server), `DocSession` (the session logic). `signaling/` and `presence/`
  are still empty — real P2P needs a signaling transport, and cursors/
  presence are unbuilt. Incoming remote edits merge into the editor's
  version history as labeled versions (`RemoteEdit` → `SyncDocument`'s
  merge path), so a synced change undoes like a local edit instead of
  resetting history — and undoing one broadcasts the revert to peers.

**Backup and sharing are separate toggles.** Both are realized by the
same mechanism — Automerge pairwise sync treats every counterparty (a
server, a sibling tab, a collaborator) as a peer with its own sync
state — what differs is policy, not plumbing:

- **Backup** is *account-level* infrastructure: once signed in, a private
  server peer absorbs each document's draft layer. It never initiates
  changes, so it generates no `onRemoteText` traffic and no history
  noise; restoring a device is the existing `saved` resume path. Because
  Automerge sync is transitive — anything received flows to every peer —
  the server must *not* relay between clients; a per-document ACL keeps
  "private" from leaking, and enforcement lives in the server, not the
  sync layer.
- **Sharing** is a *per-document* act: inviting a peer onto a document
  (its Automerge doc id is the capability) opens sync to that peer.
  Today's toolbar toggle — which only controls tab-to-tab
  BroadcastChannel sync — becomes this per-doc switch once real
  transports exist.
- **Local-only** remains available: backup off + not shared reproduces
  today's toggle-off state, minus durability.

A user experimenting privately runs backup on, sharing off — off-device
durability without visibility to collaborators. Two implementation paths
for the server peer: speaking the sync protocol live (incremental and
multi-device-safe — the intended direction) or, as a stepping stone,
periodic `Automerge.save` blob snapshots (simple, but last-writer-wins
across devices). Encrypting the synced bytes would keep the server
plaintext-blind but demotes it to the blob model, since it could no
longer participate in merging.

**Unified history.** Two synced tabs currently keep separate session
history DAGs that diverge — confusing, since there is one shared
document. The unified model projects the *Automerge change graph itself*
as the history: every change already carries a hash, an actor id,
dependency heads, and a timestamp, and all peers converge to the same
graph by construction. History entries become `(hash, actor, time,
label)` references — document-at-version is derived via
`Automerge.view(doc, heads)` (lazy and cacheable) rather than stored
snapshots. Concurrent edits produce divergent heads, and the change that
joins them is a genuine two-parent version — `VersionTree.merge`'s first
real use.

Consequences:

- *Undo is scoped to one's own changes.* Ctrl+Z means "take back *my*
  last change," never "rewind the shared log" — the settled answer
  across collaborative editors (Google Docs transforms inverse ops
  forward, Yjs un-deletes tombstoned items, Figma scopes undo per user;
  Etherpad's global undo is the cautionary counterexample). Two cases:
  at the frontier — my latest change sits at the heads, the common
  case — the revert is exact (restore the snapshot at its parent deps).
  Past interleaved remote changes, the revert is instead *anchored at
  the old heads* via `changeAt` (the `automerge-repo-undo-redo`
  pattern), so the CRDT merge preserves remote work — accepting that
  edits overlapping the same region merge rather than invert cleanly;
  true item-level inversion isn't reachable through Automerge's public
  API. Each undo is an ordinary labeled change: visible to peers,
  itself undoable, no sync disruption — repeated undos keep walking
  back one's own changes even after peers' edits.
- *Navigation is local.* Clicking a version moves the editor's view
  only — a `DocViewed` output separate from `DocChanged`, emitting
  nothing to peers (the current coupling, where `goTo` writes a revert
  change, is the bug this fixes). The pin survives incoming remote
  edits via hash-stable version identity, so browsing doesn't get
  bumped while a peer types.
- *Editing a checked-out version forks.* A jump into history followed
  by an edit means "continue from here" — realized as a *branch*: a
  separate draft seeded at that version's text with a back-ref to its
  origin change, syncing on its own channel (the Upwelling
  private-draft pattern — branches sidestep shared-undo ambiguity
  entirely). The main doc is untouched; collaborators get a "branched
  at #n — rejoin?" notice on the existing channel and opt in rather
  than watching their document revert under them. Merge-back is a
  deliberate later gesture. The gesture carries the intent — no
  distance heuristic needed: Ctrl+Z is always incremental, checkout +
  edit is always a fork.
- *Identity.* Changes are tagged by actor id. Until accounts exist, each
  tab gets an auto-generated display name resolved through an
  `actorId → name` map synced inside the document — all peers render the
  same labels, and accounts later re-key the map without touching the
  mechanism. Each session must take a *unique* actor id on attach: tabs
  resuming the same persisted bytes would otherwise share an actor and
  collide on `(actor, seq)` — a latent bug in the current resume path.
- *Granularity.* A history entry is an Automerge change, not a
  keystroke. `DocSession` coalesces rapid consecutive local texts into a
  single change on a short debounce — a typing burst becomes one
  undoable unit; a remote message, a persist, or session close flushes
  pending edits immediately.
- *Visibility.* Peers sharing a document see its full draft history —
  checkpointing is not a visibility boundary, only the sharing toggle
  is. Ephemeral state (peer cursors, selections, eventually
  keystroke-level live preview) belongs to the future `presence/`
  channel, which broadcasts state without touching the change graph.

**Curated checkpoints.** The `Store`'s append-only `url@v` versions are
the git-like commit layer over the operational log: a save becomes a
labeled, deliberate, addressable checkpoint (`url@version#node` refs
already resolve them). The operational history is complete but unbounded
— the op log may be compacted between checkpoints — while the curated
layer holds the meaningful, branchable story.

**History of history.** Operations that mutate history itself —
restores, branch creation, prunes, compactions, curated checkpoints —
need a synced meta-log, not unilateral deletion: a locally pruned change
is simply re-offered by any peer that still holds it, so "prune below
hash X" must itself be a shared, versioned operation peers honor.
Ephemeral navigation never enters this log; it is the audit trail that
makes cleanup safe under sync.

## 9. The retired scaffold

The first commits landed a scaffold layer — `foundation/project`
(`Project`/`Definition`/`Workspace`), `foundation/structure`
(`Schema`/`SchemaRegistry`), `foundation/typing`
(`TypedGap`/`TypedGapManager`), `foundation/dsl` (`Expression`/`DSLParser`),
plus `DiagramRenderer` and a `CollaborationManager` stub — in a
`js.Dynamic`-heavy, mutable-global style that never interoperated with
`Document[NodeData]`. It has been deleted rather than integrated, since the
newer design supersedes each piece:

| Retired scaffold | Superseded / replaced by |
|---|---|
| `TypedGap`/`TypedGapManager` | `GapData`/`Hole` in `foundation/document` + `foundation/language` |
| `Expression`/`DSLParser` | `Expr`/`ExprParse`/`Language` in `foundation/language` |
| `Schema`/`SchemaRegistry` | "Schemas as documents" — future typed-document machinery |
| `Project`/`Definition`/`Workspace` | Document store + multi-document workspaces — future work |
| `DiagramRenderer`, `CollaborationManager` stubs | Document-driven visualization and real Automerge integration — future work |

When reading early history, treat anything `js.Dynamic`-heavy with mutable
global singletons and "basic implementation — can be extended" placeholders
as belonging to this removed generation.

## 10. Build and test

- Scala 3.3.1 + Scala.js via Scala CLI (`project.scala`), scalajs-dom; ES
  module output → `app.js` → webpack → `bundle.js` → loaded by `index.html`.
- `build.bat` / `build.sh` for the full pipeline (`--force` packaging handled
  by deleting `app.js` first); `test.bat` / `test.sh` run the suite.
- Tests: ~95 munit tests across `test/foundation/**` (document ops, outline
  codec, typed docs, expression parser/eval/printer, version LSCA) and
  `test/ui/**` (editor updates, fp components, typed-doc transitions) — the
  pure core is the tested core.
- npm dependencies: CodeMirror 6 packages and `@automerge/automerge`
  (declared, not yet consumed by Scala code).

For what currently works, what's in progress, open questions, and next
steps, see `STATUS.md`.
