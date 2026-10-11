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

1. Open `Household budget`, switch to the `sheet` view — the spreadsheet
   alone fills the pane; no outline, no machinery. Not a program: a
   document; everything on screen is a derived view.
2. One cell shows `?` — formula `total / people` is a hole because `/`
   is not in the grammar. The app still works; every other cell
   computes. (*Never Broken.*)
3. Click a `line total` cell → its formula node is selected in the
   (hidden) editor. Then edit `price` *in the grid itself* — the cell is
   a projection: typing a new value rewrites the source node and the grid
   recomputes in place. (*Reactive*, provenance navigation per DESIGN §7,
   projectional editing.)
4. Two navigations out of the app surface: **Raw** shows this document's
   own representation (the outline editor, where the last-clicked cell's
   source node is selected); **Definition (F12)** follows the document's
   type ref — "go to definition" for the app itself.

**Act 2 — under the hood.**

5. From the sheet: F12 or the **Definition** button follows the type ref
   → lands on `opelan:languages/sheet`, a *document*: node kinds, cell
   formulas typed by `opelan:languages/expr`, the view declarations —
   editable in the same editor. (Today it lands on the descriptive
   outline; when the definition format gets its own UI, go-to-definition
   jumps straight to it — same gesture, deeper landing.)
6. Follow the formula's `ext:` ref → `opelan:languages/int-expression`,
   the expression language's document.
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

1. ~~Extend `Frag`/`render` into a small styled tree where fragments
   carry producing node ids~~ — done as **view trees**: `render` returns
   a `DetachedNode[NodeData]` in the `foundation/view` vocabulary
   (block/flow/heading/grid/row/cell, text leaves, `GapData` holes);
   `NodeData.SourceRef` first-children carry producing node ids; one
   recovering interpreter (`ui/view`) renders them in `TypedDocSidebar`.
   The same vocabulary is what a language doc's view spec will encode.
2. ~~Extend `Expr`: `*`, `/`, `FloatData` (money), `Ref`~~ — done:
   `Mul`/`Div`/`FLit`/`Ref` cases; `Num` keeps ints integral and floats
   decimal (display-rounded); precedence-aware lexer/parser/print; eval
   takes a ref-resolver over the enclosing doc with cycle detection.
   Ref text is `ref:N` (matching the outline codec) rather than `A1` —
   `A1` needs row/col naming machinery, deferred. Bonus delivered:
   "go to definition" on a cell ref is the ordinary internal-ref jump.
3. ~~`SheetLanguage` in Scala: rows/cells schema, `sheet` view (computed
   grid), `formulas` view~~ — done: `sheet > row/hrow > cell` schema;
   `sheet` view evaluates each cell (labels pass through, formulas
   resolve through the document), `formulas` prints each cell's
   expression; a blocked or cycling ref degrades only the dependent
   cells. Seeded "Household budget" behind the **Sheet sample** toolbar
   button.
4. ~~Click-in-app → select source node~~ — done: `SourceRef` provenance →
   `SelectSource` → the editor child selects the producing node (a
   same-doc `SyncDocument` push records no version). App surface stays
   read-only; editing happens in existing panes — key scope cut.
5. ~~Projectional cell editing~~ — done: an `input` element in the view
   vocabulary (first child is a `SourceRef` naming the node it edits —
   the provenance convention, semanticized). The sheet view emits one
   per leaf cell (labels, literals, holes — refs and formulas stay
   read-only computed values); the `formulas` view makes every cell an
   input showing `cellText` (so formulas edit as `ref:N * ref:M` text).
   Commit parses via `Language.parseCell` and rewrites the node *in
   place* (`replaceNodeContent`) — the id survives, so `ref:N` cells
   pointing at it keep working and the grid recomputes; an unchanged
   commit is a no-op, so blur can't churn versions.
6. ~~Seed the three demo docs; store a *descriptive* `lang: expr` doc
   under the type ref~~ — done: `Language.langDoc` makes every
   registered language self-describing; hydration seeds each `langDoc`
   at the URL its `typeRef` names (version 0 — the pinned version) plus
   the budget at `opelan:docs/household-budget`, never overwriting a
   URL the store already holds. Ctrl+Enter on the budget's type ref
   lands on `lang: sheet`; its "formula" cell kind links on to
   `lang: expression` via an `ext:` ref, so act-2 steps 5–6 are
   navigable now — descriptive, not yet authoritative (that's M2).

**M2 — the bootstrap bet:**

7. Define the language-definition document format: node-kind schema +
   view spec + a minimal declarative grammar sufficient for
   infix-expression languages. Eval semantics stay named built-ins
   (`expr-eval`) — honest scaffolding; semantics-as-documents is later
   work, and the demo should say so.
8. `DocLanguage`: `languageForRef` gains Store access, resolves the
   ref to the stored def doc, interprets it into `Language`
   (`views`/`render`/`parse`).
9. Reactivity: when a def doc version bumps, open documents re-resolve
   their language and re-render — the wiring behind act-2 step 7.

**M3 — flex:** version-pin upgrade path, tab-sync in the demo script,
USAGE/demo notes.

**Explicit cuts:** no type system, no editable app surface, no
auto-migrations, no network transport.

**Risk:** step 7's grammar-as-document interpreter. Keep the grammar DSL
tiny; M1 alone still yields a navigable, reactive demo if it runs long.
