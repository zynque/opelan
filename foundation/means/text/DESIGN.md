# `foundation/means/text/` — design

The text surface of the [`means` rule notation](../DESIGN.md): rule text
is itself read and printed through the same scannerless combinator
machinery every language uses (`foundation/grammar`), so the rule
notation is an ordinary grammar, ahead of full self-description.

- **`RuleLexicon`** — the lexicon of rule text (the token kinds of a
  rule line).
- **`RuleGrammar`** — the grammar of rule text, as scannerless
  combinators: `rule := pattern means? meaning? where?`, with the
  meaning alternatives `build` (`word(args)`), `use` (a lone
  placeholder), and `phrase` (a phrase in the defined language, taken
  raw). It always produces a tree — total parsing, gaps included.
- **`RuleRead`** — turns a `RuleGrammar` tree into a `Rule`. `=>` is
  accepted wherever `means` is — the terse spelling.
- **`RulePrint`** — rule text as role-tagged spans (`Role`: keyword,
  literal, placeholder, constructor tag, constraint term, marker, …), so
  a highlighting surface and plain text come from one source. Span roles
  are how a surface italicizes placeholders the way math italicizes
  variables; `Verbosity` selects the spelling level.
- **`RuleView`** — the spans' view projection: styled roles become
  `styled:role` elements (whole lines via `ruleView`, field displays via
  `patternView`/`meaningView`/`whereView`), so the surfaces that show
  rules highlight and their editable fields stay in lockstep with the
  printed text.
- **`RuleCheck`** — well-formedness issues as readable messages
  (`ruleIssues`, `ruleSetIssues`): unbound or duplicate placeholders,
  `where` terms unknown to the leaf vocabulary, binding-list mistakes.
  Reported, never thrown; a rule set with issues still compiles as far
  as it can (*Never broken*).

The provisional placeholder convention — a lone lowercase letter,
optionally numbered; quoting forces a literal — is discussed under
*Placeholders and `where`* in [`../DESIGN.md`](../DESIGN.md).
