# `data/storage/` — design

Persistence to the browser's IndexedDB (part of the storage-and-sync
design in [`../../collaboration/DESIGN.md`](../../collaboration/DESIGN.md)).

- **`IndexedDBStore`** — Future-based access to four object stores
  (`projects`, `automerge_docs`, `settings`, `documents`) using
  `js.Dynamic` payloads, so the store stays agnostic about what it
  persists.
- **`DocumentRepo`** — maps the `documents` store to the
  [`Store`](../../foundation/document/store/DESIGN.md): one record per
  `url@version` holding `Serial` text (id-preserving — see
  [`../../foundation/document/DESIGN.md`](../../foundation/document/DESIGN.md));
  `loadAll` re-parses every record into a `Store` and derives heads as
  the max version per URL — no separate index to keep consistent.
- **`AutomergeRepo`** — keeps each synced document's Automerge bytes in
  `automerge_docs`, so a reloading tab resumes rather than restarts.
