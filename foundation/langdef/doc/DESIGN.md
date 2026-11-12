# `foundation/langdef/doc/` — design

The `lang:` document format for a `LanguageDef`/rule set
(see [`../DESIGN.md`](../DESIGN.md)).

- **`DefDocEncode` / `DefDocDecode` / `DefDocReader`** — encode a def as
  a typed `lang:` document and read it back recoveringly; sets are
  sorted so encoding is deterministic. Readers match
  constructs by node identity (`DefDocReader.cons`), never on a string.
  The `vocabulary` section encodes only what the ambient layer can't
  name (a non-ambient import or a term shadowing a placeholder name);
  at decode, a non-ambient ref's name comes through `resolve` — the
  referenced def's own vocabulary — instead of a stored entry.
- **`RuleEncode` / `RuleDocDecode`** — a rule set as document nodes:
  each `rule` holds `pattern` + `meaning` (+ `where`) sections; pattern
  pieces are `placeholder`/`literal` nodes with a `glued` marker when
  the source wrote them unspaced; the meaning is the term subtree.
- **`RuleLink`** — points each meaning's `use`/`source`/`builtin`/
  `literal` operand at the placeholder piece node it names, so a
  placeholder rename heals every use.
- **`DefDocText`** — the `lang:` document's text surface
  (`foundation/language/DocText.scala`): the content subtree as an
  outline in which meta constructs print by name and each `rule`
  subtree flattens to its `means` line. A string line under `rules`
  reads back as a `rule` — the surface reads/writes rules rather than
  leaf strings — while `binding`/`tier`/`example`/`rule` meta nodes
  keep their structured encoding, so hand-written structure still
  lands. Phrase meanings a language-aware commit would desugar stay
  quoted gaps; `rephraseRuleSet` retries them at interpretation.
- **`DefDocMarks`** — `defDocLineMarks`: the mark pass behind the
  `edit` view's decorations — per line, a class saying what the line
  read as (meta construct, parsed rule, gap rule, payload kind,
  unmatched word) plus inline spans for `means`/`where` keywords and
  placeholder names. Read-only: it classifies, `DefDocText` commits.
- **`Meta`** — `metaRef`/`metaNameOf`/`metaConsOf`: meta constructs by
  placeholder ref — `lang: language`'s vocabulary is anchored in the
  placeholder registry (`../PlaceholderTerms.scala`), so code knows a
  meta construct by identity without a stored entry.

*Everything by reference* is the convention the format embodies —
constructs are `InternalNodeRef`s to import entries, never names:
[`../../document/typed/DESIGN.md`](../../document/typed/DESIGN.md).
