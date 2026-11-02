# `ui/fx/` — design

The effect seam: invisible [`fp`](../fp/DESIGN.md) components that turn
effect requests into results — `StorageHub` (IndexedDB persistence via
[`data/storage`](../../data/storage/DESIGN.md)), `SyncHub` (Automerge
sessions via [`collaboration`](../../collaboration/DESIGN.md)), `Prompt`
(user confirmation), `Random` (id/name generation). They receive
requests as props and report results back as inputs, so every decision
stays a pure transition in the parent component (see
[`../workbench/DESIGN.md`](../workbench/DESIGN.md)).
