# `foundation/langdef/doc/` — design

The codec between a `LanguageDef`/`GrammarDef`/rule set and the `lang:`
document that stores it (see [`../DESIGN.md`](../DESIGN.md)).

- **`DefDocEncode` / `DefDocDecode` / `DefDocReader`** — encode a def as
  a typed `lang:` document and read it back recoveringly; sets and op
  tables are sorted so encoding is deterministic. Readers match
  constructs by node identity (`DefDocReader.cons`), never on a string.
- **`GrammarEncode`** — a `GrammarDef` as document nodes: each combinator
  has a `lang: language` construct, so a grammar section is authored
  declaration content.
- **`RuleEncode` / `RuleDocDecode`** — a rule set as document nodes:
  each `rule` holds `pattern` + `meaning` (+ `where`) sections; pattern
  pieces are `placeholder`/`literal` nodes with a `glued` marker when
  the source wrote them unspaced; the meaning is the term subtree.
- **`RuleLink`** — points each meaning's `use`/`source`/`builtin`/
  `literal` operand at the placeholder piece node it names, so a
  placeholder rename heals every use (the rules counterpart of
  `linkUses` for grammar nodes).
- **`DefDocText`** — the `lang:` document's text surface
  (`foundation/language/DocText.scala`): the content subtree as an
  outline in which meta constructs print by name and each `rule`
  subtree flattens to its `means` line. A string line under `rules`
  reads back as a `rule` — the surface reads/writes rules rather than
  leaf strings — while `binding`/`tier`/`example`/`rule` meta nodes
  keep their structured encoding, so hand-written structure still
  lands. Phrase meanings a language-aware commit would desugar stay
  quoted holes; `rephraseRuleSet` retries them at interpretation.
- **`Meta`** — `metaTermIds`: the node ids of `lang: language`'s own
  constructs, taken from encoding `docLanguageDef`, so code can know a
  meta construct by identity.

*Everything by reference* is the convention the codec implements —
constructs are `InternalNodeRef`s to import entries, never names:
[`../../document/typed/DESIGN.md`](../../document/typed/DESIGN.md).
