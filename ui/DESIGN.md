# `ui/` — design

The effect boundary: DOM, events, and browser APIs live here, kept thin
over the pure core in [`foundation/`](../foundation/DESIGN.md).
Everything is an [`fp`](fp/DESIGN.md) component: pure `update`
transitions produce output values, and effects are declared children
interpreted at the edge — not hidden in model code.

Packages:

- [`fp/`](fp/DESIGN.md) — the component runtime (Elm/Tyrian-style)
- [`editor/`](editor/DESIGN.md) — `DocumentEditor`, the structural
  outliner for raw documents
- [`typeddoc/`](typeddoc/DESIGN.md) — `TypedDoc`, the typed-document
  pane with several surfaces over one document
- [`text/`](text/DESIGN.md) — the CodeMirror 6 wrapper
- [`view/`](view/DESIGN.md) — `ViewTreeView`, the view-tree interpreter
- [`fx/`](fx/DESIGN.md) — invisible seam components for effects
- [`workbench/`](workbench/DESIGN.md) — `Workbench`, the application
  shell

Cross-cutting UI design — navigation across surfaces, zoom axes, jump
history — is in the root [`DESIGN.md`](../DESIGN.md) (*Navigation*).
