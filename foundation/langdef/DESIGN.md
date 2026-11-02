# `foundation/langdef/` — design

**Language definitions as documents** — the bootstrap path, and
*Frictionless* in practice. A `LanguageDef` (`LanguageDef.scala`) is a
language as data: identity, a vocabulary of node terms (derived from the
rules — each term's first meaning occurrence is its declaration), a
compositional grammar (`GrammarDef`, the data twin of the
[`foundation/grammar`](../grammar/DESIGN.md) combinators), view
bindings, and an input grammar. `langDefDoc` encodes a def as a typed
`lang:` document; `langDefFromDocument` reads it back recoveringly;
`interpret` (in [`syntax/`](syntax/DESIGN.md)) turns grammar data into a
total parser; and `DefLanguage` turns a def into a live `Language`. A
def doc's sections, rule nodes, and grammar combinators are constructs
of `lang: language` (`metaTerms`), imported like any other construct.
`lang: language` imports them from its own vocabulary, which is the
bootstrap fixpoint. Code knows meta constructs by node id
(`metaTermIds`, taken from encoding `docLanguageDef` — `Defs.scala`,
where `DocLanguage` bottoms out as the code-resident language `lang:`
documents are themselves typed by). It knows an example language's
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
[`doc/`](doc/DESIGN.md)) are the codec, with sets and op tables sorted
so encoding is deterministic. `DefLanguage(exprDef)` is held identical
to `ExprLanguage` (parse, print, and eval) on a 28-input battery, the
regression anchor for any change to the definition machinery (see
[`test/`](../../test/DESIGN.md)).

**Rules as documents.** A `lang:` document stores each rule as a
`rule(pattern, meaning, where)` node — pieces as `placeholder`/`literal`
nodes (`glued` marking unspaced adjacency), the meaning as its term
subtree (`doc/RuleEncode`, `doc/RuleDocDecode`, `doc/RuleLink`). Like
every construct use, a meaning names its constructor by reference:
`add(a, b)` is a node whose data refers to `add`'s declaration — the
first meaning node to build it — and `a` spells `use(a)` — an operand
ref to the pattern's placeholder node — so a rule survives renames on
either side. Rules compile to `GrammarDef`, which remains the
implementation layer the interpreter runs. The `means` notation is the
text surface of the language-definition language, eventually described
by its own rules (see [`../means/DESIGN.md`](../means/DESIGN.md)).

Other pieces:

- **`AutoUpdate.scala`** — the per-document `setting(auto-update, true)`
  flag that opts a document into recorded pin bumps; the mechanism is
  described under *Pins select behavior* in
  [`../document/store/DESIGN.md`](../document/store/DESIGN.md).
- **`InputGrammar.scala`** — a def's input grammar: how text committed
  in a projected input becomes content. A builtin parser runs as named;
  a grammar that refers to another language reads the text with that
  language's parser — through the host's remembered hole attempts when
  rendering, through its per-input sessions when committing — then
  classifies the result (see
  [`../language/DESIGN.md`](../language/DESIGN.md)).
- **`InputHoles.scala`** — `rememberInputHoles`: records a parse attempt
  for every hole in a document under the input grammar's current syntax,
  reusing attempts the last language change left valid and dropping
  attempts for texts no longer present.
- **`MeaningInput.scala`** — the meaning field of a rule in a `lang:`
  document as a language-aware input: phrases desugar at entry through
  the language being defined (see *Phrases desugar at entry* in
  [`../means/DESIGN.md`](../means/DESIGN.md)).

Sub-packages:

- [`doc/`](doc/DESIGN.md) — the def↔document codec
- [`syntax/`](syntax/DESIGN.md) — grammar interpretation, vocabulary
  derivation, grammar diffs
- [`view/`](view/DESIGN.md) — the def document's derived views
