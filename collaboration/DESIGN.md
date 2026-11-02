# `collaboration/` — design

The operational layer of the two-layer history design (see
[`../foundation/version/DESIGN.md`](../foundation/version/DESIGN.md)):
live multi-user convergence on a CRDT, with the change graph doubling
as the durable draft history. Persistence lives in
[`../data/storage/`](../data/storage/DESIGN.md); the curated
`url@v` checkpoint layer lives in
[`../foundation/document/store/`](../foundation/document/store/DESIGN.md).

Contents: [`automerge/`](automerge/DESIGN.md) (the facade, plus change
info and revert computation), [`backends/`](backends/DESIGN.md)
(`SyncTransport` and `BroadcastTransport`: tab-to-tab sync with no
server), `DocSession` (the session logic), `SessionUndo` (per-actor undo
stacks), and `Identity` (session actor ids and names). Signaling (for
real P2P across machines) and presence (cursors, selections) are not
built yet.

## Automerge — the operational layer

`DocSession` holds each open document's outline text in an Automerge
`{text}` doc (3.x strings merge per character), exchanges sync messages
pairwise with each peer over a `SyncTransport`, and persists the bytes so
a reloading tab resumes rather than restarts. Synced text (the `Serial`
form, so node ids agree on every peer) is parsed into
`Document[NodeData]` on arrival: the CRDT syncs the serial form, and the
document model stays canonical. A push that echoes the session's own
last text is suppressed, so the live document (selection, edit state) is
kept rather than reparsed. Syncing the serial text is scaffolding, not
the intent: merges happen at the line level, so concurrent structural
edits degrade to line merges — duplicate node ids must be renumbered
(retargeting or dangling refs that named them), and unrelated structural
changes can collide textually. The destination is a **tree-native
CRDT**: the document's nodes and child lists replicated as structure, so
concurrent edits merge structurally, node identity survives by
construction, and no reparse is needed on receipt.

## Backup and sharing are separate toggles

Both use the same mechanism — Automerge pairwise sync treats every
counterparty (a server, a sibling tab, a collaborator) as a peer with
its own sync state — and differ only in policy:

- **Backup** is *account-level* infrastructure: once signed in, a
  private server peer absorbs each document's draft layer. It never
  initiates changes, so it adds no history entries; restoring a device
  is the existing resume-from-saved-bytes path. Because Automerge sync
  is transitive — anything received flows to every peer — the server
  must *not* relay between clients; a per-document ACL keeps "private"
  from leaking, enforced by the server, not the sync layer.
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
devices). Encrypting the synced bytes would keep the server unable to
read them, but demotes it to the snapshot model, since it could no
longer take part in merging.

## Unified history (implemented for tab sync)

A synced document has one history, not one per tab: the *Automerge
change graph itself* is projected as the history. Every change carries a
hash, an actor id, dependency heads, and a timestamp, and all peers
converge on the same graph by construction. History entries are `(hash,
actor, time, label)` references; document-at-version is derived from the
graph rather than stored as snapshots. Concurrent edits produce
divergent heads, and the change that joins them is a genuine two-parent
version (`mergeVersions` —
[`../foundation/version/DESIGN.md`](../foundation/version/DESIGN.md)).

Consequences:

- *Undo is scoped to one's own changes.* Ctrl+Z means "take back *my*
  last change," never "rewind the shared log" — the settled answer
  across collaborative editors (Google Docs transforms inverse ops
  forward, Yjs un-deletes tombstoned items, Figma scopes undo per user;
  Etherpad's global undo is the cautionary counterexample). The session
  keeps a stack of the changes it authored (`SessionUndo`); undo appends
  a revert change computed as an *inverse splice at the current
  frontier* (`automerge/Revert`). The change's before/after texts give
  its span (longest common prefix/suffix), positions are carried forward
  through Automerge cursors, and the revert is declined if the span no
  longer holds exactly what the change inserted — deleting a peer's
  text inside it would be worse than refusing. This handles the frontier
  and interleaved cases uniformly. (An anchored revert via `changeAt`
  was considered and dropped: setting the pre-change text at the old
  heads is a no-op.) Each undo is an ordinary labeled change — visible
  to peers, itself undoable, no sync disruption; repeated undos keep
  walking back one's own changes, and an undo feeds the redo stack
  rather than the undo stack. Stacks are session-local and empty on
  resume; rebuilding them from actor-tagged history is future work.
- *Navigation is local.* Clicking a version moves the editor's view only
  — a `DocViewed` output, distinct from `DocChanged`, that sends
  nothing to peers. The pin survives incoming remote edits via
  hash-stable version identity, so browsing isn't disturbed while a
  peer types.
- *Editing a checked-out version forks.* A jump into history followed by
  an edit means "continue from here," realized as a *branch*: a separate
  draft seeded at that version's text with a back-ref to its origin,
  syncing on its own channel (Upwelling's private-draft pattern —
  branches sidestep shared-undo ambiguity entirely). The main document
  is untouched; collaborators get a "branched at #n — rejoin?" prompt
  and opt in rather than watching their document revert under them.
  Merge-back is a deliberate later gesture (manual today). The gesture
  carries the intent, so no distance heuristic is needed: Ctrl+Z is
  always incremental; checkout + edit is always a fork.
- *Identity.* Every session takes a *unique* actor id on attach — tabs
  resuming the same persisted bytes would otherwise collide on
  `(actor, seq)` — plus a generated display name, both kept per tab in
  `sessionStorage`. Each change carries its writer's name as its
  message, so every peer renders the same labels. Once accounts exist,
  an `actorId → name` map synced inside the document can re-key names
  without changing the mechanism.
- *Attribution follows initiation — the workbench is a collaborator.*
  Three classes of change touch a document, and only one is the user's:
  synchronous in-cursor edits (typing, shortcuts, mechanical
  refactorings) are the user's actor and land on their undo stack;
  background edits — the auto-update cascade accepting pins, an LLM
  pass — are authored by a *non-user actor* and arrive like any remote
  change, through the same merge path rather than a privileged channel
  that mutates the document underneath the user; ephemeral state
  (cursors, selections, presence) never becomes a change at all. The
  actor names who initiated, not which code ran: `acceptUpdates` behind
  the Update button is the user's edit, behind the `auto-update` flag
  the workbench's — and per-actor undo then does the right thing, since
  a background edit never enters the user's stack. Per-source actor
  names (`auto-update`, `llm`, …) beat one generic `workbench`: the name
  carries provenance and lets policy differ — an auto-update applies
  silently (the document opted in), while an LLM's edits could
  *propose*, arriving as pending updates the document accepts (the
  suggest-and-accept shape
  [`../foundation/document/store/`](../foundation/document/store/DESIGN.md)
  already defines for dependency bumps). Two pieces are missing for this
  to be real: curated `url@v` versions don't carry an author — only the
  synced change graph does, so an author field is a
  `Store`/persistence schema change — and a collaborator streaming
  edits wants the same debounce-coalescing a typing burst does
  (*Granularity*), one change per turn rather than per keystroke.
  Attribution itself needs no session: an author field on the version
  suffices until peers exist.
- *Granularity* (planned). A history entry is an Automerge change, not a
  keystroke. `DocSession` should coalesce rapid consecutive local edits
  into one change on a short debounce, so a typing burst becomes one
  undoable unit; a remote message, a persist, or session close would
  flush pending edits immediately.
- *Visibility.* Peers sharing a document see its full draft history;
  checkpointing is not a visibility boundary — only sharing is.
  Ephemeral state (peer cursors, selections, eventually keystroke-level
  live preview) belongs to the future presence channel, which broadcasts
  state without touching the change graph.

## Curated checkpoints

The `Store`'s append-only `url@v` versions are the git-like commit layer
over the operational log: a save is a labeled, deliberate, addressable
checkpoint (`url@version#node` refs already resolve them). The
operational history is complete but unbounded — the op log may be
compacted between checkpoints — while the curated layer holds the
meaningful, branchable story.

## History of history

Operations that change history itself — restores, branch creation,
prunes, compactions, curated checkpoints — need a synced meta-log, not
unilateral deletion: a locally pruned change is simply re-offered by any
peer that still holds it, so "prune below hash X" must itself be a
shared, versioned operation that peers honor. Ephemeral navigation never
enters this log; it is the audit trail that makes cleanup safe under
sync.
