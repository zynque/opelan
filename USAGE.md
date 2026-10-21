# Usage

How to drive the Opelan workbench today. Run `build.bat`, serve the repo
root, open `index.html`.

## Workbench shell

- **Documents sidebar** — lists stored document heads as `label vN`. Click
  one to open its head version. **New Document** prompts for a name and
  creates `untitled@v0` (etc.) under a slugified URL.
- **Toolbar** — **Document** / **Components** switch the main pane between
  the document workbench and the fp-components demo. **Save** appends a
  new version of the open document to the store (IndexedDB) — every save
  is a new `vN`, old versions are never overwritten.
- **Sync** — toggles live sync of the open document between tabs of this
  browser (BroadcastChannel; no server). Edits in either tab appear in the
  other; concurrent edits merge. Synced documents stay drafts — saving to
  the store is still explicit.
- The status bar shows the last action and the current `url@v` label.

## Document pane (TypedDoc)

Three surfaces over one document:

- **editor** — the structural outliner (below).
- **text** — the document's text surface in a CodeMirror editor.
  Typed documents use their language's parse/print; everything else uses
  the outline format. Edits reparse the whole text; malformed input
  becomes holes, not errors.
- **Sidebar** — the language's derived views (e.g. `print`, `eval` for the
  expression language; `sheet`, `formulas` for the sheet language),
  re-rendered live on each change. Holes render as
  yellow chips. Fragments tagged with a source node show a `node #n`
  tooltip and select that node in the structural editor when clicked.

Toolbar also shows the document's type label ("raw document", the
language name, or `unknown:` ref), plus two update controls: **Update
(N)** appears when `N` of the document's external refs pin versions
behind a store head — a pending update — and clicking it bumps every
pin to its head as one ordinary, versioned edit; **auto-update** writes
an opt-in flag into the document so those accepts happen without the
click (saving a dependency cascades pin bumps through every flagged
dependent). A **broken: N** marker counts refs pinning a node that no
longer exists at the head — they can't be bumped; the tooltip lists
them. **Expr sample** loads a typed expression-language document;
**Sheet sample** loads the typed "Household budget" spreadsheet — rows
of label, literal, and formula cells (`qty * price`, a summed total,
`total / people`) whose `sheet` view computes values and whose
`formulas` view shows the expressions.

Sheet cells are projected editors: value cells (labels, numbers, holes)
and every cell in the `formulas` view show a text box — edit in place,
Enter or click-away commits, Escape reverts. A commit rewrites the
source node in place, so formulas referencing the cell recompute.
Typing `?` writes a hole; text that parses as an expression (numbers,
`ref:N`, `a * b`) becomes formula content; text that doesn't parse but
mentions a cell (`ref:N`) is kept as a hole; anything else is a label.
Hole cells show in yellow. The cell grammar is `lang: expression` at
the version the sheet's ref pins, so a hole heals (computes) once the
sheet's pin bumps to a version that can read it — via the Update
button or auto-update, not just by saving the definition. Computed
values are formatted by the `sheet` view's `currency` setting in
`lang: sheet`.

The seeded `lang: expression` starts without `/`, so the budget's
`per person` cell starts as a hole: the demo's healing moment
(`DEMO.md`). **Reset** clears the store and reseeds.

## Structural editor keys

With the outline focused:

| Key | Action |
|---|---|
| Up / Down | move selection (pre-order) |
| Enter | new sibling after selection |
| Shift+Enter | new child of selection |
| Tab / Shift+Tab | indent / outdent |
| F2 or double-click | edit node inline; Enter commits, Escape cancels |
| Delete / Backspace | remove subtree (stays detached until Compact) |
| Ctrl+X / Ctrl+C / Ctrl+V | cut / copy / paste subtree |
| Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y | undo / redo |
| Ctrl+Enter | follow the selected node's ref |
| Escape | deselect |

Toolbar buttons: **New** (empty doc), **Sample** (manifesto doc),
**Compact** (garbage-collect detached nodes, remap ids).

### Node data syntax

Typed into the inline edit box:

- `abc` → string `"abc"`
- `42`, `1.5` → int / float
- `&5` → internal ref to node 5
- `ext:url@v#n` → external ref to node `n` of `url`'s version `v`
- `?text` → gap (hole) holding `text`

### Following refs

Ctrl+Enter on an `&n` node selects the target in place. On an
`ext:url@v#n` node it resolves through the store and opens the pinned
version at that node.

## History column

Every edit records a version in the DAG shown at the editor's right edge.
Undo walks up; a new edit after undo creates a sibling branch rather than
truncating the future. Click any `#id` row to jump to that version. The
status bar shows the current version as `v#id`.
