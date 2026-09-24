# Related Work

Systems, papers, and projects related to the goals of the Open Language Application
platform (see `MANIFESTO.md`). Organized by theme; each entry notes why it's relevant.

## Lineage

Our own prior prototypes:

- **[olw-p1](https://github.com/zynque/olw-p1)** — F# prototype. Explored phrase
  graphs, deltas, and display primitives.
- **[olw-p2](https://github.com/zynque/olw-p2)** — CoffeeScript prototype. Explored
  reactive repositories, interpreters, and document storage.
- **[olw-p3](https://github.com/zynque/olw-p3)** — Scala.js client/server prototype.
  Explored editable text segments, tree editors, and a shared
  `Document`/`NavigableDocument` data model.
- **[olw-p4](https://github.com/zynque/olw-p4)** — Elm prototype. The most
  sophisticated tree document model (persistent array-backed nodes, detached
  subtrees, per-node versions) plus an early LSCA-based version-tree merge. This is
  the model being adopted as the foundation.

## Versioned Data & Collaboration

The core tension: CRDTs give automatic convergence for live editing; git-style
models give curated, branched, truncatable history. We want both, at different layers.

- **[Upwelling](https://www.inkandswitch.com/upwelling/)** ([code](https://github.com/inkandswitch/upwelling-code)) — Ink & Switch prototype combining real-time CRDT collaboration with git-like explicit draft layers that are shared and merged deliberately. Closest existing model to our two-layer design (live sync + curated history).
- **[Patchwork](https://www.inkandswitch.com/patchwork/)** — Ink & Switch's later "version control for everything" work; explores retroactive branching and low-ceremony history annotation.
- **[Automerge](https://automerge.org)** — CRDT library. Change DAG with hash-chained commits, `fork`/`merge`/`diff`/`view`. Useful as the operational layer; its append-only op log and automatic merge semantics make it unsuitable as the curated-history layer.
- **[Dolt](https://github.com/dolthub/dolt)** — Git semantics (clone/branch/merge/diff) over SQL tables. Proof that "git for structured data" works.
- **[noms](https://github.com/attic-labs/noms)** — Archived predecessor of Dolt; versioned, content-addressed, mergeable database built on prolly trees.
- **[Irmin](https://irmin.org)** — OCaml library for mergeable, branchable persistent stores with git-like semantics on custom data types.
- **[TerminusDB](https://github.com/terminusdb/terminusdb)** — Document database with git-style revision history and collaboration.
- **[Jujutsu](https://github.com/jj-vcs/jj)** ([docs](https://jj-vcs.dev)) — Git-compatible VCS where the working copy is an automatically-saved commit, history rewriting is first-class, and the op log is inspectable/undoable. Interesting model for how "live state" relates to commits.
- **[Pijul](https://pijul.org)** / **[Darcs](https://darcs.net)** — Patch-based VCSs; merges are defined algebraically over patches rather than snapshots. Relevant theory for semantic merge.
- **[Git internals](https://git-scm.com/book/en/v2/Git-Internals-Plumbing-and-Porcelain)** — The object/refs/working-tree split our architecture mirrors (snapshot store / history DAG / live doc).
- **[IPFS](https://ipfs.tech)** — Content-addressed Merkle DAGs; the standard model for immutable referenced data, relevant to `ExternalNodeRef` and archived history layers.
- **[Datomic](https://www.datomic.com)** — Immutable database where "the database as a value" and first-class history are core. Queries-as-data philosophy is adjacent to documents-as-data.
- **[Peritext](https://www.inkandswitch.com/peritext/)** — Rich-text CRDT from Ink & Switch; what the operational layer needs to do for inline markup.
- **[Yjs](https://yjs.dev)** — The other major CRDT; worth knowing as the alternative if Automerge proves wrong.
- **[ShareDB](https://github.com/share/sharedb)** — Operational transformation backend; the classic pre-CRDT approach to real-time collaboration.
- **[Cambria](https://www.inkandswitch.com/cambria/)** — Ink & Switch lens system for translating data between schemas. Relevant to "polysyntactic" — bidirectional translation between representations.

## Language Workbenches & Structured Editors

- **[JetBrains MPS](https://www.jetbrains.com/mps/)** — The canonical language workbench: projectional editing, composable DSLs, no parsing. The elephant in this space; everything it does well and badly is instructive.
- **[Language Workbenches](https://martinfowler.com/articles/languageWorkbench.html)** — Martin Fowler's article/book on the category; defines the terminology.
- **[Intentional Programming](https://en.wikipedia.org/wiki/Intentional_programming)** — Charles Simonyi's original vision: code as a domain-model tree rendered through multiple notations. Direct ancestor of this project.
- **[Sandblocks](https://github.com/hpi-swa/sandblocks)** — Projectional block editor for Squeak/Smalltalk where visual elements are live, executable, and can have *multiple representations chosen per concept*. Very close to "polysyntactic + reactive." Also see their paper on deriving structured editors from arbitrary tree-sitter grammars ([DOI](https://dl.acm.org/doi/10.1145/3544548.3580785)).
- **[Spoofax](https://www.spoofax.dev)** — Academic language workbench; declarative language definition.
- **[Racket](https://racket-lang.org)** — `#lang`-based language-oriented programming; popularized the term.
- **[Fructure](https://github.com/disconcision/fructure)** — Structured editor experiment for Racket with interesting "implicit transformation" ideas.
- **[Blockly](https://developers.google.com/blockly)** / Scratch — Block-based editing; the mass-market end of projectional editing.
- **[Prograph](https://en.wikipedia.org/wiki/Prograph)** — Historical visual dataflow language.

## Live, Typed & Exploratory Programming

- **[Hazel](https://hazel.org)** — Live functional programming with *typed holes*: incomplete programs are first-class, editable, and still typecheck. Direct inspiration for our typed gaps; the hazelnut papers formalize the edit action calculus.
- **[Lamdu](https://github.com/lamdu/lamdu)** — Live functional programming environment: structural editing, immediate type feedback, live results. Probably the closest shipping thing to "immediately typed."
- **[Unison](https://www.unison-lang.org)** — Codebase stored as a content-addressed AST; syntax is just a rendering, names are metadata. Monosemantic + polysyntactic by construction, and content addressing makes renaming a non-event — highly relevant.
- **[Subtext](https://en.wikipedia.org/wiki/Subtext_(programming_language))** — Jonathan Edwards' research language: code as data with no syntax, edited structurally. Adjacent philosophy.
- **[Sketch-n-Sketch](https://github.com/ravichugh/sketch-n-sketch)** — Bidirectional editing: change the output, the program updates. Relevant to multiple representations editing the same semantics.
- **[Eve](https://github.com/witheve/Eve)** — Archived Chris Granger experiment: programs as sets of relational records in a document.
- **[Light Table](https://github.com/LightTable/LightTable)** — Instant-feedback IDE; popularized live evaluation inline.
- **[Glamorous Toolkit](https://gtoolkit.com)** — "Moldable development": every object gets custom views and tools; the environment is modified from within itself. Embodies frictionless + polysyntactic.
- **[Bret Victor's work](http://worrydream.com/LearnableProgramming/)** — Learnable Programming, Inventing on Principle: direct manipulation and immediate feedback as correctness tools.
- **[Elm](https://elm-lang.org)** — Besides being our prototype language: time-travel debugger and famously good error messages; proof that "immediately typed" UX is achievable.

## Documents as Data / Programmable Documents

- **[Roam Research](https://roamresearch.com)** / **[Logseq](https://logseq.com)** / **[Workflowy](https://workflowy.com)** — Outliners where a document is a tree of addressable blocks with cross-references. Our raw document editor is essentially this interaction model over ASTs; Roam's block refs prefigure `InternalNodeRef`/`ExternalNodeRef`.
- **[Jupyter](https://jupyter.org)** / **[Observable](https://observablehq.com)** / **[Pluto.jl](https://plutojl.org)** — Computational documents; reactive notebooks (Observable, Pluto) embody "results of a change made immediately visible."
- **[org-mode](https://orgmode.org)** — The everything-document: outlines, literate code, tables, agenda. Self-documenting applications' spiritual ancestor.
- **[Literate programming](https://en.wikipedia.org/wiki/Literate_programming)** — Knuth's WEB; code and documentation interleaved.

## Self-Modifying & Bootstrappable Systems

- **[Smalltalk](https://squeak.org)** ([Pharo](https://pharo.org)) — The reference for "frictionless": a live image where the IDE is written in itself and everything is inspectable/modifiable at runtime.
- **[Emacs](https://www.gnu.org/software/emacs/)** — Self-documenting, self-extensible editor; the other great existence proof that "the tool is modified from within itself" works.
- **[Oberon](http://www.projectoberon.com)** — Wirth's complete system (language, OS, UI) small enough to hold in your head; a model of what a self-hosting system can look like at minimal scale.
- **[VPRI STEPS](http://www.vpri.org)** — Alan Kay's group attempting to reinvent personal computing in ~20k lines via DSLs; language-oriented bootstrapping taken seriously.
- **[Mu](https://github.com/akkartik/mu)** — Kartik Agaram's stack designed to be understandable and bootstrappable end-to-end by a single person.
- **[Lisp](https://en.wikipedia.org/wiki/Homoiconicity)** — Homoiconicity: code as data. The oldest version of "documents with semantics."

## Historical Inspirations

- **[Engelbart's NLS/Augment](https://www.dougengelbart.org)** — The 1968 demo: structured documents, versioning, hyperlinks, real-time collaboration. Everything here, fifty years early.
- **[Project Xanadu](https://en.wikipedia.org/wiki/Project_Xanadu)** — Ted Nelson's hypertext: transclusion, versioned documents, first-class references between documents. `ExternalNodeRef` is a descendant of transclusion.
- **[HyperCard](https://en.wikipedia.org/wiki/HyperCard)** — Frictionless authoring for non-programmers; the accessibility bar to aim at.

## Papers & Theory

- **Fischer & Huson, "New common ancestor problems in trees and directed acyclic graphs"** ([DOI](https://doi.org/10.1016/j.ipl.2010.02.005)) — LSCA via LSA trees; the merge-base algorithm already used in olw-p4's `Version.elm`.
- **Kleppmann & Beresford, "A Conflict-Free Replicated JSON Datatype"** ([arXiv](https://arxiv.org/abs/1608.03960)) — The paper behind Automerge.
- **[Local-first software](https://www.inkandswitch.com/local-first/)** — Kleppmann et al.; the manifesto-adjacent essay defining the offline-first principles.
- **Omar et al., Hazelnut (POPL 2017)** — Bidirectionally typed edit actions; the formal basis for typed-hole editing (see hazel.org for the paper trail).
- **[Operational transformation](https://en.wikipedia.org/wiki/Operational_transformation)** — The pre-CRDT collaborative editing model; worth understanding for what CRDTs replaced.
