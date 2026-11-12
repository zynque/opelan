# `ui/text/` — design

`CodeMirror.scala`: a thin dynamic-`JSImport` wrapper over CodeMirror 6
(`updateListener` → `onChange` per document-changing transaction),
mounted through [`fp`](../fp/DESIGN.md)'s `View.Managed`. A comment
documents the 6.x-only package constraint.

`mountMarked` mounts the same editor with line decorations: the caller
supplies a `text => List[LineMark]` (a line class plus inline
`class`ed spans), and a `StateField` recomputes the decoration set on
each document-changing transaction — the incremental-repaint half of
the def text surface. `marksTheme` holds the palette: meta constructs,
payload kinds, `means`/`where` keywords, placeholders, gaps
(dotted-underlined red) and unmatched text.
