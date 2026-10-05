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
- **text** — a CodeMirror cell holding the document's text surface.
  Typed documents use their language's parse/print; everything else uses
  the outline format. Edits reparse the whole cell; malformed input
  becomes holes, not errors.
- **Sidebar** — the language's derived views (e.g. `print`, `eval` for the
  expression language), re-rendered live on each change. Holes render as
  yellow chips.

Toolbar also shows the document's type label ("raw document", the
language name, or `unknown:` ref). **Expr sample** loads a typed
expression-language document.

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
