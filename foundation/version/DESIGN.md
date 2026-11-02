# `foundation/version/` — design

Version history is itself a document: a `Document[Version[A]]` is a DAG
in which each node has 0, 1, or 2 parents (the second via merge).

- `appendVersion` adds a child version; `mergeVersions` adds a merge
  node pointing at both parents.
- The merge base is the **LSCA** (lowest single common ancestor), found
  via a maintained **LSA** (lowest single ancestor) link per node —
  Fischer & Huson, *Information Processing Letters* 110.8-9. `getLsca`
  walks two root paths in O(h).

**Where it is used.** The editor's history is this DAG. For an unsynced
document, `EditorModel` holds a session-local
`Document[Version[EditorSnapshot]]` plus a `versionId` cursor: every
edit records a version, undo branches rather than truncates
(`EditorHistory`), and a history column renders the tree with
click-to-jump (`EditorHistoryView`, `GoToVersion`) — all in
[`ui/editor`](../../ui/editor/DESIGN.md). For a synced document the DAG
is rebuilt from the shared Automerge change graph (`EditorSynced`), so
every peer sees the same author-tagged tree, and two-dependency changes
become real merge versions via `mergeVersions` (see
[`collaboration/`](../../collaboration/DESIGN.md)). Unsynced session
history is not yet persisted.

History is designed as two layers (per `RELATED_WORK.md`):

- **Operational layer** — a CRDT (Automerge) for live multi-user
  convergence; its change graph doubles as the durable draft history.
- **Curated layer** — deliberate, branchable, truncatable checkpoints
  (Upwelling/Patchwork-style, with git's objects/refs/working-tree split
  as the structural template), realized today as the `Store`'s
  append-only `url@v` versions (see
  [`../document/store/DESIGN.md`](../document/store/DESIGN.md)).

**Diffing.** Since the whole system is based on syntax trees and deeply
aware of semantics, diffs will be able to be shown as semantic changes
rather than just text changes.
