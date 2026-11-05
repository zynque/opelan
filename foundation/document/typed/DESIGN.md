# `foundation/document/typed/` — design

**Typed documents** (`Typed.scala`): a document's *type* is an
`ExternalNodeRef` in the root node's *data* — the header, naming the
document that defines the language the rest of the tree is written in.
A simple document can be just that: a root ref with data children. Most
documents extend the standard core language, whose layout is positional:
first child the *imports* node — a direct `ExternalNodeRef` to the core
language's `imports` declaration (self-import would be circular), its
children the ext-ref entries — second the *title* node, carrying a ref
to the `title` declaration with the raw text as its child, third the
*content* subtree (the AST). Imports come first so they are in scope for
everything after them. Even the structural words are declared once in
`lang: language`'s vocabulary and used by reference, so a standard document
contains no repeated names at all. This is olw-p2's header,
dependencies, content layout with the header folded onto the root.
Untyped documents are "raw".

## Everything by reference

Content never spells a language construct by name. A node applying `mul`
(or `vocabulary`, `term`, `row`) has as its data an `InternalNodeRef` to
an import entry. That entry is an `ExternalNodeRef` to the construct's
declaration in its `lang:` document — the first meaning-position node
that builds it in the rules (`a × b means mul(a, b)` declares `mul`;
every later occurrence, and every document use, is a reference to that
node). A construct's identity is that address, and readers match on it
(`termOf`, `DefDocReader.cons`), never on a string. Renaming a construct
therefore edits one node, its declaration, and every use, in every
document, picks up the new name. The editor shows each use
projectionally: the resolved name in bold, with the raw `&n` beside it.
References *within* a definition are plain internal refs to the
declaring node, for example a meaning's second `mul` to the first, an
operator to the term it builds, or a `use` to a `named` grammar rule.
Def documents carry no `vocabulary` section once rules can declare the
terms — the section persists only for rules-less defs (`lang: language`
itself, the sheet), where it is authored declaration content, not a
redundant listing. Only a definition's own names, literal syntax
(`"*"`), builtin names, and user data remain strings. The type and
imports slots are positional; the imports node's `"imports"` label is a
caption, not a key.

## Imports (`Imports.scala`)

Every external node a typed document uses is imported once, as an
`ExternalNodeRef` leaf in its imports section, and used through
`InternalNodeRef`s to that entry. `internalizeRefs` moves inline
external refs, as builders, parsers, and clipboards write them, into the
imports on insert, and `extractExternalized` reverses that.
`externalRefOf` dereferences one hop.

## Reconcile — committing parse output (`Reconcile.scala`)

Document identity is handled a layer down from the parse: `commitContent`
commits a parse's detached output into a node, keeping the ids of every
subtree whose content didn't change — at any depth. Both commit paths —
projected inputs and whole-text edits — use it, and commit nothing but
content: the parse's bookkeeping dies with its session. Children that
match by content keep their node ids, so refs, selection, and history
survive a reparse; matching by content subsumes parse provenance (a
reused subtree is identical content anyway), so the same reconcile
covers session reparses, whole-input commits, and languages without
combinator input grammars. (The parse side of this design is in
[`../../grammar/DESIGN.md`](../../grammar/DESIGN.md).)

Whitespace and layout are formatting, not content. A committed node
records *what* was said; the derived printer decides where the spaces go
— continuous auto-format on every commit, in the streaming spirit,
rather than preserving whatever the surface happened to hold. If a
language ever needs genuinely meaningful layout (a verbatim block, a
comment, a chosen block format), the answer is an explicit construct in
its `NodeData` — content, not inferred trivia — the same way `GapData`
already carries a gap's raw text. This makes provenance a function of
content and format, computable on demand, instead of stored data that
can disagree with the text it describes.
