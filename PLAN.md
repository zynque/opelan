# Plan

Point-in-time state of the Opelan workbench: what doesn't yet work and
what comes next. What works is described per package in each folder's
`DESIGN.md`; the root `DESIGN.md` covers architecture and rationale;
`DEMO.md` is the demo being worked toward.

Last updated: 2026-11-04

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
  - *Healing is derived:* a healed gap computes in both views, but the
    document still holds the gap text until the cell is re-committed
    (e.g. from the `formulas` view). The demo now narrates committed
    healing through a change proposal (next steps, item 2).
  - *Gap refs are text:* the seeded `total / people` gap stores its
    cell refs as names (`D5 / B6`) inside the gap string, so before it
    heals they are not real references: a row insertion retargets them
    silently.
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
  format degrade to gaps; use **Reset** to reseed. Seeds' construct ids
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
2. *Bootstrap.* Definitions are documents and their syntax is authored
   `means` rules, but their semantics (lexicons, views, eval) still name
   Scala builtins. The `means` series is the planned answer.
3. *Immediately typed.* Real typechecking per keystroke over gappy trees
   (Hazelnut-style edit semantics), beyond parse-level gaps.
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
   Heals are one candidate safe class: extension is additive and stored
   content is never reparsed, so a new rule only turns unknowns into
   content. Whether that makes heals safe to apply without review, while
   changed rules always arrive as change proposals, is part of the
   question.
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
   (`foundation/means/engine/DESIGN.md`, `foundation/grammar/DESIGN.md`),
   and the printer's role spans now reach the def view as `styled:` styling
   — `means` emphasized, placeholders italic — on read-only lines and
   inside the editable fields' displays
   (`foundation/means/text/DESIGN.md`, `foundation/view/DESIGN.md`).
   Meaning layers are done — eval is derived (`means/engine/Eval.scala`)
   on both the expression language and the sheet (`resolveGridRefs`
   projects `ref` constructs to `InternalNodeRef` leaves; `evalTree`,
   `LocExpr`, and `Num` are retired), a cycle renders as a gap at the
   closing edge, and def docs carry no vocabulary section — ambient
   terms (foundation primitives + placeholders) and imports resolved
   through the referenced def's own vocabulary cover it, so a decoded
   `add` is the foundational `add`. Sheet cells reference the
   rule-declared constructs (`*`, `+`) of lang: expression rather than
   foundation arithmetic directly; eval interprets them through the
   rules' meanings, and name resolution runs through the referenced
   document's vocabulary (ViewContext.termName) with the code-side
   term map as fallback.
   Remaining:

   - *Beyond parity:*
     - **Structural rules.** The general rule form of which textual
       `means` is the token-spine projection: a tree pattern — the
       canonical shape and editor (form: text boxes, dropdowns) for
       the construct it declares — matching subtrees instead of
       tokens. `lang: language`'s `metaTerms` and `sheet`'s
       `sheet`/`row`/`cell` become structural rules, and
       `vocabulary`/`declared` leaves `LanguageDef` entirely.
       Ambiguous surfaces store a deferred-choice gap (the projection
       renders candidates as a dropdown) rather than silently
       first-wins.
     - **Sheet on rules.** Positional refs (`C4` naming works at commit time via
      `commitLink`/`linkCellRefs`; parse-time meanings remain) as
       parse-time meanings resolving to stable `InternalNodeRef`s and
       printing back as current positions; ranges (`a .. b`).
     - **`where` and adjustable verbosity.** `where` clauses with
       constraints inferred from the layer below; a detail-level
       (terse/verbose) toggle switching `means` to `=>` and hiding
       `where` behind a marker, in the `def` view, separate from
       show/hide implementation; violated constraints surface as
       gaps at every zoom level.
     - **Value-level `where` — types, not tokens.** Today's `where`
       binds only leaf productions: `n: number` means an int/float
       *token* fills `n`. `a, b: int` meaning "the bound subtree has
       type int" is a different constraint, and needs:
       - *A type vocabulary.* `Constraint.term` is free text
         resolved only against `lexicals` (an unknown term is
         silently dropped); types want their own namespace — a
         small builtin set first, declared per-construct rather
         than per-token.
       - *Construct signatures.* The inference source: `add`/`mul`
         declare argument and result types — on the builtin
         registry *Semantics as rules* exposes — so
         `a + b means add(a, b)` derives `a, b: number` unwritten
         (`foundation/means/DESIGN.md`, *Placeholders and `where`*:
         implied constraints show only on zoom-in).
       - *`typeOf` over gappy trees.* Enforcement stays a
         post-binding check like `leafAccepts`, but on the bound
         subtree's inferred type — the result type of the construct
         that produced it, structural rather than evaluated. A gap
         operand's type is unknown: it satisfies provisionally, not
         violates (Never Broken). The thin end of *Immediately
         typed* (open question 3) and the matching half of item 4.
       - *Spelling.* Distinguish a leaf binding (`n: number`) from
         a type binding (`a: int`) in `where` — separate namespaces
         or a marker — before authored type constraints can coexist
         with token ones.
     - **Cycle prevention in the editor.** Completion on a meaning
       offers only rules below it in the DAG.
     - **Self-description.** The language-definition language's own
       syntax written as `means` rules, closing the bootstrap loop.
     - **Authored leaf productions.** User-defined leaf kinds —
       dates, currency literals, unit suffixes — as document rules
       (`n means number(n) where n: digits`), once `where` narrowing
       grows past builtin token kinds (the opaque-type question —
       `TokenKind`/`RuleName` value classes now mark the seam). When
       lexing moves into documents, a token kind ids by the leaf's
       node — rename-stable like `means:#N` for grammar rules — and
       `token`, `unrecognized`, `where` terms, and a lexicon's
       `gapStart`/`gapRun` refer by it. Deferred past the means
       series, like the lexicons they name.
2. **Pins, pending updates, change proposals, auto-update**
   (`foundation/document/store/DESIGN.md`, *Pins select behavior*).
   The demo's healing moment (`DEMO.md`, step 7) needs the first three
   steps, in order:

   - *Structured gaps.* A gap keeps the parts that did parse as real
     children (`ref a`, then the unparsed `/` as text, then `ref b`),
     so references inside a gap are structural before it heals rather
     than `ref:N` text. The seed's `total / people` cell is the first
     user.
   - *Committed healing.* Accepting a pending update bumps the pins
     and, in the same edit, reparses the gaps whose failed parse
     consulted a changed rule (`GapRetry`) under the newly pinned
     grammar, committing each one that now reads with `commitContent`.
     Constructs the healed AST uses for the first time (`div`) get an
     imports entry through `internalizeRefs`, pinned at the same
     version as the language's other entries, so one document never
     mixes versions of a language. `healGaps` remains as the preview.
     An ambiguous heal (two imported rules read the gap) stores a
     deferred-choice gap rather than taking the first.
   - *Change proposals.* A pending update is presented as a proposal:
     the document as it would be after accepting (bumped pins plus
     heals), shown with what would change, and recorded as a version
     only on accept. First stage: a live derivation recomputed as head
     moves, so a proposal never goes stale and needs no merge, with a
     simple before/after rendering of the changed nodes. Later stages:
     stored proposal branches in the `Store` (today it is linear per
     url), an author field on versions so a proposal names its actor
     (`auto-update`, `llm`; see item 3), and semantic diffs. Policy
     becomes per document: manual, propose, or auto-update (accept
     without review).

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
   - *Content migration.* Beyond heals, accepting never rewrites
     committed structure: a cell parsed under expression v0 keeps its
     v0 shape and meaning forever. Deriving content migrations for
     dependents when a definition *changes* rather than grows (the
     *Evolvable* question, open question 6) is the deep half of this
     item; such migrations would arrive as change proposals.
   - *Broken-import repair.* `broken: N` reports refs whose node is
     gone at head; there is no gesture to repoint one to a surviving
     node, swap the import, or convert it to a gap.
   - *Accept granularity.* Update bumps every pending ref at once —
     per-ref acceptance (bump the language, hold that import) isn't
     offered. With proposals, the question extends to accepting some
     heals and not others.
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
   path — and LLM edits possibly as change proposals (item 2)
   (`collaboration/DESIGN.md`, *Attribution*); debounce-coalesced edit bursts,
   including per-turn coalescing for streamed collaborator edits;
   undo stacks rebuilt on resume; branch interception for
   text-surface edits; branch merge-back; durable history beyond a tab's
   lifetime (the op log already persists in `automerge_docs`) and for
   unsynced documents; curated checkpoints; the synced meta-log for
   pruning and compaction; then a tree-native CRDT — which would also
   retire the whole-text reparse remote changes go through today
   (sync carries the `Serial` outline, parsed by the outline format;
   making that incremental is scaffolding the CRDT likely supersedes).
4. **Structure and typing.** Folded into the generalized rules model
   (item 1, *Structural rules*): a structural pattern *is* a construct's shape
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
