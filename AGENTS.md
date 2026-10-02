# AGENTS.md

## Code style

- **Small files**: aim for ~100 lines maximum per file. If a file grows past
  that, split it by responsibility rather than letting it sprawl.
- **Encapsulation**: each file should own one concern. Prefer small traits or
  classes with a focused responsibility over large classes that do everything.
  Example: `ui/editor/DocumentEditor.scala` is a thin orchestrator composed of
  single-responsibility traits (state, commands, ops, clipboard, session,
  keys, view, row, toolbar), each in its own file.
- **Pure logic separated from effects**: keep document/tree operations pure
  (`foundation/`) and DOM/event code thin (`ui/`). Pure helpers that tests can
  exercise live on companion objects (e.g. `DocumentEditor.parseNodeData`).
- **Immutability by default**: prefer `val`s, `case class`es, and immutable
  collections. Reserve `var`/`mutable` for the effect boundary — DOM nodes,
  live `Instance`s, the `Runtime` queue — or for local accumulators that
  don't escape their function (e.g. `Vector.newBuilder`).

## Build & test

- Scala CLI is at `C:\Users\Jeremy\AppData\Local\Coursier\data\bin\scala-cli.bat`
  (not on the default bash PATH).
- Test: `test.bat` or:
  `scala-cli.bat test --server=false --power project.scala foundation ui data collaboration test`
- Browser bundle: `build.bat`, which packages Scala.js output to `app.js`
  and runs webpack to produce `bundle.js`. Packaging to an existing `app.js`
  requires `--force` (build.bat deletes it first).

## Notes

- Public lineage references: prototypes live at
  `https://github.com/zynque/olw-p1` … `olw-p4`.
