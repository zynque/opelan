# AGENTS.md

## Code style

- **Small files**: aim for ~100 lines maximum per file. If a file grows past
  that, split it by responsibility rather than letting it sprawl.
- **Encapsulation**: each file should own one concern. Prefer small values,
  functions, or classes with a focused responsibility over large classes that
  do everything. Example: `ui/editor/DocumentEditor.scala` is a thin
  component delegating to single-responsibility files (update, session,
  history, doc ops, clipboard, keys, view, row), each in its own file.
- **Composition over inheritance**: the concern is inheritance used to
  *provide behavior*. Reuse behavior by holding and delegating to a value
  (e.g. `DocSession` owns a `SessionUndo` value) or by passing
  functions/components as parameters (e.g. `Workbench(docPane)`) — not by
  mixing in traits that carry real logic or state, self-types reaching into
  the host's internals, or subclassing concrete classes.
  Fine uses of `extends`:
  - implementing an interface trait (`Component`, `Language`,
    `SyncTransport`, `Cmd`), including small convenience/default methods
    for fluency (`Language.defaultView`, `Component.children = Vector.empty`);
  - sealed ADTs / enum cases;
  - test fakes mirroring an external type hierarchy (`FakeNode` /
    `FakeElement` mimic the DOM);
  - framework/interop requirements (`munit.FunSuite`, `js.Object`).
- **Pure logic separated from effects**: keep document/tree operations pure
  (`foundation/`) and DOM/event code thin (`ui/`). Pure helpers that tests can
  exercise live on companion objects (e.g. `DocumentEditor.parseNodeData`).
- **Scala 3 top-level definitions**: stateless helpers that aren't tied to a
  type live at package top level — don't wrap them in an `object` just to
  hold functions (Scala 2 style). Keep `object` only for companions of a
  type, singleton implementations (`extends Component`, `Language`),
  deliberate import namespaces (`Detached`, `Dsl`), and JS interop facades.
- **Immutability by default**: prefer `val`s, `case class`es, and immutable
  collections. Reserve `var`/`mutable` for the effect boundary — DOM nodes,
  live `Instance`s, the `Runtime` queue — or for local accumulators that
  don't escape their function (e.g. `Vector.newBuilder`).
- **Names: spell words out; shorten only as scope narrows**: top-level
  definitions — types, enum cases, case-class fields, top-level functions
  and values — use full words, not abbreviations (`Literal`, not `Lit`;
  `Token`, not `GTok`; `arguments`, not `args`), and the plain
  domain word over implementation jargon (`Placeholder`, not `Slot`).
  The narrower the scope, the shorter a name
  may be: a lambda parameter or a loop variable in a few-line block can
  be `p` for a point or `t` for a token when context makes it obvious.
  Function parameters and local values in longer bodies sit in between —
  prefer the word. Established domain terms (`url`, `id`) are fine.

## Build & test

- Scala CLI is at `C:\Users\Jeremy\AppData\Local\Coursier\data\bin\scala-cli.bat`
  (not on the default bash PATH).
- Test: `test.bat` or:
  `scala-cli.bat test --server=false --power project.scala foundation ui data collaboration test`.
  The scripts set `TEMP`/`TMPDIR` to `./.tmp` first — the Scala.js test
  runner writes the linked main.js there, and the doc-sync suite needs Node
  to resolve `node_modules` (`@automerge/automerge`) from it. Without the
  redirect the tests fail with `ERR_MODULE_NOT_FOUND`.
- Browser bundle: `build.bat`, which packages Scala.js output to `app.js`
  and runs webpack to produce `bundle.js`. Packaging to an existing `app.js`
  requires `--force` (build.bat deletes it first).

## Git

- The user reviews and commits changes manually — do not commit or push.

## Notes

- Public lineage references: prototypes live at
  `https://github.com/zynque/olw-p1` … `olw-p4`.
