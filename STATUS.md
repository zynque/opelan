# Status

Point-in-time state of the Opelan workbench. For architecture and rationale
see `DESIGN.md`; for principles see `MANIFESTO.md`.

Last updated: 2026-10-14

The project is in Phase 1 (foundation & schema system) of the
implementation plan. **Next major series:** replacing combinator-based
language definitions with `pattern MEANS meaning` rules compiled onto the
same combinators — see
"Next steps", item 6.

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
- **Typed documents** (`foundation/document/Typed`): the type ref is the
  root node's data; a standard-layout document's children are imports,
  title, then content — olw-p2's dependencies, header, content layout
  with the header folded onto the root. `title`/`imports` are
  themselves declared constructs in `lang: language`'s schema; imports
  carries a direct external ref (self-import would be circular), title
  an imported one. A typed document without an
  imports-shaped second child is simple data: it has no dependencies
  and no standard content slot.
- **Everything by reference** (`Imports`, `Names`, DESIGN §4): language
  constructs are never stored as strings. Each one a document uses is
  imported once (an `ExternalNodeRef` to its schema entry) and applied
  through `InternalNodeRef`s to that entry. Readers (expression, sheet,
  def-doc decoder) match constructs by identity, and the structural
  editor shows the resolved name beside the raw ref. Renaming a construct
  in its `lang:` doc renames every use, and decoding is unaffected
  (tested). Def docs import their meta constructs from `lang: language`,
  which imports from itself.
- **Id-preserving persistence** (`Serial`): stored and synced text carries
  node ids (`#17 "mul"`), so internal refs and `url@v#n` refs into a
  document survive save, reload, and sync. Before this, a reload
  renumbered nodes and broke them. Id-less text from older stores still
  loads.
- **Expression language** (`examples/`): the demonstration
  language — int/float literals (`Num` keeps integers integral, division
  floats), `+`/`−`/`*`/`/` with precedence-aware printing, `ref:N` cell
  references (stored as `InternalNodeRef`), structural grouping — with a
  total, recovering parser (holes hold verbatim unparsed text),
  `print`/`eval` derived views rendered as provenance-tagged view trees
  (`ExprView`'s `LocExpr` keeps each node's source id), resolver-based
  evaluation with cycle detection, and a span map (tree path → source
  range).
- **Sheet language** (`examples/Sheet*`): the demo app — a
  typed document whose content is `sheet > row/hrow > cell` rows of
  labels, literals, and formula cells (expressions over refs to other
  cells). `sheet` and `formulas` views render evaluated/source grids;
  cells compose through the document resolver so a hole or cycle in a
  referenced cell degrades the dependents, all with provenance. Cells
  are projected editors — labels, literals, and formulas edit in place,
  committing through the cell grammar back into the document. A seeded
  "Household budget" sample loads from the toolbar.
- **View trees** (`foundation/view` + `ui/view`): a derived view is
  detached document data — `block`/`flow`/`heading`/`grid`/`row`/`cell`
  containers, scalar leaves as text runs, `GapData` as holes, `input`
  editable text regions, a `SourceRef` first child tagging an element's
  provenance (for `input`, naming the node it edits). One recovering
  interpreter (`viewTree`) renders any view tree to an fp `View`; unknown
  structure degrades to hole chips. `Language.render` produces these;
  the sidebar's provenance clicks select the producing node
  (`SelectSource` rides the ordinary `SyncDocument` push — a same-doc
  push carrying a selection records no version — as a *silent select*:
  `SelectTarget` carries a focus flag so the outline highlights without
  claiming DOM focus and the cell's edit box keeps it), and `input` commits
  rewrite the source node via `Language.parseCell` +
  `replaceNodeContent` — an id-preserving edit, so refs keep resolving.
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
  stay in sync without echo loops; clicking a provenance-tagged fragment of
  a derived view selects the producing node in the editor. A derived view
  can also fill the pane alone (`DocView.Derived`) — the app surface —
  with **Raw** (this document's representation) and **Definition (F12)**
  (the type ref's target — the app's implementation) as its exits.
- **Document store** (`foundation/document/Store`): a pure, immutable,
  versioned map of documents by URL — `put` appends a version, `resolve`
  dereferences `url@version#node`, heads are tracked per URL. Persisted in
  IndexedDB (`documents` object store, one outline-text record per
  `url@version`) via `data/storage/DocumentRepo`.
- **Multi-document workspace** (`ui/editor/Workbench` + `Workspace`): the
  sidebar lists stored documents; New/Save/open flows go through the pure
  `Workspace` value; Ctrl+Enter on a ref node follows it — internal refs
  select their target, external refs resolve through the store and open
  the pinned version at the referenced node. First hydration seeds the
  demo lineup (`ui/workbench/DemoDocs`): each registered language's
  `langDoc` — the real definition encoding (below) — lands at the URL
  its `typeRef` names (v0, the version refs pin), plus the
  language-definition language at `opelan:languages/language` and the
  Household budget at `opelan:docs/household-budget`; a URL the store
  already holds is never overwritten. Following the budget's type ref
  lands on `lang: sheet` — itself typed by `lang: language`, rendered by
  its `def` view — whose cell grammar links on to `lang: expression` by
  external ref.
- **Language definitions** (`foundation/langdef`): a `LanguageDef` ADT
  (schema, combinator grammar, view/cell bindings) is encoded as a
  document by `langDefDoc` and recovering-read by `langDefFromDocument`;
  `interpret` turns grammar data into a total parser and `DefLanguage`
  turns a def into a live `Language`. `languageForRef(ref, store)`
  resolves a type ref through the document store first — the stored def
  doc *is* the implementation — falling back to the Scala registry
  (`ExprLanguage`, `SheetLanguage`) and to `DocLanguage`, the
  code-resident language for def docs themselves. The pane's `store`
  prop carries the resolution context, so saving an edited `lang:` doc
  re-interprets dependents on the next render. Malformed defs degrade
  to holes, never exceptions — missing refs, cyclic grammars, and
  cyclic documents are all guarded.
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
  webpack); `test.bat` runs ~190 munit tests covering the pure core and the
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
  generated names per change. The §7 gesture split is implemented:
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

- **Stored documents from before the by-reference format** (old IndexedDB
  stores) aren't migrated: their typed docs have no imports section and
  spell constructs as strings, so they degrade to holes. Seeding never
  overwrites, so use **Reset** to reseed. The seeds' construct ids must
  also match code's canonical encodings (`metaKindIds`, `kindRefs`), so
  a change to a seed's layout needs a reset or a migration.
- **Cross-document paste** doesn't externalize/re-import refs yet
  (`extractExternalized` + `internalizeRefs` are the primitives), and
  import entries pin the version current at import time. Names display
  from the head, but nothing yet offers to bump pins.

## Open design questions

1. *Two-layer history.* Largely settled in design (DESIGN.md §7): the
   Automerge change graph is the unified operational history — projected
   into the version-DAG view, actor-tagged — with the gesture split
   implemented: navigation is local (`DocViewed`), undo scopes to one's
   own changes (inverse-splice reverts), and checkout+edit forks a
   branch draft peers opt into. `Store` saves are the curated, git-like
   checkpoint layer on top. Open: the op-log compaction boundary, the
   synced meta-log for history mutation, branch merge-back semantics,
   and the presence channel's shape.
2. *Bootstrap.* Language definitions are documents resolved through
   `typeRef`, but their semantics (lexers, views, eval) still name Scala
   builtins, and the definition format is combinator data rather than
   something a domain expert would write. The MEANS-rules series (next
   steps, item 6) is the planned answer to both.
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
5. ~~Languages defined as documents~~ — done: `LanguageDef` + `lang:`
   documents + store-first resolution (`foundation/langdef`). Remaining:
   schemas are descriptive only; gap/typing machinery is not yet folded
   into the document model.
6. **MEANS rules — the next major series.** Replace authoring of the
   combinator-based language definition model (`GrammarDef`, a
   hand-written schema list, hand-written printers, and builtin views)
   with `pattern MEANS meaning` rules plus a tightest-first binding list,
   compiled onto the existing recovering combinators, as designed in
   DESIGN.md §4
   (*Defining languages by MEANS rules*). The steps below keep the
   current model working alongside until parity is proven; each step
   lands with tests.

   *Phase A — the rule engine (pure, `foundation/`):*

   0. ~~**Everything by reference.**~~ Done: constructs are imported and
      used by ref, and persistence preserves ids (see "Working"). Rules
      inherit this: a meaning's constructor and placeholders are refs,
      not names.
   1. **Rule model.** A `Rule(pattern, meaning)` ADT: patterns as
      literal/placeholder pieces; meanings as a constructor
      (`add(a, b)`), a bare placeholder (`(a) MEANS a`), or a rewrite into
      the same language (`a % MEANS a / 100`); a binding list of tiers;
      worked examples. Rule shape — atom, prefix, infix, postfix,
      outfix (`|a|`), mixfix — is derived from the pattern.
   2. **Tokens from rules.** Derive the token set from the rules' literal
      pieces (longest match), plus a small set of builtin lexical classes
      for leaves (number, whole number, name, text). Retires the builtin
      `expr` lexer.
   3. **Operator combinator + rule compiler.** Combinators stay the
      parsing substrate (DESIGN.md §4, *Engine*). Generalize
      `precedenceTable` (left-associative infix only) into a
      precedence-climbing operator combinator covering prefix, postfix,
      and right-/non-associative infix, driven by the binding list. Then
      compile rules to combinator compositions: operator-shaped rules join
      the operator combinator; bracketed, keyword-led, and sequence rules
      compile to sequence/choice and serve as its atoms. Recovery stays in
      the combinators — a missing operand is an empty hole, unplaceable
      text a verbatim hole — so `Junk`/`Phrase`/stop sets never appear in
      an authored definition. `Signum`'s fused negative literals become a
      parse-time meaning (`-n MEANS literal(-n) WHERE n: number`).
   4. **Derived printer.** Invert constructor meanings and use the binding
      list for minimal parentheses. Property test:
      `parse(print(t)) == t` over generated trees and the parser battery.
   5. **Derived schema.** `NodeKind`s (name and arity) come from the
      rules' meanings; the hand-written schema list goes away.

   *Phase B — the expression language on rules:*

   6. **`exprRules` at parity.** Write the expression language as rules;
      `DefLanguage` accepts rule-based syntax next to `GrammarDef`.
      Done when the rule form is identical to `ExprLanguage` — trees,
      spans, printing, and eval — on the existing 28-input battery and
      the full parser test suite (the same regression anchor M2 used).

   *Phase C — rules as documents:*

   7. **Rule encoding.** `lang:` documents hold `means(pattern, meaning)`
      nodes, a binding list, and examples; the recovering decoder turns a
      malformed rule into a hole rule that never applies.
   8. **MEANS text surface.** The `def` view and text cell for `lang:`
      documents read and write the MEANS notation (hand-written parser at
      first), so editing a language reads like the DESIGN.md examples
      rather than an outline of combinators.
   9. **Worked examples as live checks.** `examples:` entries are checked
      on every render; a failing example shows as a hole in the `def`
      view (*Learnable and self documenting*, *Reactive*).

   *Phase D — meaning layers; demote the old model:*

   10. **Semantics as rules.** `add(a, b) MEANS builtin num-add`: eval
       is derived from meaning rules bottoming out in builtins, replacing
       the hand-coded `Expr.eval` behind the `expr-eval` view. The rule
       dependency DAG and derived layers are computed; a cycle renders as
       a hole at the closing edge.
   11. **Migrate stored definitions.** Users' IndexedDB stores hold
       `lang:` docs in the combinator format, and seeding never
       overwrites. Convert legacy grammar subtrees to rules on read (or
       on first load) — the project's first real *Evolvable* migration,
       revertible because every version is kept.
   12. **Demote `GrammarDef` to the implementation layer.** Definitions
       are authored and stored as rules; `GrammarDef` stays as the
       compilation target `interpret` runs, shown only under show
       implementation — and still writable there as the escape hatch for
       what rules can't express. Remove the combinators rules made
       redundant in authored definitions (`Junk`, `Phrase`, `Signum`,
       stop sets). Update DESIGN.md and the demo.

   *Phase E — beyond parity:*

   13. **Sheet on rules.** Positional refs (`ref:c,r`, then `C4`) as
       parse-time meanings resolving to stable `InternalNodeRef`s and
       printing back as current positions; ranges (`a .. b`).
   14. **WHERE and adjustable verbosity.** WHERE clauses with constraints
       inferred from the layer below; a terse/verbose detail toggle in the
       `def` view, kept separate from show/hide implementation; violated
       constraints surface as holes at every zoom level.
   15. **Cycle prevention in the editor.** Completion on a meaning offers
       only rules below it in the DAG.
   16. **Self-description.** The language-definition language's own
       syntax is written as MEANS rules, closing the bootstrap loop.

   Open questions to settle along the way (DESIGN.md §4): the plain-text
   placeholder convention, how parse-time vs evaluation-time meanings are
   marked, and whether alternative notations live in one language
   document or in layered notation documents.

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
- Broader test coverage beyond the current ~255 munit tests.
