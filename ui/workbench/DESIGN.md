# `ui/workbench/` — design

`Workbench`: the application shell mounted by `Main`. Every decision —
open, save, follow, sync, branch — is a pure transition over
`WorkbenchModel` (`WorkbenchUpdate`, `WorkbenchEvents`,
`WorkbenchDocs`), and every effect is a declared child: invisible *seam*
components in [`ui/fx`](../fx/DESIGN.md) (`StorageHub`, `Prompt`,
`Random`, `SyncHub`) receive requests as props and report results back
as inputs. Document-store decisions live in the pure `Workspace` value;
markup lives in `WorkbenchView`; `WorkbenchShell` is the browser edge.

The view is a sidebar listing stored document heads (with New), a
toolbar (a switch between the **document** pane hosting
[`TypedDoc`](../typeddoc/DESIGN.md) and a **components** demo pane, plus
Save, Sync, and Reset), and a status bar; the expression and sheet
samples load from the pane's own toolbar. On first load, `DemoDocs`
seeds each registered language's `langDoc` at the URL its type ref
names, plus the household budget — never overwriting a URL the store
already holds. Ctrl+Enter on a ref node follows it: internal refs select
their target in place; external refs bubble up, resolve through the
[`Store`](../../foundation/document/store/DESIGN.md), and open the
pinned version at the referenced node.

The save path runs the auto-update fixpoint pass (`WorkbenchDocs`):
every `auto-update`-opted document still pinned behind a head gets its
pins bumped as an ordinary edit — see *Pins select behavior* in
[`foundation/document/store`](../../foundation/document/store/DESIGN.md).
