# `foundation/grammar/` — design

The parsing substrate: total, recovering, scannerless combinators.
[`means` rules](../means/DESIGN.md) are a notation *over* these
combinators — compiled to combinator compositions, never a separate
parser (see [`../means/engine/DESIGN.md`](../means/engine/DESIGN.md)).
An operator table is one meta-combinator (`precedenceTable`, in
`Precedence`/`Operators`), not a format feature.

## Scannerless: no separate lexers

The combinators parse characters directly, as OMeta, Ohm, and Parsec do;
there is no token stream and no lexing pass. A *lexicon*
(`Lexicon.scala`, `LexicalRules.scala`) is an ordered set of named,
character-level lexical rules, tried only where a combinator asks what
starts at its position — the first rule that matches wins, so order is
priority (`float` before `int`). A grammar's token kinds (`"int"`,
`"rpar"`) are the names of these rules, so the `GrammarDef` format and
stored `lang:` documents are unchanged; a syntax names its lexicon
(`"expr"`) where it once named a lexer. Token-level combinators skip
whitespace before what they match; lexical rules never do. The `means`
reader is itself a combinator grammar (`RuleGrammar` in
[`../means/text/`](../means/text/DESIGN.md)) over a rule lexicon. Why
this direction:

- *Lexing is the lowest layer of rules.* Number, name, and text become
  builtin character-level rules (`n means number(n) where n: digits`)
  instead of a tokenizer outside the language definition.
- *Composition.* A separate tokenizer imposes one global token set, so
  an input cannot embed a language with different lexical rules.
  Character parsers compose freely (PLAN, *Language composition*), and
  context-dependent lexing (`ref:3,4`, units like `5kg`, string
  interpolation) is natural.
- *`glued` gains its meaning* (with the rule compiler). Following Ohm's
  split between syntactic rules (whitespace skipped between parts) and
  lexical rules (no whitespace allowed), a glued piece means "no
  whitespace here": `ref:c,r` matches `ref:3,4` but not `ref : 3 , 4`.
  Unglued pieces skip whitespace implicitly, so rules never mention it.
  Landed between *lexical* pieces — a literal, or a placeholder bound
  to a leaf production — so `-n` fuses sign and number (`- 4` is a
  gap) while `( 1 )` still parses through the non-lexical `a`.
- *The rule notation is an ordinary grammar*, ahead of full
  self-description.

Recovery keeps its token-level vocabulary (`Recovery.scala`):
`unrecognized` gathers a run of lexicon matches of given kinds into a
gap, and `phrase` stops at given kinds, so behavior is identical to the
token-stream version. A character no lexical rule accepts still forms a
one-character token, so no text is lost. Once grammars are compiled from
rules, the same recovery can be phrased in terms of the literals a
grammar knows instead of kind names. Every result reports how far it
examined (its lookahead), the bookkeeping incremental parsing needs.

## Incremental parsing (*Incremental*)

The parser follows *Incremental Packrat Parsing* (Dubroy & Warth, SLE
2017 — the algorithm behind Ohm): every named rule application is
memoized by `(rule, position)`, and each result records how far it
consumed, how far it *examined* (lookahead can reach past what it
consumed), and which rules it called. That one mechanism answers three
different needs:

- *Text edits.* Drop the results whose examined range overlaps the edit;
  everything else is reused. Cost follows the size of the edit, not of
  the text.
- *Identity.* A commit reconciles parsed content against the document
  subtree it replaces (`commitContent` — see
  [`../document/typed/DESIGN.md`](../document/typed/DESIGN.md)): children
  that match by content keep their node ids, at any depth — refs,
  selection, and history survive a reparse. Matching by content subsumes
  parse provenance (a reused subtree is identical content anyway), so
  the same reconcile covers session reparses, whole-input commits, and
  languages without combinator input grammars.
- *Language changes.* Drop only the results that called a changed rule.
  Rules have stable identities (their node ids in the `lang:` document,
  per
  [*Everything by reference*](../document/typed/DESIGN.md)),
  so the memo key survives renames and only real changes invalidate.
  Rules decoded from a `lang:` document key by their rule node
  (`means:#N`), compared canonically — placeholders normalized by
  position — so a placeholder rename invalidates nothing while a
  meaning change or a re-created node still does; code-authored rules
  fall back to pattern-key names.
  Stored documents are trees and need no reparse at all: a language
  change re-renders views and retries the gaps whose failed parse
  called a changed rule — the demo's healing moment, done
  incrementally.

The memo rides the parse tree, not the document. A result node already
records the rule that produced it and its children; what the memo adds
is bookkeeping: its text length and its examined length, stored as
*lengths relative to the node* rather than absolute offsets, so an edit
never shifts the positions of untouched nodes (absolute positions are
sums along the path, as in Tree-sitter and Lezer), plus the rules it
depended on. Results that don't become nodes — failed attempts, and
alternatives tried and rejected — live in a small side table keyed by
`(rule, position)`; failures must be remembered too, or an edit could
make a previously failed alternative succeed without being retried.

The committed document carries none of it. A document node is content
only — its `NodeData`, children, and id — because under continuous
canonical formatting the text is *derived* from content, and anything
derivable needn't be stored (and can't then go stale). The parse's
bookkeeping serves the session that owns it; when the session ends,
the document keeps the result, not the scaffolding.

Two constraints follow for the combinators: rule applications must be
*named* (they carry the identity the memo key needs), and every result —
including gaps and unrecognized runs — must report an honest examined
range, or invalidation will miss cases. The guard is a property test:
for any edit, an incremental reparse equals a full parse of the edited
text (see [`test/`](../../test/DESIGN.md)).

### What is built (`Memo`, `ParseSession`, `Tree`)

The memo lives where a parse lives — on the parse tree — and never
crosses into the document: committed nodes carry only their `NodeData`,
so the document model stays simple and generic, and no provenance can go
stale.

- Parse trees are relative (`Tree.scala`): a node's `from` is an offset
  from its parent's start and its `length` is its span; absolute
  positions are sums down the path, computed where they are needed
  (`spansOf`, the rule reader). Combinators still see absolute positions
  during a parse — a subtree's offsets are fixed when a parent adopts
  it, and never move again.
- `rule(name)` memoizes by `(name, position)` in the parse's
  `MemoTable`, and a fresh result also stamps its tree's root with a
  `ParseMark` — the same entry as node-relative data (rule, applied
  offset, consumed and examined lengths, calls). A reused result is the
  *same* subtree object.
- `ParseSession` holds text, grammar, the result tree, and a `side`
  table — one column per position holding only what the tree can't
  represent (rejected alternatives, results re-spanned since stamping);
  every other result is claimed by a mark on the tree itself. `edit` and
  `changeRules(grammar, names)` re-derive their seeds by walking the
  kept marks — an entry's positions are relative to its node, so
  nothing inside an entry or a subtree is touched — plus the side
  columns' own splice. `changedRules` diffs two `GrammarDef`s to supply
  the names (in
  [`../langdef/syntax/`](../langdef/syntax/DESIGN.md)).
  A version bump is a special change: the rules may be identical, but
  term tags pin the def's version — `changedSince` (in
  [`../language/TextSession.scala`](../language/DESIGN.md)) reports the
  rule diff plus a migration, and kept results re-pin their tags rather
  than reparse.
- Verified by property tests over a multi-rule grammar and the
  interpreted expression language: after hundreds of random edits, with
  rule changes interleaved, a session equals a full parse (trees, spans,
  positions, examined extents). A one-statement edit in a 20-statement
  text reruns under a quarter of the rule bodies, and untouched subtrees
  are the same objects, even after length-changing edits.

The sessions that carry this machinery into the pane — `TextSession`,
`InputSessions`, `GapRetry` — are in
[`../language/DESIGN.md`](../language/DESIGN.md); the commit-side
reconcile is in [`../document/typed/DESIGN.md`](../document/typed/DESIGN.md).
