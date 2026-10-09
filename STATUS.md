# Status

Point-in-time state of the Opelan workbench. For architecture and rationale
see `DESIGN.md`; for principles see `MANIFESTO.md`.

Last updated: 2026-10-06

The project is in Phase 1 (foundation & schema system) of the
implementation plan.

## Working

- **Document model** (`foundation/document`): persistent array-backed tree;
  pure structural edits — insert, cut, paste, atomic move with cycle check,
  subtree extraction, per-node versions; `compact` (unreachable-node GC with
  id remap, the defined lineage boundary) and `validate` (integrity check).
- **Outline codec** (`foundation/document/Outline`): indentation-based
  text↔tree round-trip for raw documents, with surface syntax for every
  `NodeData` variant including refs and gaps.
- **Version DAG** (`foundation/version`): version tree as a document;
  update/merge with LSCA merge-base via LSA links (Fischer & Huson). Ported
  from olw-p4 and fully tested — but see "not yet wired" below.
- **Typed documents** (`foundation/document/Typed`): type-ref first child +
  content subtree convention, mirroring the olw-p1/p2 header convention.
- **Expression language** (`foundation/language`): the demonstration
  language — integer literals, `+`/`−`, structural grouping — with a total,
  recovering parser (holes hold verbatim unparsed text), a `print`/`eval`
  derived views, and a span map (tree path → source range).
- **fp component runtime** (`ui/fp`): Elm-style `Component[I,O]` with pure
  update, single FIFO dispatch queue, positional DOM patching that preserves
  focus, keyed child reconciliation, `Managed` escape hatch for imperative
  widgets (CodeMirror), declarative focus/scroll.
- **DocumentEditor** (`ui/editor`): keyboard-driven outliner over raw
  documents — navigate, insert sibling/child, indent/outdent, inline edit,
  subtree cut/copy/paste, compact. History is the version DAG: every edit
  records a version node, undo branches rather than truncates, and a
  history column (`EditorHistoryView`) renders the tree — click any
  version to jump to it.
- **TypedDoc** (`ui/typeddoc`): the workbench's document pane — structural
  editor, CodeMirror text cell, and language-provided derived views all live
  over one document; editor state survives view switches; text and structure
  stay in sync without echo loops.
- **Document store** (`foundation/document/Store`): a pure, immutable,
  versioned map of documents by URL — `put` appends a version, `resolve`
  dereferences `url@version#node`, heads are tracked per URL. Persisted in
  IndexedDB (`documents` object store, one outline-text record per
  `url@version`) via `data/storage/DocumentRepo`.
- **Multi-document workspace** (`ui/editor/Workbench` + `Workspace`): the
  sidebar lists stored documents; New/Save/open flows go through the pure
  `Workspace` value; Ctrl+Enter on a ref node follows it — internal refs
  select their target, external refs resolve through the store and open
  the pinned version at the referenced node.
- **IndexedDB persistence** (`data/storage`): generic `js.Dynamic` store
  over four object stores (`projects`, `automerge_docs`, `settings`,
  `documents`); persists document versions as outline text (the projects
  list UI was replaced by the documents list).
- **Live sync** (`collaboration/`): a real Automerge layer — `DocSession`
  holds each open document's outline text in a `{text}` CRDT doc (in 3.x
  strings merge at the character level), syncs it over a pluggable
  `SyncTransport`, and persists the Automerge bytes to IndexedDB. The
  first transport is `BroadcastTransport`: tabs of the same browser share
  edits live with no server. The workbench **Sync** button toggles it.
  Each tab gets a unique actor id plus an auto-generated name
  (sessionStorage), and every change carries the writer's name as its
  message — the change graph is the shared history: identical on every
  peer once converged, projected into the editor's version DAG with
  author labels and real merge versions for concurrent edits. Undo emits
  a revert change that propagates to peers. Echoes are suppressed by
  last-text comparison, not protocol smarts.
- **Build/test pipeline**: `build.bat` produces `bundle.js` (scala-cli →
  webpack); `test.bat` runs ~130 munit tests covering the pure core and the
  update functions of the UI layer, plus DocSession convergence tests that
  run real Automerge under Node.

## In progress / scaffolded

- **Workbench shell** (`ui/editor/Workbench`): documents sidebar backed by
  the document store + view switcher between the live `TypedDoc` pane
  ("document") and the fp components demo. The earlier DSL editor, schema
  editor, and visualization views were retired with the scaffold layer.
- **`collaboration/`**: live sync works tab-to-tab (above). `signaling/`
  and `presence/` remain empty — real P2P across machines needs a
  signaling transport and presence is unbuilt.

## Not yet wired

- The **version DAG** is wired for in-session editor history (branching
  undo, version-tree column); for synced documents it is now a projection
  of the shared Automerge change graph (`EditorSynced`) — author-tagged,
  two-dep changes render as merge versions via `mergeVersions`.
  Unsynced documents keep the local session DAG, history is not persisted
  across sessions, and the document store's append-only versions remain
  the simpler curated-checkpoint layer.
- **`ExternalNodeRef`s to missing targets** report "unresolved" rather than
  offering creation; and refs into old pinned versions open read-write —
  saving an old version appends a new head rather than branching.
- **Text edits reparse whole cells**; the parser's span map exists to enable
  finer-grained text-edit → node mapping later.
- **Synced history is unified, author-tagged, and navigation is
  mutation-free**: the pane's version DAG is rebuilt from the Automerge
  change graph on every growth, so both tabs see the same tree with
  generated names per change. The §8 gesture split is implemented:
  checkout/`goTo` emits `DocViewed` (local view only — no change
  emitted, and a browsing pin survives remote pushes by change hash);
  Ctrl+Z at the synced head defers to `DocSession.undo`, which reverts
  this actor's own last change via a cursor-translated inverse splice
  (peers' interleaved edits preserved; a revert is declined rather than
  clobbering a span a collaborator edited inside); and editing a
  checked-out version emits `BranchRequested` — the workbench forks a
  branch draft on its own channel and peers get a confirm-prompt to
  rejoin. A sync push that echoes our own edit (same outline text)
  keeps the live document — a reparse reassigns node ids, which would
  otherwise retarget selection/edit state onto wrong nodes. Remaining:
  remote application is still a whole-text reparse (finer-grained
  application belongs with the span-map work); text-cell
  edits while browsing still write to the main session (branch
  interception covers structural-editor edits); undo stacks are
  session-local and lost on resume; branch merge-back is manual (a
  restore-text change); and edits made *before first sync contact* can
  lose — concurrent writes to `text` without a shared ancestor resolve
  last-writer-wins rather than char-merging (real CRDT semantics).
- **Schemas** have no representation yet — the scaffold's
  `foundation/structure` model was retired; convergence presumably means
  "schemas as documents".

## Open design questions

1. *Two-layer history.* Largely settled in design (DESIGN.md §8): the
   Automerge change graph is the unified operational history — projected
   into the version-DAG view, actor-tagged — with the gesture split
   implemented: navigation is local (`DocViewed`), undo scopes to one's
   own changes (inverse-splice reverts), and checkout+edit forks a
   branch draft peers opt into. `Store` saves are the curated, git-like
   checkpoint layer on top. Open: the op-log compaction boundary, the
   synced meta-log for history mutation, branch merge-back semantics,
   and the presence channel's shape.
2. *Bootstrap.* Languages are Scala objects today; the end state is language
   definitions as documents, resolved through `typeRef` — which requires the
   document store and enough language machinery to be self-describing.
3. *Immediately typed.* Real typechecking per keystroke over holey trees
   (Hazelnut-style edit semantics), beyond the current parse-level holes.
4. *Semantic diff/merge.* Node-id-stable documents enable meaning-aware
   diffs; compaction is the defined lineage boundary.
5. *Polysyntactic parity.* The text surface is currently per-language and
   read/write through a full reparse; richer projections (tables, diagrams as
   editors, per-user notation) remain open.
6. *Migration classes.* Per the *Evolvable* principle, a change to a language
   definition should derive migration tooling for its artifacts, applied
   automatically by default. But "automatic" should depend on the migration's
   class: provably mechanical changes (renames, reorders, added fields with
   defaults) are categorically safer than semantic ones, and the generator is
   in a position to know which it produced. How migrations are classified —
   and whether semantic ones auto-apply with notification or hold for
   opt-in review — is open.
7. *Language composition & interop.* The fragmentation answer
   (`ADOPTION.md`) depends on mechanisms that don't exist: extending a
   language document rather than authoring a new one (language
   inheritance/mixins as document composition), a canonical base vocabulary
   of language fragments most DSLs share, and a shared value/evaluation
   model so an artifact in one DSL is semantically meaningful to another —
   cross-document *linking* exists via refs; cross-language *composition*
   does not.

## Next steps

The natural sequence from the current code is:

1. ~~Document store + external-ref resolution~~ — done: `Store` +
   `DocumentRepo`, documents sidebar, follow-ref navigation.
2. ~~Wire the version DAG into the editor~~ — done: branching undo,
   `GoToVersion`, history column in `DocumentEditor`. Remaining: merges,
   persisted history.
3. ~~Real Automerge integration for live sync~~ — done for the local case:
   `DocSession` + `BroadcastTransport` give live convergence between tabs.
   Remaining: a real network transport (signaling), presence, and the
   curated layer on top.
4. ~~Unified history~~ — done for the tab-sync case: the Automerge change
   graph is projected into the version-DAG view with per-tab actor ids
   and generated names, merge versions on concurrent edits, and
   revert-style undo. The navigation/undo/branch split is done
   (`DocViewed`, per-actor undo via cursor-translated inverse splices,
   checkout-edit forks + rejoin prompt). Remaining: debounce-coalesced
   edit bursts for granularity, session-resumed undo stacks, text-cell
   edits made while browsing, branch merge-back, durable history beyond
   the tab's lifetime (the op log already persists via `automerge_docs`),
   curated checkpoints, and the synced meta-log for history pruning/
   compaction.
5. More languages defined as documents — begin the bootstrap; schemas and
   gap/typing machinery folded into the document model. (Type refs already
   resolve through the store — a language definition stored as a document
   is the first bootstrap candidate.)

The scaffold layer (`dsl`, `structure`, `typing`, `project`, plus the
`DiagramRenderer` and `CollaborationManager` stubs) has been retired —
document-based concepts superseded or will replace each piece.

Beyond that sequence, the roadmap also calls for:

- A visual schema editor and interactive diagram editing as document
  views.
- In-workbench help: `USAGE.md` documents shortcuts and views as an
  interim measure; per the *self documenting* principle this belongs in
  the workbench itself (a help document rendered as a view — documents
  all the way down).
- P2P presence indicators (cursors, selections, eventually
  keystroke-level live preview) — the sync envelope and per-peer state
  are in place; presence is ephemeral broadcast state on top, separate
  from the change graph.
- A fuller evaluation engine for the DSL beyond the expression language's
  minimal `eval` view.
- Broader test coverage beyond the current ~120 munit tests.
