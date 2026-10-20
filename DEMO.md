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
   formulas typed by `opelan:languages/expr`, the view declarations —
   editable in the same editor. The doc is itself typed (by
   `lang: language`, the def-definition language), so it gets a `def`
   view and F12 bottoms out at the self-describing `lang: language`
   doc. (A projectional text surface for def docs — IDE feel — is a
   future goal below.)
6. Follow the formula's `ext:` ref → `opelan:languages/int-expression`,
   the expression language's document.
7. Add the `/` production (after M4: the line `a / b means div(a, b)`).
   Return to the budget: the `total / people`
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
   Commit parses via `Language.parseCell` and reconciles the result into
   the node *in place* (`commitContent`) — the id survives, the ids of
   unchanged subtrees survive, so `ref:N` cells pointing at it keep
   working and the grid recomputes; an unchanged commit is a no-op, so
   blur can't churn versions.
6. ~~Seed the three demo docs; store a *descriptive* `lang: expr` doc
   under the type ref~~ — done: `Language.langDoc` makes every
   registered language self-describing; hydration seeds each `langDoc`
   at the URL its `typeRef` names (version 0 — the pinned version) plus
   the budget at `opelan:docs/household-budget`, never overwriting a
   URL the store already holds. Ctrl+Enter on the budget's type ref
   lands on `lang: sheet`; its "formula" cell kind links on to
   `lang: expression` via an `ext:` ref, so act-2 steps 5–6 are
   navigable now — descriptive, not yet authoritative (that's M2).

**M2 — the bootstrap bet** (combinator-grammar direction: a precedence
table is just one meta-combinator — `precedenceTable(atom, levels)`
folds `chainLeft` over op sets — so the grammar format composes named
combinators and grows toward general parsing rather than hardcoding
operator tables. The twist vs Haskell-style combinators: ours are
*total* — they produce `GapData` holes instead of failing, since a
holey document must still parse (Never Broken)):

7. ~~Recovering parser combinators (`foundation/grammar`): atoms, choice,
   sequencing, `chainLeft`, `precedenceTable`, `parens`, `phrase` over
   generic tokens, producing `DetachedNode[NodeData]` trees with spans.
   The expression grammar is re-expressed as a combinator composition~~ —
   done: `ExprParse` is now `phrase(precedenceTable(atom, levels))` over
   tokens (since made scannerless: character-level lexicon rules), where `atom = int | float | ref | '?' | parens | signum |
   junk` and `levels` is the tightest-first op→tag table. All parser
   tests pass unchanged — identical trees and spans prove the machinery.
8. ~~`LanguageDef` + `DefLanguage` + builtins: the in-memory ADT a def
   describes (schema, grammar-as-combinator-composition, view bindings
   to named built-ins like `expr-eval` — eval semantics stay in code,
   honest scaffolding), interpreted into a `Language`~~ — done:
   `GrammarDef` is the data twin of the combinator set (`Named`/`Ref`
   encode recursion), `interpret` walks it to a `Parser`, and the
   builtins registry names the code-resident semantics (`expr-print`,
   `expr-eval`, `sheet-grid`, `sheet-formulas`, the `expr` lexer,
   `sheet-cell`). `DefLanguage(exprDef)` is proven identical to
   `ExprLanguage` — parse/print/eval agree on a 28-input battery.
9. ~~The def-doc format: recovering `langDefFromDocument` /
   `langDefToDetached`; the seeded `lang:` docs regenerate in the real
   format. The sheet's cell grammar names the expression language *by
   ref*, so editing `lang: expression` heals both surfaces~~ — done:
   `LanguageDef` gained `title`/`description`/`cellParser`
   (`CellParserDef.Language(ref, fallback)`), `DefDocEncode`/`Decode`/
   `Reader` are the codec (sorted sets/ops → deterministic bytes), the
   seeded `lang:` docs are the real encoding *typed* by
   `lang: language`, and a damaged def doc decodes to holes (cyclic
   docs and cyclic `Ref`s included).
10. ~~`DocLanguage`: `languageForRef` gains Store access — resolve the
    ref to the stored def doc, interpret it into `Language`; the Scala
    registry stays the fallback~~ — done: `DocLanguage` is
    `DefLanguage(docLanguageDef)` bound to a `def-view` builtin, seeded
    at `opelan:languages/language`; `examples.languageForRef(ref, store)`
    decodes the head version when it's def-typed (registry + DocLanguage
    as fallbacks), and the pane's `store` prop carries the resolution
    context through view/sidebar/cell-commit paths.
11. ~~Reactivity: resolution reads the current store per render/push, so
    returning to the budget after editing `lang: expression` re-heals
    its holes — mostly free once step 10 lands~~ — done via the `Pane`
    store prop: `languageForRef` resolves `store.head` on every render,
    so a saved def-doc edit is live on the next redraw. (Deliberate
    semantics: *execution* resolves at head — live — while
    go-to-definition refs stay version-pinned. An old persisted store
    with untyped `lang:` docs just falls back to the registry.)

**M3 — flex:** version-pin upgrade path, tab-sync in the demo script,
USAGE/demo notes.

**M4 — `means` rules (next major series).** M2 proved a language can be a
document, but the document is combinator data (`precedence`, `chain`,
`signum`, `junk`, stop sets) — readable by its author, not by the
domain expert the demo is aimed at. M4 replaces it with
`pattern means meaning` rules and a tightest-first binding list (DESIGN.md
§4). The combinators stay as the parsing substrate; rules compile onto
them, with operators and precedence handled by one precedence-climbing
combinator. In phases: a rule compiler (scannerless combinators — no
separate lexers — made incremental by packrat memoization, then the
operator combinator, printer, and schema); the expression language on rules at parity
with `ExprLanguage`; rules as `lang:` documents with a `means` text surface
and live worked examples; semantics as rules bottoming out in builtins,
with stored combinator-format docs migrated and `GrammarDef` demoted to
the implementation layer (visible under show implementation); then the sheet's
positional refs, `where` with adjustable verbosity, and self-description.
The by-reference document format already landed ahead of it, so a
meaning's constructor (`div(a, b)`) will refer to the `div` kind by
identity — a rule, like every other construct use, survives renames.
The step-by-step breakdown is in `STATUS.md` (next steps, item 6).

What it changes in the demo: act 2's moment (step 7) stops being "add a
`/` entry to an op table inside a precedence node" and becomes adding one
line —

```
a / b  means  div(a, b)
```

— plus `a * b, a / b` on the binding list. The `total / people` hole
heals the same way, but the audience can read the change. Step 5's
`lang: sheet` and step 6's `lang: expression` read as rules rather than
outlines of combinators, which also covers most of the "projectional
source editor for definitions" goal below.

**Explicit cuts:** no type system, no editable app surface, no
auto-migrations, no network transport.

**Risk:** step 7's grammar-as-document interpreter. Keep the grammar DSL
tiny; M1 alone still yields a navigable, reactive demo if it runs long.

## Beyond the demo

Goals deliberately out of scope for the demo but on the roadmap:

- **Projectional source editor for definitions.** Language and grammar
  definitions are today edited as outlines — adequate, but not the IDE
  feel. A `lang:` doc should eventually get a text-like projectional
  surface (a def view emitting `input` cells over grammar nodes, in the
  spirit of the sheet's `formulas` view), so navigating to
  `lang: sheet` lands on something that reads like editing a grammar
  file rather than a tree. The document stays the source of truth; the
  text is a projection.
- **Contextual F1 help.** Help is just documents: F1 on a cell opens
  the help page for that construct (what a cell is, an example), and
  from there links on to the implementation that makes it work — the
  language def, the grammar, the builtin — for users who want to look
  under the surface. Same navigation machinery as go-to-definition;
  the help pages would be seeded docs linked by ref from schema/node
  kinds.
