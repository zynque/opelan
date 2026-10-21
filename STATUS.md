# Status

Point-in-time state of the Opelan workbench: what works, what doesn't yet,
and what comes next. For architecture and rationale see `DESIGN.md`; for
the demo being worked toward see `DEMO.md`.

Last updated: 2026-10-20

The project is in Phase 1 (foundation & schema system). **Next major
series:** replacing combinator-based language definitions with
`pattern means meaning` rules compiled onto the same combinators (*Next
steps*, item 1).

## Working

Each item is described in the cited `DESIGN.md` section.

- **Document model** (`foundation/document`, §2): persistent tree with
  pure structural edits, `compact`, and `validate`; `Outline` (human
  text) and `Serial` (id-preserving storage and sync) codecs; the
  pure, versioned `Store`.
- **Typed documents and everything by reference** (§4): type ref on the
  root; imports, title, content layout; every construct imported once and
  used by `InternalNodeRef`, so renaming a construct in its `lang:` doc
  renames every use (tested).
- **Version pins select execution** (§4): language resolution reads the
  pinned `url@version`, not the head; `pendingUpdates` finds a
  document's refs pinning behind a head, the toolbar's **Update** button
  accepts them as one ordinary edit, and a `setting(auto-update, true)`
  node opts a document into accepting without the click — in the pane,
  and in a fixpoint cascade on save that republishes flagged dependents
  (tested end-to-end: `/` → sheet v1 → budget). Pins whose node is gone
  at head surface as `broken: N` (an accept can't bump them), and a
  bump itself is cheap: `changedSince` reports a version move as a
  kind-tag migration, so kept parses re-pin rather than reparse.
- **Version DAG** (`foundation/version`, §3): LSCA merge base; drives
  editor history (branching undo, history column).
- **Expression and sheet languages** (`examples/`, §4): the recovering
  expression parser, `print`/`eval` views, ref resolution with cycle
  detection; the sheet's `sheet`/`formulas` grids with projected cell
  editing, holes that heal when the cell grammar can read them, and a
  `currency` view setting; the seeded "Household budget", staged for the
  demo with `/` missing from `lang: expression`.
- **View trees** (`foundation/view`, `ui/view`, §4, §6): one recovering
  interpreter, provenance clicks that select the producing node, and
  `input` commits reconciled in place by `commitContent`.
- **Language definitions as documents** (`foundation/langdef`, §4):
  `lang:` docs typed by `lang: language`, resolved store-first, so a
  saved definition edit is live on the next render.
- **`means` rule groundwork** (`foundation/means`, `foundation/grammar`,
  §4): rule model, checker, reader, and role-tagged printer;
  scannerless combinators; the incremental packrat engine (`Memo`,
  `ParseSession`), driving the text cell through `TextSession`, and
  stored-hole retries through `HoleRetry` — a hole reparses only when
  a language change touched a rule its last attempt consulted;
  id-preserving reconcile (`commitContent`) on both commit paths.
- **UI** (`ui/`, §5): fp runtime, `DocumentEditor`, `TypedDoc` with the
  app surface and its Raw / Definition (F12) exits, `Workbench` with
  seeded demo documents and ref following.
- **Live sync** (`collaboration/`, §7): Automerge between tabs over
  BroadcastChannel; one author-tagged history projected into the
  version DAG; per-actor undo; local navigation; checkout + edit forks a
  branch draft that peers can rejoin.
- **Build/test** (§9): `build.bat`/`build.sh`, `test.bat`/`test.sh`;
  310 munit tests over the pure core, UI update functions, and
  DocSession convergence under real Automerge in Node. A fresh clone
  needs a `webpack.config.js` (see `README.md`).

## Known gaps

- **Demo rough edges.** The full narration in `DEMO.md` was walked in a
  fresh browser on 2026-10-20 (Linux, `./build.sh` + `npx http-server`,
  starting from **Reset**) and every step works. What still reads
  awkwardly:
  - *Adding `/` takes outline surgery:* copy the `*` op node, paste it
    (it lands as a child; outdent it), then edit its symbol and retarget
    its kind ref by node id (`&33`). The `means` series replaces this
    with one readable line.
  - *Healing is derived:* a healed hole computes in both views, but the
    document still holds the hole text until the cell is re-committed
    (e.g. from the `formulas` view).
  - *Update edges:* the cascade runs on local saves only and skips
    synced docs; the full list of pin edges — remote heads, content
    migration, broken-import repair — is item 2 in next steps.
  - *`currency` formats computed cells only;* price inputs stay plain
    numbers.
- **Only the text cell parses incrementally.** Projected cell commits
  still parse each committed text whole (they are short, one cell at a
  time), and synced remote text is the `Serial` outline, parsed by the
  outline codec rather than a language grammar.
- **Synced history:** remote changes apply as a whole-text reparse;
  text-cell edits made while browsing a version still write to the main
  session (branching covers structural edits); undo stacks are
  session-local and lost on resume; branch merge-back is manual; edits
  made before first sync contact resolve last-writer-wins rather than
  merging; edit bursts aren't coalesced. Unsynced session history isn't
  persisted.
- **References:** an `ExternalNodeRef` to a missing target reports
  "unresolved" rather than offering creation; refs into old pinned
  versions open read-write, and saving appends a new head rather than
  branching; cross-document paste doesn't externalize and re-import refs
  (`extractExternalized` and `internalizeRefs` are the primitives).
- **Old stores aren't migrated.** Stores from before the by-reference
  format degrade to holes; use **Reset** to reseed. Seeds' construct ids
  must match code's canonical encodings (`metaKindIds`, `kindRefs`), so
  changing a seed's layout needs a reset or a migration.
- **Schemas are descriptive only:** node kinds aren't checked, and the
  gap/typing machinery isn't folded into the document model.
- **No network transport or presence:** sync is tab-to-tab only.

## Open design questions

1. *Two-layer history* (DESIGN §7). The gesture split is settled and
   built. Open: the op-log compaction boundary, the synced meta-log for
   history mutation, branch merge-back semantics, and the presence
   channel's shape.
2. *Bootstrap.* Definitions are documents, but their semantics (lexicons,
   views, eval) still name Scala builtins, and the format is combinator
   data rather than something a domain expert would write. The `means`
   series is the planned answer to both.
3. *Immediately typed.* Real typechecking per keystroke over holey trees
   (Hazelnut-style edit semantics), beyond parse-level holes.
4. *Semantic diff/merge.* Node-id-stable documents enable meaning-aware
   diffs; compaction is the defined lineage boundary.
5. *Polysyntactic parity.* The text surface is per-language and
   reparses fully; richer projections (tables, diagrams as editors,
   per-user notation) remain open.
6. *Migration classes.* Per *Evolvable*, a language change should derive
   migrations for its artifacts, applied automatically by default. But
   mechanical changes (renames, reorders, added fields with defaults) are
   categorically safer than semantic ones, and the generator knows which
   it produced. How migrations are classified, and whether semantic ones
   auto-apply with notification or wait for opt-in review, is open.
7. *Language composition & interop.* The fragmentation answer
   (`ADOPTION.md`) needs mechanisms that don't exist yet. The intent is
   settled (DESIGN §4, *Towers*). Open: resolving input-surface
   ambiguity between imported languages (inline prompt or stored
   preference), a canonical base vocabulary of shared language
   fragments, and a shared value model so one DSL's artifacts mean
   something to another.
8. *Tree-native sync* (DESIGN §7). Syncing `Serial` text is
   scaffolding; line-level merges renumber duplicate ids and can
   retarget refs. The destination is a tree-native CRDT.
9. *`means` notation* (DESIGN §4): the plain-text placeholder
   convention, how parse-time and evaluation-time meanings are marked,
   and whether alternative notations live in one language document or
   in layered notation documents.

## Next steps

1. **`means` rules — the next major series.** Replace authoring of the
   combinator-based model (`GrammarDef`, a hand-written schema list,
   hand-written printers, builtin views) with `pattern means meaning`
   rules plus a tightest-first binding list, compiled onto the existing
   recovering combinators (DESIGN §4, *Defining languages by `means`
   rules*). The current model keeps working alongside until parity is
   proven; each step lands with tests. The rule model, scannerless
   combinators, the incremental engine, and id-preserving reconcile are
   done (*Working*).

   *Phase A — the rule engine (pure, `foundation/`):*

   1. **Finish incremental parsing** (DESIGN §4, *Incremental parsing*):
      - *Wiring:* the text cell is done (`TextSession`, below). Still
        whole reparses: projected cell commits, and synced remote text
        once sync carries language text rather than the outline.
      - *Memo in the document:* parse results live in document nodes,
        with only failed attempts in a side table.
      - *Rule identity by reference:* rules are identified by name until
        they live in `lang:` documents (step 6), then by node id, so a
        rename invalidates nothing.
   2. **Operator combinator + rule compiler.** Generalize
      `precedenceTable` (left-associative infix only) into a
      precedence-climbing operator combinator covering prefix, postfix,
      and right- and non-associative infix, driven by the binding list.
      Compile rules to combinator compositions: operator-shaped rules
      join the operator combinator; bracketed, keyword-led, and sequence
      rules compile to sequence/choice and serve as its atoms. Recovery
      stays in the combinators, so `Junk`/`Phrase`/stop sets never appear
      in an authored definition. `Signum`'s fused negative literals
      become a parse-time meaning (`-n means literal(-n) where n:
      number`). Also deferred from the scannerless work: glued pieces
      compile to lexical sequencing, a shared set of builtin lexical
      rules (number, whole number, name, text), and recovery driven by
      the grammar's literals rather than kind names.
   3. **Derived printer.** Invert constructor meanings and use the
      binding list for minimal parentheses. Property test:
      `parse(print(t)) == t` over generated trees and the parser battery.
   4. **Derived schema.** `NodeKind`s (name and arity) come from the
      rules' meanings; the hand-written schema list goes away.

   *Phase B — the expression language on rules:*

   5. **`exprRules` at parity.** Write the expression language as rules;
      `DefLanguage` accepts rule-based syntax next to `GrammarDef`. Done
      when the rule form is identical to `ExprLanguage` (trees, spans,
      printing, eval) on the 28-input battery and the full parser suite.

   *Phase C — rules as documents:*

   6. **Rule encoding.** `lang:` documents hold `means(pattern, meaning)`
      nodes, a binding list, and examples; the recovering decoder turns a
      malformed rule into a hole rule that never applies.
   7. **`means` text surface.** The `def` view and text cell for `lang:`
      documents read and write the `means` notation (the existing
      printer and `RuleGrammar` reader), so editing a language reads like
      the DESIGN.md examples rather than an outline of combinators. The
      view renders the printer's role spans: `means` emphasized,
      placeholders styled, and a detail-level toggle switching `means` to
      `=>` and hiding `where`.
   8. **Worked examples as live checks.** `examples:` entries are checked
      on every render; a failing example shows as a hole in the `def`
      view (*Learnable and self documenting*, *Reactive*).

   *Phase D — meaning layers; demote the old model:*

   9. **Semantics as rules.** `add(a, b) means builtin num-add`: eval is
      derived from meaning rules bottoming out in builtins, replacing the
      hand-coded `Expr.eval` behind `expr-eval`. The rule dependency DAG
      and derived layers are computed; a cycle renders as a hole at the
      closing edge.
   10. **Migrate stored definitions.** Users' IndexedDB stores hold
       `lang:` docs in the combinator format, and seeding never
       overwrites. Convert legacy grammar subtrees to rules on read (or
       first load): the first real *Evolvable* migration, revertible
       because every version is kept.
   11. **Demote `GrammarDef` to the implementation layer.** Definitions
       are authored and stored as rules; `GrammarDef` stays as the
       compilation target `interpret` runs, shown only under show
       implementation and still writable there as the escape hatch.
       Remove the combinators rules made redundant in authored
       definitions (`Junk`, `Phrase`, `Signum`, stop sets). Update
       DESIGN.md and the demo.

   *Phase E — beyond parity:*

   12. **Sheet on rules.** Positional refs (`ref:c,r`, then `C4`) as
       parse-time meanings resolving to stable `InternalNodeRef`s and
       printing back as current positions; ranges (`a .. b`).
   13. **`where` and adjustable verbosity.** `where` clauses with
       constraints inferred from the layer below; a terse/verbose toggle
       in the `def` view, separate from show/hide implementation;
       violated constraints surface as holes at every zoom level.
   14. **Cycle prevention in the editor.** Completion on a meaning offers
       only rules below it in the DAG.
   15. **Self-description.** The language-definition language's own
       syntax is written as `means` rules, closing the bootstrap loop.
2. **Pins, pending updates, auto-update** (DESIGN §4, *Pins select
   behavior*). Built: pin-faithful resolution, `pendingUpdates` /
   `acceptUpdates`, the toolbar Update button, the
   `setting(auto-update, true)` flag with in-pane auto-accept, the
   save-time cascade (synced docs skipped), broken imports surfaced in
   the toolbar (`broken: N`), and version bumps carried through
   `changedSince` as a kind-tag migration — only affected holes
   reparse. The demo beat: adding `/` to `lang: expression` pends
   `lang: sheet`; accepting publishes sheet v1, which pends the budget
   — the hole heals on the budget's accept.

   Still open:

   - *Remote heads.* The Update scan and the cascade run against this
     tab's store, which changes only on local saves and boot hydration
     — a save in another tab never enters it (sync streams the open
     document's text, not store versions), so a cross-tab dependency
     bump doesn't even *surface* as pending until reload. Two tabs can
     also publish divergent v1s of the same url — version ids are
     computed per tab. This is really the store-convergence half of
     item 3: once a remote head is observable, the cascade should run
     against it.
   - *Content migration.* Accepting bumps refs but never rewrites
     committed structure — a cell parsed under expression v0 keeps its
     v0 shape and meaning forever; only source-bearing holes re-lift
     eagerly. Deriving content migrations for dependents when a
     definition changes (the *Evolvable* question) is the deep half of
     this item: today "update" means pins only.
   - *Broken-import repair.* `broken: N` reports refs whose node is
     gone at head; there is no gesture to repoint one to a surviving
     node, swap the import, or convert it to a hole.
   - *Accept granularity.* Update bumps every pending ref at once —
     per-ref acceptance (bump the language, hold that import) isn't
     offered.
   - *Head fallback.* `pinnedOrHead` resolves at head when the pinned
     version isn't stored — unreachable while the store is
     append-only, but it becomes a silent semantic change if
     compaction ever prunes versions.
   - *Scan cost.* pending/broken detection walks all reachable refs on
     every render — fine at demo size, wants caching keyed on doc and
     store identity.
3. **History and sync.** A network transport (signaling) and presence
   (cursors, selections, eventually keystroke-level preview, as
   ephemeral broadcast state off the change graph); debounce-coalesced
   edit bursts; undo stacks rebuilt on resume; branch interception for
   text-cell edits; branch merge-back; durable history beyond a tab's
   lifetime (the op log already persists in `automerge_docs`) and for
   unsynced documents; curated checkpoints; the synced meta-log for
   pruning and compaction; then a tree-native CRDT.
4. **Schemas and typing.** Check documents against their node kinds and
   fold the gap/typing machinery into the document model.
5. **Further roadmap:**
   - A visual schema editor and interactive diagram editing as document
     views.
   - In-workbench help: `USAGE.md` is an interim measure; per *self
     documenting*, help belongs in the workbench as documents. F1 on a
     construct opens its help page (what it is, an example), linked by
     ref from its node kind, and from there on to the implementation
     (language def, grammar, builtin) through the go-to-definition
     machinery.
   - A fuller evaluation engine beyond the expression language's `eval`.
