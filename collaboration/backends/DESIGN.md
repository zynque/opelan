# `collaboration/backends/` — design

Sync transports: how a `DocSession`'s pairwise Automerge sync messages
reach a peer (see [`../DESIGN.md`](../DESIGN.md)).

- **`SyncTransport`** — the transport interface a session exchanges sync
  messages over.
- **`BroadcastTransport`** — tab-to-tab sync over `BroadcastChannel`,
  with no server: today's Sync toggle.

Signaling (for real P2P across machines) and presence (cursors,
selections) are not built yet; once real transports exist, the per-tab
toggle becomes the per-document sharing switch described in *Backup and
sharing are separate toggles* ([`../DESIGN.md`](../DESIGN.md)).
