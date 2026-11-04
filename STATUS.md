# Status

Point-in-time state of the Opelan workbench: what doesn't yet work and
what comes next. What works is described per package in each folder's
`DESIGN.md`; the root `DESIGN.md` covers architecture and rationale;
`DEMO.md` is the demo being worked toward.

Last updated: 2026-10-29

The project is in Phase 1 (foundation & vocabulary system). **Next major
series:** replacing combinator-based language definitions with
`pattern means meaning` rules compiled onto the same combinators (*Next
steps*, item 1).

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
  `Serial` outline, parsed by the outline format rather than a language
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

1. *Two-layer history* (`collaboration/DESIGN.md`). The gesture split is settled and
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
   settled (`DESIGN.md`, *Towers*). Open: resolving input-surface
   ambiguity between imported languages (inline prompt or stored
   preference), a canonical base vocabulary of shared language
   fragments, and a shared value model so one DSL's artifacts mean
   something to another.
8. *Tree-native sync* (`collaboration/DESIGN.md`). Syncing `Serial` text is
   scaffolding; line-level merges renumber duplicate ids and can
   retarget refs. The destination is a tree-native CRDT.
9. *`means` notation* (`foundation/means/DESIGN.md`): the plain-text placeholder
   convention, how parse-time and evaluation-time meanings are marked,
   and whether alternative notations live in one language document or
   in layered notation documents.

## Next steps

1. **`means` rules — the next major series.** `pattern means meaning`
   rules compiled onto the recovering combinators
   (`foundation/means/DESIGN.md`). The engine groundwork — including the
   derived printer and derived vocabulary — has landed
   (`foundation/means/engine/DESIGN.md`, `foundation/grammar/DESIGN.md`).
   Remaining steps, in order:

   - *Rule engine loose ends:*
     - **`means` surface polish.** The printer's role spans (`means`
       emphasized, placeholders styled) reaching the surfaces that
       show rules.
   - *Meaning layers; demote the old model:*
     - **Semantics as rules.** `add(a, b) means builtin num-add`: eval
       derived from meaning rules bottoming out in builtins, replacing
       the hand-coded `Expr.eval` behind `expr-eval`. The rule
       dependency DAG and derived layers are computed; a cycle renders
       as a hole at the closing edge.
     - **Migrate stored definitions.** Users' IndexedDB stores hold
       `lang:` docs in the combinator format, and seeding never
       overwrites. Convert legacy grammar subtrees to rules on read
       (or first load): the first real *Evolvable* migration,
       revertible because every version is kept.
     - **Demote `GrammarDef` to the implementation layer.** Definitions
       authored and stored as rules only; `GrammarDef` shown only
       under show implementation as the escape hatch; remove the
       combinators rules made redundant in authored definitions
       (`Unrecognized`, `Phrase`, `Signum`, stop sets). Update
       DESIGN.md and the demo.
   - *Beyond parity:*
     - **Structural rules.** The general rule form of which textual
       `means` is the token-spine projection: a tree pattern — the
       canonical schema and editor (form: text boxes, dropdowns) for
       the construct it declares — matching subtrees instead of
       tokens. `lang: language`'s `metaTerms` and `sheet`'s
       `sheet`/`row`/`cell` become schema rules, and
       `vocabulary`/`declared` leaves `LanguageDef` entirely.
       Ambiguous surfaces store a deferred-choice hole (the projection
       renders candidates as a dropdown) rather than silently
       first-wins.
     - **Sheet on rules.** Positional refs (`ref:c,r`, then `C4`) as
       parse-time meanings resolving to stable `InternalNodeRef`s and
       printing back as current positions; ranges (`a .. b`).
     - **`where` and adjustable verbosity.** `where` clauses with
       constraints inferred from the layer below; a detail-level
       (terse/verbose) toggle switching `means` to `=>` and hiding
       `where` behind a marker, in the `def` view, separate from
       show/hide implementation; violated constraints surface as
       holes at every zoom level.
     - **Cycle prevention in the editor.** Completion on a meaning
       offers only rules below it in the DAG.
     - **Self-description.** The language-definition language's own
       syntax written as `means` rules, closing the bootstrap loop.
     - **Authored leaf productions.** User-defined leaf kinds —
       dates, currency literals, unit suffixes — as document rules
       (`n means number(n) where n: digits`), once `where` narrowing
       grows past builtin token kinds (the opaque-type question).
       Deferred past the means series, like the lexicons they name.
2. **Pins, pending updates, auto-update** (`foundation/document/store/DESIGN.md`,
   *Pins select behavior*). Still open:

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
   UX (`collaboration/DESIGN.md`, *Attribution*); debounce-coalesced edit bursts,
   including per-turn coalescing for streamed collaborator edits;
   undo stacks rebuilt on resume; branch interception for
   text-surface edits; branch merge-back; durable history beyond a tab's
   lifetime (the op log already persists in `automerge_docs`) and for
   unsynced documents; curated checkpoints; the synced meta-log for
   pruning and compaction; then a tree-native CRDT — which would also
   retire the whole-text reparse remote changes go through today
   (sync carries the `Serial` outline, parsed by the outline format;
   making that incremental is scaffolding the CRDT likely supersedes).
4. **Schemas and typing.** Folded into the generalized rules model
   (item 1, *Structural rules*): a structural pattern *is* a construct's schema
   — checking is matching — so typing rides the same tree-pattern
   engine rather than a separate registry.
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
