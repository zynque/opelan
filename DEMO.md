# Demo

Plan for a shareable demo that proves the make-or-break bet of
`ADOPTION.md`: an end-user application whose implementation is a document
you can reach, edit, and watch take effect — no toolchain. What is built,
what still blocks the demo, and the steps to finish it are in `STATUS.md`;
the architecture is in `DESIGN.md`.

## Candidate applications

The demo must show: app → navigate to definition → change → see effect.

- **Toy spreadsheet — "Budget" (chosen).** A small grid: items with
  qty × price, computed totals. Unmistakably an *application* — the
  ADOPTION argument is that Excel/Notion won domain-expert programming, so
  a spreadsheet is the most honest flex. Strongest impl-change moment: a
  formula sitting as a hole (`total / people`, `/` not yet in the grammar)
  *heals into a live number* when the production is added to the grammar
  document — *Never Broken* + *Evolvable* + bootstrap in one gesture.
- **Task/project checklist.** Cheapest real app, but reads as a form;
  impl changes are cosmetic (add a field) rather than semantic.
- **Live notes with inline computed expressions** (mini-Notion). Cheapest
  option that still demos language composition (a notes language
  embedding the expression language by ref), but a rendered document
  undersells the application story.

The bootstrap-as-demo alternative was considered and set aside: an
end-user app is more compelling, and the bootstrap shows anyway because
the app's implementation *is a document in the same workbench*.

## The demo, narrated

Sidebar documents: `Household budget`, `lang: sheet`, `lang: expression`.

**Act 1 — the app.**

1. Open `Household budget`, switch to the `sheet` view — the spreadsheet
   alone fills the pane; no outline, no machinery. Not a program: a
   document; everything on screen is a derived view.
2. One cell shows `?` — formula `total / people` is a hole because `/`
   is not in the grammar. The app still works; every other cell
   computes. (*Never Broken.*)
3. Click a `line total` cell → its formula node is selected in the
   (hidden) editor. Then edit `price` *in the grid itself* — the cell is
   a projection: typing a new value rewrites the source node and the grid
   recomputes in place. (*Reactive*, provenance navigation per DESIGN §6,
   projectional editing.)
4. Two navigations out of the app surface: **Raw** shows this document's
   own representation (the outline editor, where the last-clicked cell's
   source node is selected); **Definition (F12)** follows the document's
   type ref — "go to definition" for the app itself. In the raw view the
   document is all references: an imports section holds each construct's
   `ext:` address once, and every use is an `&n` internal ref to an
   entry — shown with its resolved name (`&54 [mul]`), so the tree still
   reads plainly while a rename of `mul` in its definition would relabel
   every use.

**Act 2 — under the hood.**

5. From the sheet: F12 or the **Definition** button follows the type ref
   → lands on `opelan:languages/sheet`, a *document*: node kinds, cell
   formulas typed by the expression language, the view declarations —
   editable in the same editor. The doc is itself typed (by
   `lang: language`, the def-definition language), so it gets a `def`
   view and F12 bottoms out at the self-describing `lang: language`
   doc.
6. Follow the formula's `ext:` ref → `opelan:languages/int-expression`,
   the expression language's document. With `means` rules, both
   definitions read as rules rather than outlines of combinators.
7. Add the `/` production — with `means` rules, one line the audience
   can read,

   ```
   a / b  means  div(a, b)
   ```

   plus `a * b, a / b` on the binding list (before rules land, an `/`
   entry in the grammar's precedence node). Return to the budget: the
   `total / people` hole has healed — the cell computes. No rebuild, no
   redeploy; a running application's language changed by editing a
   document. **The moment.**
8. Cosmetic contrast: edit the currency format or a column label in
   `lang: sheet` → grid restyles. View specs are data too.

**Act 3 — the substrate flex.**

9. The budget's type ref is `opelan:languages/sheet@0` — pinned. Bumping
   to a later version is itself an edit ("the app upgraded"); the pinned
   old version still opens and computes correctly. (*Evolvable*.)
10. Second tab → Sync → edit a price in one tab, totals update in both.

Close: the app, its data, and its implementation are three documents in
one store — addressable, versioned, editable in place. Nothing was
compiled.

## Scope

Explicit cuts: no type system, no auto-migrations of dependents, no
network transport. Projectional editing is limited to the grid's cells
and the definitions' text surface.
