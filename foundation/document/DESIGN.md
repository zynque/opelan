# `foundation/document/` — design

The substrate everything is built on — a direct port of olw-p4, extended:

- **`Document[A]`** — a persistent tree stored as `rootId` + `Vector[Node[A]]`.
  Node ids are indexes into the vector and are **never reused**. Each node
  carries `version`, `data`, `childIds`, and `parentId`. Pure, immutable,
  and cheap to snapshot — which is what makes undo trivial.
- **`DetachedNode[A]`** — an id-less tree for building content before
  insertion. `buildDocument` assigns ids post-order, so each subtree
  occupies a contiguous id range. The `Detached` object provides compact
  builder syntax (`n(s("a"), sl("b"), il(2))`).
- **`Edit.scala`** — pure edit operations returning
  `Either[String, Document[A]]`: `insertNode`, `cutNode` (unlinks; the
  node stays in the vector), `pasteNode` (re-attaches, keeping identity),
  `moveNode` (atomic cut+paste with a cycle check), `extractSubtree`,
  `updateNodeData`, `updateNodeChildIds`, `reachableIds`, `compact`, and
  `validate` (structural integrity check).
- **`compact`** — garbage-collects unreachable nodes, rewriting ids and
  returning an old→new id map. It is a deliberate **lineage boundary**:
  callers must remap any `InternalNodeRef`s in node data.
- **`NodeData`** — the concrete payload type: `StringData`, `IntData`,
  `FloatData`, `InternalNodeRef(nodeId)`, `ExternalNodeRef(ref)` — a
  transclusion-style cross-document reference whose
  `ExternalNodeReference(documentUrl, documentVersionId, nodeId)` reads
  as `url@version#node` — **`GapData(text)`** (a gap, see
  [`../DESIGN.md`](../DESIGN.md)), and **`SourceRef(nodeId)`**, a pointer
  to the definition, which, when interpreted, produced this node.
- **`Outline`** — a text format for raw documents: one line per node,
  indentation for nesting, and surface syntax for each `NodeData` variant
  (`"abc"`, `42`, `1.5`, `ref:5`, `ref:url@v#n`, `?text`). It is the human
  text surface; a parse renumbers nodes.
- **`Serial`** — the storage and sync format: the outline with each line
  prefixed by its node id (`#17 "mul"`). Node identity survives save,
  reload, and sync, which documents depend on. Internal refs (imports,
  construct uses, cell refs) and other documents' `url@v#n` refs all
  name ids. Unreachable ids stay reserved, duplicate ids from a text
  merge get fresh ones, and id-less text falls back to `Outline`. This is
  an interim solution — the long term plan is to use a proper CRDT
  reflecting the document structure.
- **`Show`** — debug rendering, including node versions and links.

Sub-packages:

- [`typed/`](typed/DESIGN.md) — the typed-document layout (type ref,
  imports, title, content), everything-by-reference, the imports
  machinery, and `commitContent`/`Reconcile`.
- [`store/`](store/DESIGN.md) — the versioned `Store` keyed by URL, the
  `refName` projection, and the pin/pending-update machinery.
