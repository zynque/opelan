# Status

Point-in-time state of the Opelan workbench. For architecture and rationale
see `DESIGN.md`; for principles see `MANIFESTO.md`.

Last updated: 2026-09-29

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
  subtree cut/copy/paste, undo/redo over document values, compact.
- **TypedDoc** (`ui/typeddoc`): the workbench's document pane — structural
  editor, CodeMirror text cell, and language-provided derived views all live
  over one document; editor state survives view switches; text and structure
  stay in sync without echo loops.
- **Build/test pipeline**: `build.bat` produces `bundle.js` (scala-cli →
  webpack); `test.bat` runs ~95 munit tests covering the pure core and the
  update functions of the UI layer.

## In progress / scaffolded

- **Workbench shell** (`ui/editor/Workbench`): sidebar + view switcher work
  and host the live `TypedDoc` pane in the "document" view; the DSL editor,
  schema editor, and visualization views are placeholder UIs from the
  earlier scaffold generation.
- **IndexedDB persistence** (`data/storage`): generic store works;
  currently persists project stubs only — documents are not yet saved.
- **CollaborationManager** (`data/automerge`): a `js.Dynamic` stub with a
  naive key-merge. `@automerge/automerge` is a declared npm dependency (wasm
  blob at repo root) but no Scala code calls it yet.
- **`collaboration/`**: `signaling/`, `backends/`, `presence/` exist as
  empty directories for the planned P2P sync layer.
- **Scaffold layer** (`foundation/dsl`, `structure`, `typing`, `project`):
  earlier-generation model types (`Definition`, `Schema`, `TypedGap`,
  `Expression`) that don't interoperate with `Document[NodeData]`.
  `GapData`/`Hole` already supersedes `TypedGap`; expect gradual displacement
  rather than integration.

## Not yet wired

- The **version DAG** is implemented but unused — no branching history UI,
  and editor undo is a linear stack rather than tree navigation.
- **`ExternalNodeRef`** defines an addressing scheme (`url@version#node`)
  but there is no document store/resolver to dereference it.
- **Text edits reparse whole cells**; the parser's span map exists to enable
  finer-grained text-edit → node mapping later.
- **Schemas** (`foundation/structure`) are disconnected from the
  typed-document machinery — convergence presumably means "schemas as
  documents".

## Open design questions

1. *Two-layer history.* How the CRDT op layer and the curated version DAG
   interleave — what triggers a curated commit vs. a live-sync op
   (Upwelling's draft-layer model is the reference).
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

## Next steps

Reading `README.md`'s next steps against the current code, the natural
sequence is:

1. Document store + external-ref resolution (makes `ExternalNodeRef` real,
   enables multi-document workspaces).
2. Wire the version DAG into the editor (branching undo, version-tree view).
3. Real Automerge integration for live sync, then the curated layer on top.
4. More languages defined as documents — begin the bootstrap; schemas and
   gap/typing machinery folded into the document model.
5. Retire or absorb the scaffold layer (`dsl`, `structure`, `typing`,
   `project`) as equivalent document-based concepts land.
