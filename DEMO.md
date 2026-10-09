# Demo

Plan for a shareable demo that proves the make-or-break bet of
`ADOPTION.md`: an end-user application whose implementation is a document
you can reach, edit, and watch take effect — no toolchain. Companion to
`STATUS.md` (what exists) and `DESIGN.md` (architecture).

## Candidate applications

The demo must show: app → navigate to definition → change → see effect.

- **Toy spreadsheet — "Budget" (recommended).** A small grid: items with
  qty × price, computed totals. Unmistakably an *application* — the
  ADOPTION argument is that Excel/Notion won domain-expert programming, so
  a spreadsheet is the most honest flex. Strongest impl-change moment: a
  formula sitting as a hole (`total / people`, `/` not yet in the grammar)
  *heals into a live number* when the production is added to the grammar
  document — *Never Broken* + *Evolvable* + bootstrap in one gesture.
  Moderate cost: table view, `*`/`/` and cell refs in the expression
  language, doc-defined-language machinery.
- **Task/project checklist.** Cheapest real app, but reads as a form;
  impl changes are cosmetic (add a field) rather than semantic.
- **Live notes with inline computed expressions** (mini-Notion). Cheapest
  option that still demos language composition (notes language embeds the
  expression language by ref — DESIGN open question 7), but a rendered
  document undersells the application story.

The bootstrap-as-demo alternative was considered and set aside: an
end-user app is more compelling, and the bootstrap shows anyway because
the app's implementation *is a document in the same workbench*.

## The demo, narrated

Sidebar documents: `Household budget`, `lang: sheet`, `lang: expression`.

**Act 1 — the app.**

1. Open `Household budget` — renders as a grid. Not a program: a
   document; everything on screen is a derived view.
2. One cell shows `?` — formula `total / people` is a hole because `/`
   is not in the grammar. The app still works; every other cell
   computes. (*Never Broken.*)
3. Click a `line total` cell → the document pane opens with that formula
   node selected. Edit `price` → the grid recomputes in place.
   (*Reactive*, provenance navigation per DESIGN §7.)
4. Flip to the text surface: `qty * price` as text, the structural
   editor shows the same subtree. (*Polysyntactic* — already works.)

**Act 2 — under the hood.**

5. Follow the document's type ref (Ctrl+Enter machinery exists) → lands
   on `opelan:lang/sheet`, a *document*: node kinds, cell formulas typed
   by `opelan:lang/expr`, the view declaration — editable in the same
   editor.
6. Follow the formula's type ref → `opelan:lang/expr`, the grammar
   document.
7. Add the `/` production. Return to the budget: the `total / people`
   hole has healed — the cell computes. No rebuild, no redeploy; a
   running application's language changed by editing a document. **The
   moment.**
8. Cosmetic contrast: edit the currency format or a column label in
   `lang: sheet` → grid restyles. View specs are data too.

**Act 3 — the substrate flex.**

9. The budget's type ref is `opelan:lang/sheet@3` — pinned. Bumping to
   `@4` is itself an edit ("the app upgraded"); the pinned old version
   still opens and computes correctly. (*Evolvable*; `url@version#node`
   already implemented.)
10. Second tab → Sync → edit a price in one tab, totals update in both.
    (BroadcastTransport already works.)

Close: the app, its data, and its implementation are three documents in
one store — addressable, versioned, editable in place. Nothing was
compiled.

## Steps to get there

**M1 — the app, impl still in code** (de-risks everything but the
bootstrap):

1. Extend `Frag`/`render` into a small styled tree (text, hole,
   table/grid, heading) where fragments carry producing node ids; render
   in `TypedDocSidebar`. Pure, testable.
2. Extend `Expr`: `*`, `/`, `FloatData` (money), `Ref` — text `A1` ↔
   `InternalNodeRef`; eval takes a ref-resolver over the enclosing doc.
   Bonus: "go to definition" on a cell ref is already the internal-ref
   jump.
3. `SheetLanguage` in Scala: rows/cells schema, `sheet` view (computed
   grid), `formulas` view.
4. Click-in-app → select source node (frag provenance → selection). App
   surface stays read-only; editing happens in existing panes — key
   scope cut.
5. Seed the three demo docs; store a *descriptive* `lang: expr` doc
   under the type ref so under-the-hood navigation lands somewhere real
   even before it is authoritative.

**M2 — the bootstrap bet:**

6. Define the language-definition document format: node-kind schema +
   view spec + a minimal declarative grammar sufficient for
   infix-expression languages. Eval semantics stay named built-ins
   (`expr-eval`) — honest scaffolding; semantics-as-documents is later
   work, and the demo should say so.
7. `DocLanguage`: `languageForRef` gains Store access, resolves the
   ref to the stored def doc, interprets it into `Language`
   (`views`/`render`/`parse`).
8. Reactivity: when a def doc version bumps, open documents re-resolve
   their language and re-render — the wiring behind act-2 step 7.

**M3 — flex:** version-pin upgrade path, tab-sync in the demo script,
USAGE/demo notes.

**Explicit cuts:** no type system, no editable app surface, no
auto-migrations, no network transport.

**Risk:** step 7's grammar-as-document interpreter. Keep the grammar DSL
tiny; M1 alone still yields a navigable, reactive demo if it runs long.
