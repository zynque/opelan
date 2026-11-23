# Plan

What doesn't yet work and what comes next.

**Current Focus:** Reducing the examples so that they consist of only dialect documents expressed in terms of foundational constructs provided by the platform and contain no custom Scala code

## Known gaps

- **Demo rough edges:** History does not show updates when the sheet document is updated to pull in the updates to the sheets & expression dialect.
- **Sync:** Remote text doesn't parse incrementally. Display of remote changes in local history is unclear. Undo stacks are session-local and lost on resume. Edits
  made before first sync contact resolve last-writer-wins rather than
  merging. Edit bursts aren't coalesced. Unsynced session history isn't
  persisted.
- **References:** References pinned to old
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
   fragments, and a shared value model so one dialect's artifacts mean
   something to another.
8. *Tree-native sync* (`collaboration/DESIGN.md`). Syncing `Serial` text is
   scaffolding; line-level merges renumber duplicate ids and can
   retarget refs. The destination is a tree-native CRDT.
9. *`means` notation* (`foundation/means/DESIGN.md`): the plain-text placeholder
   convention, how parse-time and evaluation-time meanings are marked,
   and whether alternative notations live in one language document or
   in layered notation documents.

## Next steps

1. **Dialects** `pattern means meaning`
    - **Structural Definitions** Stored definitions should start with AST structure and provide text, editor, etc. projections. Definitions like 'a * b means mul(a, b) can still be used to infer the structure *and* the text, but the structure should be the primary focus. Ambiguous parsers are allowed, but produce a deferred-choice gap (the projection
      renders candidates as a dropdown) rather than silently
      first-wins.
    - **Adjustable Verbosity in Definitions** (Terse/Verbose) toggle
      switching `means` to `=>` and hiding `where` behind a marker
    - **Patterns as Editors** The pattern language expands to express visual
      affordances — dropdowns, widgets, per-element view toggles,
      alternate presentations a document can switch between — so
      richer editors and variants are authored *in* the definition,
      not bound as named builtins. The pattern editor itself (a
      `lang:` doc's `edit` view) is the first mixed text/graphical
      instance of the same idea.
    - **Asserted Examples** A `lang:` doc's examples carry their
      expected interpretation, rendered and checked by the
      interpretation view — a dialect document becomes self-testing.
      `summary` is the placeholder until this lands.
    - **Types in Definitions** In today's definitions 'where a, b: number'
      only controls which *tokens* are accepted for those placeholders.
      In some cases, it should act as a type constraint, so those placeholders
      can be filled with any expression that matches the type. We still need
      a way to differentiate whether a placeholder is an expression of said type, or
      a leaf token of said type. Gaps need to be compatible with expected types.
    - **Cycle Prevention** The editor should only show definitions below the current one
      in the DAG, but manual document editing could still result in a cycle.
    - **Self-Description** The language-definition language's own
      syntax written a Dialect, closing the bootstrap loop. `builtin`
      input-parser names (`impls.inputParsers` — `text`, `sheet-cell`)
      are the last name-keyed doc binding standing here: they become
      anchored foundation terms or `Language`-ref inputs as part of
      closing the loop.
    - **Authored Leaf Productions** User-defined leaf kinds —
      dates, currency literals, unit suffixes — as document rules
      (`n means number(n) where n: digits`)
    - **Lexicon closed over at construction.** Today a parser and its
      lexicon travel as a `Grammar` pair — sessions and syntaxes can't
      mix them up — but every token boundary still resolves the lexicon
      out of the parse input. The end state is a parser that takes only
      a string and a memo table: `token`, `anyToken`, `unrecognized`,
      literal matching, `leafParser`, and the other scanning helpers
      receive the lexicon when the grammar is built, not on every call
      (`ParseInput` shrinks to `(text, memo)`). `ruleGrammar`,
      `meaningText`, `compileRules`, markdown's productions, and
      `DefLanguage.syntax` each bind their effective lexicon once at
      construction. Do this together with leaf productions: leaf
      declarations become data at the same time, so the
      combinator/lexicon boundary only churns once.
2. **Surfaces as definitions — the examples shed their code.** The
   `means` series made expression syntax and printing data — the
   expression example is now its dialect text alone — but markdown
   still hand-writes its document-level surface: `MarkdownParse`
   (the `blocks`/`runs`/`plain` productions,
   `flatRuns`, `withinLine`, `spaceLeaf`, `strayLeaf`, `inlineSkip`),
   `MarkdownPrint` (`markdownLines`), and `MarkdownMarks`
   (`markdownLineMarks`). The target:
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
   - *Production-aware derived printing.* `printWith` already walks
     rules backwards inside a line; extend it over the declared
     productions — `list`/`some`/`many` nodes emit children
     one-per-line, `runs` splices, block-per-line layout — and
     `markdownLines` retires.
   - *Derived edit-surface marks.* `markdownLineMarks` → a
     `marksWith` that classifies each line by the production that
     applied and each span by the rule that matched, mapping
     construct → class by convention (`.cm-<construct>`) or by
     settings the pattern language declares (*Patterns as Editors*,
     item 1). An unmatched marker paints
     nothing because no rule claimed it — the marks *are* the parse.
   - *Lexicons, partly.* Landed for literals:
     `LiteralLexicon.withLiterals` composes the patterns' literal
     displays over the authored lexicon — longest-first, reserved
     over the leaves, infix literals carrying gap-start — so a rule
     line is all a literal needs (`a // b` tokenizes as soon as its
     definition exists). Remaining: run-leaf stop sets are still
     hand-authored (`text` stopping at `*`, `unrecognized`'s
     exclusion — a glued `x&&b` still reads one token), and the
     impls-authored token productions (`text`, `plain`) stay in
     `Impls.leaves` until *Authored leaf productions*
     (item 1) lands or becomes `leaf` declarations beside the rules —
     `number`/`ref`/… already moved to the shared `standard`
     vocabulary. `SyntaxDef.lexicon`'s lookup — `lexiconFor`,
     impls-first then the standard lexicons — retires when lexicon
     content is authored this way too.
   - *Sheet's commit surface* (`commitLink`/`linkCellRefs`,
     positional `C4` → `InternalNodeRef`) stays an `Impls`
     interpretation — it rewrites committed structure, it isn't
     syntax — and moves under *Sheet on rules* (item 1) when that
     lands.

   End state: expression's half landed — `examples/expr/` is the
   dialect document alone. `examples/markdown/` is
   `MarkdownDoc` (the dialect data), `MarkdownDef`,
   `MarkdownLanguage`, `MarkdownView` — `MarkdownParse`/
   `MarkdownPrint`/`MarkdownMarks` retired to foundation generics —
   and then the same single-file end state.
3. **Pins, pending updates, change proposals, auto-update**
   (`foundation/document/store/DESIGN.md`, *Version pins select behavior*).
   The demo's healing moment (`DEMO.md`, step 7) needs the first three
   steps, in order:

   - *Structured gaps.* A gap keeps the parts that did parse as real
     children (`ref a`, then the unparsed `/` as text, then `ref b`),
     so references inside a gap are structural before it heals rather
     than `ref:N` text. The seed's `total / people` cell is the first
     user.
   - *Committed healing.* Accepting a pending update bumps the version pins
     and, in the same edit, reparses the gaps whose failed parse
     consulted a changed rule (`GapRetry`) under the newly pinned
     grammar version, committing each one that now reads with `commitContent`.
     Constructs the healed AST uses for the first time (`div`) get an
     imports entry through `internalizeRefs`, pinned at the same
     version as the language's other entries, so one document never
     mixes versions of a language. `healGaps` remains as the preview.
     An ambiguous heal (two imported rules read the gap) stores a
     deferred-choice gap rather than taking the first.
   - *Change proposals.* A pending update is presented as a proposal:
     the document as it would be after accepting (bumped version pins plus
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
6. **Text input as structural operations** — MPS/Hazel-style hybrid
   editing: keystrokes interpreted against the current tree emit node
   ops directly, so identities are retained by construction instead of
   reconstructed afterward by `commitContent`'s matching. The parser
   demotes from the commit path to oracle plus fallback, and
   `Reconcile`/`TextProvenance` become the ambiguous-input path. In
   order:

   - *Structural cursor.* Position as node path + slot + character
     offset within a leaf, maintained continuously —
     `TextProvenance`'s char→node-chain mapping made primary rather
     than a per-commit hint.
   - *Op algebra.* A small set of structural ops — edit-leaf-data,
     insert-sibling, split, wrap/unwrap selection, retokenize-leaf —
     each mapping onto existing document ops (`insertNode`,
     `updateNodeData`, `updateNodeChildIds`); untouched nodes keep ids
     trivially.
   - *Keystroke interpreter.* (key, cursor, tree) → op: typing inside
     a leaf edits its data directly; operator characters propose a
     restructure validated by the parser-as-oracle; ambiguous input
     reparses only the enclosing production and reconciles that
     subtree; uninterpretable input lands in a gap — free typing is a
     gap's text until `GapRetry` resolves it, preserving the
     text-editor feel.
   - *Grammar introspection.* A continuation oracle — which pieces may
     follow at a position inside a rule application — over the
     combinator objects (`Combinators`, `Operators`, `LexicalRules`),
     which are introspectable data rather than opaque functions.
   - *Parser demoted.* Whole-buffer `retext` becomes one input mode
     among others (paste, bulk edits); `ParseSession`'s memo remains
     for that path and for `changeRules` re-deriving stored text on
     language evolution.

   The end state intersects *Immediately typed* (open question 3):
   per-keystroke structure is the substrate Hazelnut-style edit
   semantics would build on.
7. **Further roadmap:**
   - A visual vocabulary editor and interactive diagram editing as document
     views.
   - In-workbench help: `USAGE.md` is an interim measure; per *self
     documenting*, help belongs in the workbench as documents. F1 on a
     construct opens its help page (what it is, an example), linked by
     ref from its node term, and from there on to the implementation
     (language def, grammar, builtin) through the go-to-definition
     machinery.
   - A fuller evaluation engine beyond the expression dialect's `eval`.
