# `ui/text/` — design

`CodeMirror.scala`: a thin dynamic-`JSImport` wrapper over CodeMirror 6
(`updateListener` → `onChange` per document-changing transaction),
mounted through [`fp`](../fp/DESIGN.md)'s `View.Managed`. A comment
documents the 6.x-only package constraint.
