# `foundation/langdef/` — design

**Language definitions as documents** — the bootstrap path, and
*Frictionless* in practice. A `LanguageDef` (`LanguageDef.scala`) is a
language as data: identity, a vocabulary of node terms (derived from the
rules — each term's first meaning occurrence is its declaration), a
`means` rule set, view bindings, and an input grammar. `langDefDoc`
encodes a def as a typed `lang:` document; `langDefFromDocument` reads
it back recoveringly; `compileRules` (in
[`../means/engine/`](../means/engine/DESIGN.md)) turns the rules into a
total parser over the lexicon's recovery; and `DefLanguage` turns a def
into a live `Language`. A
def doc's sections and rule nodes are constructs
of `lang: language` (`metaTerms`) — *placeholders*: their vocabulary
lives in the placeholder registry (`PlaceholderTerms.scala`), anchored
in the seeded `opelan:foundation/placeholders` document until the def
language can declare it in-document. That registry is the second
category beside the truly foundational primitives: placeholders share
the foundation's ambient implementation (name↔ref with no stored
entry) but are marked as terms intended to move up out of the
foundation layer once it is rich enough to define them — the sheet's
grid terms (`ref`, `column`, `row`, …) are the other group.
Code knows meta constructs by placeholder ref (`metaRef`/`metaNameOf`,
`doc/Meta.scala`; `DocLanguage` bottoms out as the code-resident
language `lang:` documents are themselves typed by — `Defs.scala`). It knows an example language's
constructs by the ids its canonical encoding assigns (`termRefs`,
`exprTerms`, `sheetTerms` — see [`examples/`](../../examples/DESIGN.md)).
Each decoded `NodeTerm` carries its `ref`, and a def-interpreted parser
emits that ref for each node it builds. Semantics the def names by
string — lexicons, view renderers, builtin input parsers — resolve
through an `Impls` bundle (`Builtins.scala`, also `ViewContext`: the
view's settings plus the language's current input grammar): honest
scaffolding while behavior still lives in code. The input grammar
(`InputParserDef.Language(ref, fallback)`, `InputGrammar.scala`) names
another language by ref, which is how the sheet's cells parse with
`lang: expression`. `DefDocEncode`/`DefDocDecode`/`DefDocReader` (in
[`doc/`](doc/DESIGN.md)) implement the def↔document format, with sets
sorted so encoding is deterministic. A constructed `DefLanguage(exprDef)`
is held identical to the `ExprLanguage` singleton (parse, print, and
eval) on a 28-input battery, checking the construction path of the
definition machinery (see
[`test/`](../../test/DESIGN.md)).

**Rules as documents.** A `lang:` document stores each rule as a
`rule(pattern, meaning, where)` node — pieces as `placeholder`/`literal`
nodes (`glued` marking unspaced adjacency), the meaning as its term
subtree (`doc/RuleEncode`, `doc/RuleDocDecode`, `doc/RuleLink`). Like
every construct use, a meaning names its constructor by reference:
`add(a, b)` is a node whose data refers to `add`'s term entry — a
declared term's first meaning occurrence, or the ambient ref it
names: foundation primitives and placeholders are ambient
(`ambientRefs`/`ambientTermOf`, built on `foundationalOf` +
`placeholderOf`), and a ref into another definition's document names
itself through that definition's own vocabulary, resolved through the
`resolve` parameter (`langDefFromDocument`, and `termImports` for
name→ref at commit). Ambient terms and resolved imports between them
cover every case the `vocabulary` section ever served, so the format
has no vocabulary section at all. `a`
spells `use(a)` — an operand
ref to the pattern's placeholder node — so a rule survives renames on
either side. Rules compile to the recovering combinators of
[`foundation/grammar`](../grammar/DESIGN.md) — the implementation layer
— through `compileRules`. The `means` notation is the
text surface of the language-definition language — `rule` nodes print
as `means` lines and `means` lines under `rules` commit as `rule`
nodes (`doc/DefDocText`) — eventually described
by its own rules (see [`../means/DESIGN.md`](../means/DESIGN.md)).

Other pieces:

- **`AutoUpdate.scala`** — the per-document `setting(auto-update, true)`
  flag that opts a document into recorded pin bumps; the mechanism is
  described under *Pins select behavior* in
  [`../document/store/DESIGN.md`](../document/store/DESIGN.md).
- **`InputGrammar.scala`** — a def's input grammar: how text committed
  in a projected input becomes content. A builtin parser runs as named;
  a grammar that refers to another language reads the text with that
  language's parser — through the host's remembered gap attempts when
  rendering, through its per-input sessions when committing — then
  classifies the result (see
  [`../language/DESIGN.md`](../language/DESIGN.md)).
- **`InputGaps.scala`** — `rememberInputGaps`: records a parse attempt
  for every gap in a document under the input grammar's current syntax,
  reusing attempts the last language change left valid and dropping
  attempts for texts no longer present.
- **`MeaningInput.scala`** — the meaning field of a rule in a `lang:`
  document as a language-aware input: phrases desugar at entry through
  the language being defined (see *Phrases desugar at entry* in
  [`../means/DESIGN.md`](../means/DESIGN.md)).

Sub-packages:

- [`doc/`](doc/DESIGN.md) — the def↔document format
- [`syntax/`](syntax/DESIGN.md) — vocabulary derivation, language-change
  diffs, the leaf vocabulary
- [`view/`](view/DESIGN.md) — the def document's derived views
