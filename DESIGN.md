# Opelan — Design

Opelan (**Op**en **L**anguage **A**pplicatio**n**) is a browser-based language
workbench: a tool for building software in which programs, documents, and the
tooling itself are all versioned, structured trees that can be viewed,
edited, and interpreted in multiple ways. It is written in
Scala.js, bundled with webpack, and runs local-first in the browser
(IndexedDB persistence, no server required).

This document covers the project's goals, lineage, and the design that
spans packages — navigation, language towers, and build/test. The design
of each package lives in a `DESIGN.md` beside the code, mapped below.
Companion docs: `MANIFESTO.md` (principles), `PRINCIPLES.md` (the reasoning
behind them), `ADOPTION.md` (why workbenches haven't spread, and how this
one means to), `RELATED_WORK.md` (survey of related systems), `DEMO.md`
(plan for a shareable demo), `README.md` (build/run), `USAGE.md` (how to
drive the workbench), `PLAN.md` (current state and next steps), and
`AGENTS.md` (code conventions).

## Package map

| Folder | Design |
|--------|--------|
| [`foundation/`](foundation/DESIGN.md) | the pure core: documents, versions, grammars, languages, views — and the *Never broken* gaps convention |
| [`foundation/document/`](foundation/document/DESIGN.md) | the persistent document tree, `NodeData`, `Outline`/`Serial` formats |
| [`foundation/document/typed/`](foundation/document/typed/DESIGN.md) | typed-document layout, imports, *everything by reference*, `commitContent` |
| [`foundation/document/store/`](foundation/document/store/DESIGN.md) | the versioned `Store`, names as projections, pins and pending updates |
| [`foundation/version/`](foundation/version/DESIGN.md) | the version DAG, LSCA merge base, the two-layer history model |
| [`foundation/grammar/`](foundation/grammar/DESIGN.md) | scannerless recovering combinators, lexicons, incremental packrat parsing |
| [`foundation/language/`](foundation/language/DESIGN.md) | the `Language` trait, text surface, `TextSession`/`InputSessions`/`GapRetry` |
| [`foundation/langdef/`](foundation/langdef/DESIGN.md) | language definitions as `lang:` documents; format in `doc/`, interpretation in `syntax/`, def views in `view/` |
| [`foundation/means/`](foundation/means/DESIGN.md) | the `means` rule notation; evaluation and rule→combinator compilation in `engine/`, rule text in `text/` |
| [`foundation/view/`](foundation/view/DESIGN.md) | the derived view-tree vocabulary, `SourceRef` provenance, verbosity |
| [`examples/`](examples/DESIGN.md) | the expression and sheet languages, the store-first resolver/registry |
| [`data/storage/`](data/storage/DESIGN.md) | IndexedDB persistence |
| [`collaboration/`](collaboration/DESIGN.md) | Automerge sync, sessions, unified history, per-actor undo, backup vs sharing |
| [`ui/`](ui/DESIGN.md) | the effect boundary: fp components over the pure core |
| [`ui/fp/`](ui/fp/DESIGN.md) | the component runtime |
| [`ui/editor/`](ui/editor/DESIGN.md) | `DocumentEditor`, the structural outliner |
| [`ui/typeddoc/`](ui/typeddoc/DESIGN.md) | `TypedDoc`, the multi-surface typed-document pane |
| [`ui/text/`](ui/text/DESIGN.md) | the CodeMirror 6 wrapper |
| [`ui/view/`](ui/view/DESIGN.md) | the view-tree → `View` interpreter |
| [`ui/fx/`](ui/fx/DESIGN.md) | effect seam components |
| [`ui/workbench/`](ui/workbench/DESIGN.md) | the application shell |
| [`test/`](test/DESIGN.md) | the suite layout and the oracle/property-test strategy |

## Lineage: the olw-p* prototypes

Four earlier prototypes `github.com/zynque/olw-p1` ... `olw-p4` each explored a piece of the design:

| Prototype | Tech | What it explored | What survived |
|-----------|------|------------------|---------------|
| **olw-p1** "FSOLP" | F# / WPF | *Phrase graph*: every phrase has a type (first phrase-child), parents, contents, and **interpretations/quotations keyed by Context** — the origin of "one node, many representations". First-class deltas/changesets with dirty-flag propagation for re-render and undo. | The typed-document convention (type = first child); deltas → today's persistent-document ops + undo stacks |
| **olw-p2** | CoffeeScript | Immutable document store: append-only node array, copy-on-write path updates yielding a new `rootId` per version; explicit version lists; a *live repository* façade over the immutable store; a document **header URL selects its interpreter** | Immutable-node-array document; versions as data; type-directed interpretation |
| **olw-p3** | Scala.js client/server | `Document[T]` / `NavigableDocument` shared model (persistent tree + parent index); editable text segments, tree editors, virtual-dom experiments | Document shape refined; lesson that a hand-grown ad-hoc UI layer becomes unmanageable — motivation for `ui/fp` |
| **olw-p4** | Elm | The most mature model: persistent array-backed `Document`, detached subtrees, per-node versions, and a **version tree represented as a document** with LSA-based merge-base computation (Fischer & Huson LSCA); manifesto encoded as data | Directly ported: `foundation/document` and `foundation/version` are the Scala.js ports of `Olw/Document/*` and `Olw/Version/Version.elm` |

Lessons carried forward:

- *Documents all the way down.* A repository, a directory tree, a version
  tree, a language definition — all are documents (olw-p3 `notes.txt`).
  The manifesto itself ships as a sample document (`EditorSample`).
- *Views are derived, not stored.* p1's per-context interpretations became
  `Language.views` — renderings computed from the canonical tree.

## Navigation

Navigation follows from two manifesto principles: *polysyntactic* (the same
gesture is interpreted per surface and language) and *language*
(keystrokes and clicks are a language the software interprets). The
design has two layers:

- *Mechanism* — uniform. Every jump reduces to moving a selection within a
  document or following a ref, and refs are first-class (`InternalNodeRef`,
  and `ExternalNodeRef` with `url@version#node` addressing resolved
  through the [`Store`](foundation/document/store/DESIGN.md)). A *nav
  target* is a `(document address, node, view, jump|peek)` tuple, so
  landing in a particular surface — or peeking inline — uses the same
  machinery, not a special case.
- *Relations* — per-language. In a typed document the cursor sits on a
  *name*, and each command asks a different semantic question: what
  defines it, what types it, what uses it, what exemplifies it. `Language`
  therefore grows a resolver capability — `resolve(relation, doc,
  position): List[NavTarget]` alongside `views`/`render`/`parse`. Raw
  documents are the degenerate case: the "resolver" is the ref literally
  stored in the node (today's Ctrl+Enter).

The relation vocabulary has two tiers. A *foundational* set is fixed by the
workbench — named, keybound, and guaranteed across languages (definition,
usages, type-of, examples). The language supplies the implementation, but
the platform owns the vocabulary, so applications inherit it (*Transitive*).
An *open* set is advertised per language, like `views` — "go to test",
"go to migration" — and listed by the command palette (*Discoverable*).
Both tiers resolve through the same interface and return ordinary nav
targets, so peek, jump history, and results-as-documents apply uniformly
to language-invented jumps.

**Spatial navigation** — depends on the active surface's syntax:

- *Forward/backward* (left/right) and *up/down* move through the
  projection: by token or line in text, by sibling or parent in the
  structural editor. Today arrow up/down traverse the outline linearly;
  collapse-to-parent and expand-to-child on left/right are the natural
  additions.
- *Structural jumps*: go to parent/child, next/previous sibling, and
  next/previous node of the same type or category (next definition, next
  gap). The raw-document editor is just another surface, so these
  primitives apply everywhere — raw editing is not a special mode.
- *Expand/shrink selection* — the selection itself walks the tree.

**Semantic navigation** — distinct commands over the resolver interface:

- *Go to definition* / *find usages*: the binding relation and its
  inverse. Definition is a resolver query (name under cursor → defining
  node); usages come from an inverse ref index over stored documents —
  meaning-aware, since refs are structured rather than found by text
  search.
- *Go to examples*: under *Learnable and self documenting*, examples live
  in documents linked by refs. Links may be stored (a definition document
  references its examples) or discovered (the inverse index filtered by
  "example-ness"). Multi-valued relations like usages and examples return
  a list of targets, presented as its own document whose entries are
  themselves nav targets.
- *Go to type*: two readings. The *document's* type is its header ref
  (mechanism-level follow-ref on the first child — today's F12); the type
  of the *named entity* under the cursor is a semantic `type-of` query.
  Revealing or hiding types is a projection concern (detail level, below).
- *Next/previous gap or error*: gappy documents (`GapData`) make "next
  problem" a first-class jump; `gapCount` already shows in the status
  bar.
- *Provenance navigation*: click a value in a derived view to jump to the
  node that produced it — upstream along the derivation. Implemented for
  view trees via `SourceRef` (see
  [`foundation/view`](foundation/view/DESIGN.md)). The click is a *silent
  select*: it rides the ordinary `SyncDocument` push (a same-document
  push carrying a selection records no version), and `SelectTarget`'s
  focus flag lets the outline highlight the node without taking DOM
  focus from the cell's edit box.

**Cross-surface navigation** — the selection follows the user across views:

- *Go to/from rendering* re-projects the same node into a different view.
  It is a nav target carrying a `view`, so it belongs to the mechanism
  layer rather than being a semantic query. Position follows via stable
  node identity plus the parser's span map: a node selected in the
  structural editor lands on the corresponding text range, not the top of
  the input — and vice versa.
- *Peek*: `ExternalNodeRef` is already transclusion-style; peek-definition
  renders the target inline instead of navigating away.

**Zoom navigation** — three orthogonal axes, all per-user projections of
the same document, never edits:

- *Detail level* (terse/verbose — the *Adjustable verbosity* principle):
  zooming out elides — types and constraints hidden, signatures only;
  zooming in reveals. It stays at the current layer of meaning: more of
  *what* the content says, never *how* it is carried out. Fold/collapse is
  the degenerate local case. (`Verbosity` lives in
  [`foundation/view`](foundation/view/DESIGN.md).)
- *Implementation* (show/hide implementation): descend from a construct to
  what it means in the layer below — a rule to its meaning, a meaning to
  its builtin (see
  [`foundation/means`](foundation/means/DESIGN.md)), an app to its
  language document (today's Definition/F12 exit). Orthogonal to detail
  level: an implementation can itself be shown tersely or verbosely.
- *Scope* (hoist/focus): a subtree becomes the temporary root of the
  view — the outliner's zoom. Orthogonal to both.

**History** — navigation state is data, and eventually a document:

- *Jump history*: back/forward across jumps (like browser back or an
  IDE's navigate-back). "Return from example/rendering" is a special case;
  a general stack makes every jump non-destructive.
- *Version navigation*: the version DAG is a document (see
  [`foundation/version`](foundation/version/DESIGN.md)), so
  previous/next version, go to branch point or merge, and "next change in
  a diff" can reuse the same traversal machinery. Today: click-to-jump in
  the history column.

**Addressing and search**

- *Go to symbol / quick open / command palette*: fuzzy matching over
  names, document URLs, and node ids. Every node is already addressable as
  `url@v#n`, so a jump target is literally a parsed ref (*Discoverable*).
- *Find text*, *bookmarks*, *breadcrumbs* (a clickable root→selection
  path): the mundane floor.

**Collaborative navigation** — once presence exists (see
[`collaboration/`](collaboration/DESIGN.md)), "follow a collaborator's
cursor" and shared selections for pairing fall out of presence being
broadcast state.

## Towers: languages composed into layers (design intent)

The platform's long-term shape is a *tower* (see `GLOSSARY.md`): the
workbench's base languages at the bottom, then increasingly
domain-specific languages — a game language, a card-game language, a
language for one game's items, behaviors, and levels — with the artifact
itself (the game, the budget) as the topmost document. Each *layer* is a
`lang:` document written only in constructs the layers below provide.

**Extension is strictly additive.** A layer adds rules whose meanings
name constructs imported from below; it never redefines, overrides, or
shadows an inherited rule. Narrowing is by non-import: a layer's usable
vocabulary is exactly the constructs it imports plus what it declares.
"This lower rule needs changing" is a signal the layer below wasn't
general enough — the fix is to generalize it there and publish a new
version; dependents' `url@version` pins hold them on the old one until
they accept the pending update (or auto-update) — see
[`foundation/document/store`](foundation/document/store/DESIGN.md) — so
adopting it is opt-in by construction.

**Every layer is fixable or forkable.** Inspecting a layer is the
ordinary follow-the-type-ref mechanism (F12), and editing a layer one
doesn't control produces a new branch or version of its document —
visible, versioned divergence that a later merge could reconcile —
rather than an invisible override. The cost of divergence grows with
depth: heavy local modification of the lowest layers cuts a user off
from community support — a social-contract problem the platform can
surface but not solve (open).

**Ambiguity lives only at the input surface.** Storage is never
ambiguous: every construct use is a ref to a fully specified
declaration. Two imported languages may define overlapping surface
syntax (two `a + b` rules); rather than silently taking the first, the
document can store a deferred-choice node — a gap holding the
candidates that the projection renders as a dropdown — so the user can
type on and disambiguate whenever they choose. Once chosen, the node
will hold a reference id, so there is no structural ambiguity, though
notation may still be ambiguous.

## Build and test

- Scala 3.9 + Scala.js via Scala CLI (`project.scala`) and scalajs-dom; ES
  module output → `app.js` → webpack → `bundle.js`, loaded by
  `index.html`.
- `build.bat` / `build.sh` run the full pipeline (`--force` packaging is
  handled by deleting `app.js` first); `test.bat` / `test.sh` run the
  suite. The test scripts point `TEMP`/`TMPDIR` at `./.tmp` so Node can
  resolve `node_modules` from the linked test bundle.
- Tests: munit suites across `test/**` — the pure core is the tested
  core (layout and strategy: [`test/`](test/DESIGN.md)).
- npm dependencies: CodeMirror 6 packages and `@automerge/automerge`.

For what currently works, what's in progress, open questions, and next
steps, see `PLAN.md`.

## Future Directions

- Compilation to WASM
- Integration with AI coding assistants
- Compilation to MLIR
