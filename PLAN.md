# Plan

What doesn't yet work and
what comes next. What works is described per package in each folder's
`DESIGN.md`; the root `DESIGN.md` covers architecture and rationale;
`DEMO.md` is the demo being worked toward.

**Current Major Focus:** Reducing the examples so that they consist of only dialect documents expressed in terms of foundational constructs provided by the platform, and contain no custom Scala code

## Known gaps

- **Demo rough edges:** Addition of an expression like 'a // b' does not work, because tokens are hard-coded instead of being derived as part of the expression dialect. History does not show updates when the sheet document is updated to pull in the updates to the sheets & expression dialect.
- **Sync:** Remote text doesn't parse incrementally. Display of remote changes in local history is unclear. Undo stacks are session-local and lost on resume. Edits
  made before first sync contact resolve last-writer-wins rather than
  merging. Edit bursts aren't coalesced. Unsynced session history isn't
  persisted.
- **References:** References into old pinned
  versions open read-write, and saving appends a new head rather than
  branching. Cross-document paste doesn't externalize and re-import refs.
- **Old stores aren't migrated.** For now we make no attempt to migrate old stored documents. Anything intended to be persisted should be saved within the Scala code itself.
- **Vocabularies are descriptive only:** Node terms aren't checked, and the
  gap/typing machinery isn't folded into the document model.
- **No network transport or presence:** Sync is tab-to-tab only.

## Open design questions

1. *Two-layer history* (`collaboration/DESIGN.md`). By giving the history document its own history, we can have a more granular control over the history compaction and merging without data loss.
2. *Bootstrap.* The example dialects and definitions therein work, but pieces still point to hardcoded scala code. The dialect definition language itself should eventually become authored in the platform.
3. *Immediately typed.* Real typechecking per keystroke over gappy trees
   (Hazelnut-style edit semantics), beyond parse-level gaps.
4. *Semantic diff/merge.* Node-id-stable documents enable meaning-aware
   diffs.
5. *Polysyntactic parity.* Richer projections (tables, diagrams as editors,
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
   - *Remaining*
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
         typed* (open question 3) and the matching half of item 5.
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
2. **Surfaces as definitions — the examples shed their code.** The
   `means` series made expression syntax and printing data, but each
   example still hand-writes its document-level surface: markdown's
   `MarkdownParse` (the `blocks`/`runs`/`plain` productions,
   `flatRuns`, `withinLine`, `spaceLeaf`, `strayLeaf`, `inlineSkip`),
   `MarkdownPrint` (`markdownLines`), and `MarkdownMarks`
   (`markdownLineMarks`); expression's `Expr`/`ExprDoc` tree↔AST
   codec and `exprViewLeaf`/`exprPrintLeaf` leaf spellers. The target:
   an example is its dialect document's data (rules, lexicon,
   vocabulary), the `Impls` wiring, and its views/eval — nothing that
   reads or writes text by hand. In order:

   - *Authored productions.* `where` terms today bind builtin leaves
     or code-side `Impls.productions` (the seam added for markdown —
     a production is just a `LexicalTerm`). Give the def document a
     production form — repetition (`many`/`some`), sequencing,
     choice, a line-scope marker (`withinLine`'s job), strict vs
     recovering emphasis, a verbatim/stray fallthrough — compiled to
     `LexicalTerm`s, so `blocks`/`runs`/`plain` live in `lang:
     markdown`'s text, not `markdownImpls`. Whitespace semantics come
     with it: `inlineSkip`/`spaceLeaf` exist because `scanAt` skips
     whitespace globally — a production needs a `raw` token mode that
     keeps intra-line spaces as content. Meta construct, encode/
     decode, `lexicalTerms` reading from the decoded def, and
     `Impls.productions` reduced to a bootstrap fallback.
   - *Document-level `self`.* With `blocks` declared, `d means d
     where d: blocks` compiles the whole document — `DefLanguage`'s
     `textParser` shortcut retires and `textSyntax` returns, giving
     markdown the incremental sessions the expression language has.
   - *Production-aware derived printing.* `printWith` already walks
     rules backwards inside a line; extend it over the declared
     productions — `list`/`some`/`many` nodes emit children
     one-per-line, `runs` splices, block-per-line layout — and
     `markdownLines` retires.
   - *Derived edit-surface marks.* `markdownLineMarks` → a
     `marksWith` that classifies each line by the production that
     applied and each span by the rule that matched, mapping
     construct → class by convention (`.cm-<construct>`) or by
     settings on the def's view bindings. An unmatched marker paints
     nothing because no rule claimed it — the marks *are* the parse.
   - *A shared leaf speller.* `exprViewLeaf`/`exprPrintLeaf` collapse
     to a foundation default (ints, floats, `ref:N`, verbatim/`?`
     gaps, `NodeDataText.show` fallback); a language overrides only
     when it differs.
   - *Retire the `Expr` codec.* `evalNode` already evaluates document
     trees through `semanticsOf`; `exprToDetached`/`exprFromDetached`
     is a hand-written reader — tests build trees through
     `ExprLanguage.parse` or a vocabulary-derived builder and read
     values via `evalNode`/`valueText`; the sheet's `Expr.Gap` reads
     become `GapData`/`StringData` checks. `Expr.scala`/
     `ExprDoc.scala` delete, or `Expr` survives as pure test
     scaffolding.
   - *Lexicons, partly.* Marker literals (`##`, `**`, `#`, `*`) are
     spelled in the patterns — collectable longest-first; the token
     productions (`text`, `number`, `ref`, `plain`) stay authored,
     either in `Impls.leaves` until *Authored leaf productions*
     (item 1) lands or as `leaf` declarations beside the rules.
   - *Sheet's commit surface* (`commitLink`/`linkCellRefs`,
     positional `C4` → `InternalNodeRef`) stays an `Impls`
     interpretation — it rewrites committed structure, it isn't
     syntax — and moves under *Sheet on rules* (item 1) when that
     lands.

   End state: `examples/expr/` is `ExprRules` + `ExprLexicon` (until
   the lexicon step), `ExprDef`, `ExprLanguage`, `ExprView`;
   `examples/markdown/` is `MarkdownDoc` (the dialect data),
   `MarkdownDef`, `MarkdownLanguage`, `MarkdownView` — `MarkdownParse`
   /`MarkdownPrint`/`MarkdownMarks` retired to foundation generics.
3. **Pins, pending updates, change proposals, auto-update**
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
     (`auto-update`, `llm`; see item 4), and semantic diffs. Policy
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
     item 4: once a remote head is observable, the cascade should run
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
4. **History and sync.** A network transport (signaling) and presence
   (cursors, selections, eventually keystroke-level preview, as
   ephemeral broadcast state off the change graph); actor attribution
   on curated versions, with background changes (auto-update accepts,
   LLM edits) authored by non-user actors arriving through the merge
   path — and LLM edits possibly as change proposals (item 3)
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
5. **Structure and typing.** Folded into the generalized rules model
   (item 1, *Structural rules*): a structural pattern *is* a construct's shape
   — checking is matching — so typing rides the same tree-pattern
   engine rather than a separate registry.
6. **Further roadmap:**
   - A visual vocabulary editor and interactive diagram editing as document
     views.
   - In-workbench help: `USAGE.md` is an interim measure; per *self
     documenting*, help belongs in the workbench as documents. F1 on a
     construct opens its help page (what it is, an example), linked by
     ref from its node term, and from there on to the implementation
     (language def, grammar, builtin) through the go-to-definition
     machinery.
   - A fuller evaluation engine beyond the expression language's `eval`.
