# Status

Point-in-time state of the Opelan workbench: what works, what doesn't yet,
and what comes next. For architecture and rationale see `DESIGN.md`; for
the demo being worked toward see `DEMO.md`.

Last updated: 2026-10-24

The project is in Phase 1 (foundation & vocabulary system). **Next major
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
  term-tag migration, so kept parses re-pin rather than reparse.
- **Version DAG** (`foundation/version`, §3): LSCA merge base; drives
  editor history (branching undo, history column).
- **Expression and sheet languages** (`examples/`, §4): both are
  `DefLanguage` over `exprDef`/`sheetDef` — the means rules *are* the
  code-resident languages, with the registry objects kept only as the
  fallback for a store without the lang: doc (the hand-written
  `ExprParse` grammar stays as the parity oracle plus
  `parseCellText`'s reader). `print`/`eval` views, ref resolution
  with cycle detection; the sheet's `sheet`/`formulas` grids with
  projected cell editing, holes that heal when the cell grammar can
  read them, and a `currency` view setting; the seeded "Household
  budget", staged for the demo with `/` missing from
  `lang: expression`.
- **View trees** (`foundation/view`, `ui/view`, §4, §6): one recovering
  interpreter, provenance clicks that select the producing node, and
  `input` commits reconciled in place by `commitContent`.
- **Language definitions as documents** (`foundation/langdef`, §4):
  `lang:` docs typed by `lang: language`, resolved store-first, so a
  saved definition edit is live on the next render.
- **`means` rule groundwork** (`foundation/means`, `foundation/grammar`,
  §4): rule model, checker, reader, and role-tagged printer;
  scannerless combinators; the incremental packrat engine (`Memo`,
  `ParseSession`), driving the text surface through `TextSession`,
  projected input commits through per-input `InputSessions`, and
  stored-hole retries through `HoleRetry` — a hole reparses only when
  a language change touched a rule its last attempt consulted;
  id-preserving reconcile (`commitContent`) on both commit paths.
  The compiler is in: `operatorTable` covers prefix, postfix, and
  left/right/non-associative infix driven by the binding list, and
  `compileRules` turns a `RuleSet` into a parser — `exprRules`
  (`examples/ExprRules.scala`) matches the hand-written expression
  grammar on every input in the battery, spans included. Rules are
  now live in the definition path: `exprDef` carries `exprRules`
  over a combinator operand fallback of just signum and junk —
  the leaf tokens come from the vocabulary's leaf terms bound
  through `Impls.leaves` (`SyntaxDef.rules`), `DefLanguage`
  compiles them, `lang:` docs store rules as `means(pattern,
  meaning)` nodes — `placeholder`/`literal` pieces (`glued` markers
  preserve spacing), meaning terms as subtrees, constructs by
  internal ref to the def's vocabulary entries — and the def view
  projects each rule line as an `input` bound to the `rule` node,
  so a commit rewrites it as structured nodes (`commitMetaInput`),
  constructs bound to the doc's own terms. The decoder also reads
  the older text form, so docs written before the structured
  encoding and mid-edit states still decode. Language-change
  diffing names the top rule plus changed `means:` sequence names,
  so adding a rule heals holes through the same session carry.
  Sequence rules decoded from `lang:` docs memoize under their rule
  node id (`means:#N`), compared canonically (placeholders
  normalized by position) — a placeholder rename is a no-op for the
  session, while a meaning change or a re-created node still
  invalidates. Code-authored rules fall back to pattern-key names.
  Schema and checks derive too: `vocabularyFrom` turns construct
  meanings into the def's terms, and worked examples in the `def`
  view are checked live against the interpreted definition — a
  failing one shows as a hole chip saying what the input read as.
  The meaning model itself is the unified term of DESIGN §4 (*A
  meaning is a term, evaluated*): one `tag`/`arguments`/`reference`/
  `phrase`/`quoted` node, with `use`/`source`/`reparse`/`builtin`/
  `quote` as meaning-language builtins rather than separate cases —
  `applyMeaning` evaluates by tag, `readRule` spells a bare `a` as
  `use(a)` and a phrase as `reparse`, and an unknown builtin is a
  hole rather than a node. Phrases desugar at interpretation:
  `desugarRuleSet` parses a template through the whole rule set
  (placeholders as atoms) into `use`/construct/`quote` terms, so a
  bound operand's node lands in the output rather than a reparsed
  copy.
- **UI** (`ui/`, §5): fp runtime, `DocumentEditor`, `TypedDoc` with the
  app surface and its Raw / Definition (F12) exits, `Workbench` with
  seeded demo documents and ref following.
- **Live sync** (`collaboration/`, §7): Automerge between tabs over
  BroadcastChannel; one author-tagged history projected into the
  version DAG; per-actor undo; local navigation; checkout + edit forks a
  branch draft that peers can rejoin.
- **Build/test** (§9): `build.bat`/`build.sh`, `test.bat`/`test.sh`;
  409 munit tests over the pure core, UI update functions, and
  DocSession convergence under real Automerge in Node. A fresh clone
  needs a `webpack.config.js` (see `README.md`).

## Known gaps

- **Demo rough edges.** The full narration in `DEMO.md` was walked in a
  fresh browser on 2026-10-20 (Linux, `./build.sh` + `npx http-server`,
  starting from **Reset**) and every step works. What still reads
  awkwardly:
  - *Adding `/` takes outline surgery:* copy the `*` op node, paste it
    (it lands as a child; outdent it), then edit its symbol and retarget
    its term ref by node id (`&33`). The `means` series replaces this
    with one readable line.
  - *Healing is derived:* a healed hole computes in both views, but the
    document still holds the hole text until the cell is re-committed
    (e.g. from the `formulas` view).
  - *Update edges:* the cascade runs on local saves only and skips
    synced docs; the full list of pin edges — remote heads, content
    migration, broken-import repair — is item 2 in next steps.
  - *`currency` formats computed cells only;* price inputs stay plain
    numbers.
- **Synced remote text doesn't parse incrementally.** It is the
  `Serial` outline, parsed by the outline codec rather than a language
  grammar. Input sessions are pane-local and start fresh after a reload
  or for an input's first commit; builtin input parsers (a def's
  `inputParser` naming an impls function) parse whole.
- **Synced history:** remote changes apply as a whole-text reparse;
  text-surface edits made while browsing a version still write to the main
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
  must match code's canonical encodings (`metaTermIds`, `termRefs`), so
  changing a seed's layout needs a reset or a migration.
- **Vocabularies are descriptive only:** node terms aren't checked, and the
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

1. **`means` rules — the next major series.** `pattern means meaning`
   rules compiled onto the recovering combinators (DESIGN §4, *Defining
   languages by `means` rules*). Phases A–C are largely landed (*Working*
   above); what remains:

   *Phase A — rule engine loose ends:*
   - *Memo in the document:* parse results live in document nodes, with
     only failed attempts in a side table.
   - `Signum`'s fused negative literals as a parse-time meaning
     (`-n means literal(-n) where n: number`); glued pieces as lexical
     sequencing; builtin leaf productions authored in the document
     (they live in Impls like lexicons and views); example-driven
     associativity overrides.
   - *Derived printer:* a provenance-tagged variant emitting `src:`
     spans for click-through (printing over document nodes with ids,
     not detached trees), and the round-trip property
     `parse(print(t)) == t` over generated trees.
   - *Derived vocabulary:* collect `where`-bound leaf terms into
     vocabulary entries, and derive a def document's vocabulary section
     to retire the hand-written list there.
   - *Rule encoding:* the meaning field as its own per-keystroke input
     rather than the rule line's whole-text commit; then
     `reparse`/`phrase` leave the model.
   - *`means` surface polish:* the printer's role spans (`means`
     emphasized, placeholders styled), a detail-level toggle switching
     `means` to `=>` and hiding `where`, and the `lang:` text surface
     itself reading/writing rules rather than leaf strings.

   *Phase D — meaning layers; demote the old model:*
   - **Semantics as rules.** `add(a, b) means builtin num-add`: eval
     derived from meaning rules bottoming out in builtins, replacing
     the hand-coded `Expr.eval` behind `expr-eval`. The rule dependency
     DAG and derived layers are computed; a cycle renders as a hole at
     the closing edge.
   - **Migrate stored definitions.** Users' IndexedDB stores hold
     `lang:` docs in the combinator format, and seeding never
     overwrites. Convert legacy grammar subtrees to rules on read (or
     first load): the first real *Evolvable* migration, revertible
     because every version is kept.
   - **Demote `GrammarDef` to the implementation layer.** Definitions
     authored and stored as rules only; `GrammarDef` shown only under
     show implementation as the escape hatch; remove the combinators
     rules made redundant in authored definitions (`Junk`, `Phrase`,
     `Signum`, stop sets). Update DESIGN.md and the demo.

   *Phase E — beyond parity:*
   - **Sheet on rules.** Positional refs (`ref:c,r`, then `C4`) as
     parse-time meanings resolving to stable `InternalNodeRef`s and
     printing back as current positions; ranges (`a .. b`).
   - **`where` and adjustable verbosity.** `where` clauses with
     constraints inferred from the layer below; a terse/verbose toggle
     in the `def` view, separate from show/hide implementation;
     violated constraints surface as holes at every zoom level.
   - **Cycle prevention in the editor.** Completion on a meaning offers
     only rules below it in the DAG.
   - **Self-description.** The language-definition language's own
     syntax written as `means` rules, closing the bootstrap loop.
2. **Pins, pending updates, auto-update** (DESIGN §4, *Pins select
   behavior*). Still open:

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
   ephemeral broadcast state off the change graph); actor attribution
   on curated versions, with background changes (auto-update accepts,
   LLM edits) authored by non-user actors arriving through the merge
   path — and LLM edits possibly as *proposals* via the pending-update
   UX (DESIGN §7, *Attribution*); debounce-coalesced edit bursts,
   including per-turn coalescing for streamed collaborator edits;
   undo stacks rebuilt on resume; branch interception for
   text-surface edits; branch merge-back; durable history beyond a tab's
   lifetime (the op log already persists in `automerge_docs`) and for
   unsynced documents; curated checkpoints; the synced meta-log for
   pruning and compaction; then a tree-native CRDT — which would also
   retire the whole-text reparse remote changes go through today
   (sync carries the `Serial` outline, parsed by the outline codec;
   making that incremental is scaffolding the CRDT likely supersedes).
4. **Schemas and typing.** Check documents against their node terms and
   fold the gap/typing machinery into the document model.
5. **Further roadmap:**
   - A visual vocabulary editor and interactive diagram editing as document
     views.
   - In-workbench help: `USAGE.md` is an interim measure; per *self
     documenting*, help belongs in the workbench as documents. F1 on a
     construct opens its help page (what it is, an example), linked by
     ref from its node term, and from there on to the implementation
     (language def, grammar, builtin) through the go-to-definition
     machinery.
   - A fuller evaluation engine beyond the expression language's `eval`.
