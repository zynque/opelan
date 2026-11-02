# `ui/editor/` — design

`DocumentEditor`: an outliner-style structural editor for raw
`Document[NodeData]`, built as an [`fp`](../fp/DESIGN.md) component and
split one concern per file (`EditorModel`, `EditorUpdate`,
`EditorSession`, `EditorDocOps`, `EditorClip`, `EditorKeys`,
`EditorHistory`, `EditorHistoryView`, `EditorSynced`, `EditorView`,
`EditorRow`, `EditorLabel`, `EditorSample`). It is keyboard-driven:
Enter/Shift+Enter insert, Tab indents, F2 or double-click edits inline,
subtrees cut/copy/paste, `Compact` garbage-collects, and undo/redo walk
the branching version history (see
[`../../foundation/version/DESIGN.md`](../../foundation/version/DESIGN.md);
for a synced document the DAG is rebuilt from the shared Automerge
change graph — `EditorSynced`, see
[`../../collaboration/DESIGN.md`](../../collaboration/DESIGN.md)). The
sample document is the manifesto rendered as a tree — dogfooding.
