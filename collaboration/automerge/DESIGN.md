# `collaboration/automerge/` — design

The facade over `@automerge/automerge` (see
[`../DESIGN.md`](../DESIGN.md) for the sync/history design it serves).

- **`Automerge.scala`** — the JS-interop facade: creating and loading
  docs, applying local changes, pairwise sync-message exchange, and
  save/load of the persisted bytes.
- **`ChangeInfo.scala`** — a change's metadata projected out of the
  change graph (hash, actor id, dependency heads, timestamp, label): the
  `(hash, actor, time, label)` entries unified history is made of.
- **`Revert.scala`** — the undo computation: an *inverse splice at the
  current frontier*, with spans taken from the change's before/after
  texts (longest common prefix/suffix) and positions carried forward
  through Automerge cursors (see *Undo is scoped to one's own changes*
  in [`../DESIGN.md`](../DESIGN.md)).
