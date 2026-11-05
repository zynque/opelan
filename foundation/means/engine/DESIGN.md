# `foundation/means/engine/` — design

The rule engine: what a matched rule's meaning evaluates to, and how
rule sets compile down to [`foundation/grammar`](../../grammar/DESIGN.md)
combinator compositions.

## A meaning is a term, evaluated (`MeaningApply`, `Desugar`)

The meaning spellings are surface forms of one shape: a meaning is a
tree over a small vocabulary — `add(a, b)` constructs a node, a bare `a`
is `use(a)`, a bound-value reference, and `source`/`builtin`/`literal`
are further meaning builtins. The meaning tree is the program; the
produced tree is the value — `use(a)` evaluates to the bound tree and
never materializes a `use` node in the output. `literal` is the first
*computing* builtin: its operand is a value term — `literal(neg(n))`
negates the bound leaf's data — evaluation arriving in miniature at
parse time, so a signum is a rule (`-n means literal(neg(n)) where n:
number`) rather than a combinator. Two consequences: placeholders splice
at tree level, so `5 %` producing `div(5, 100)` keeps the `5` node's
identity; and meaning builtins belong to `lang:`'s vocabulary, not the
defined language's, so vocabulary derivation must not turn them into
constructs. Evaluation-time meanings (`add(a, b) means builtin num-add`)
are the same form — one evaluation story for parse-time and eval-time
layers alike.

A phrase spelling desugars when it is *read* (`Desugar.scala`): it
parses through the language's own rules, and the parsed tree becomes
meaning terms — placeholders as `use`, the language's constructs as
construct terms by reference, concrete content as `quote` — so at apply
time a desugared meaning is only ever term evaluation (see *Phrases
desugar at entry* in [`../DESIGN.md`](../DESIGN.md)).

## Combinators underneath, operator rules on top (`Compile`)

The parsing substrate stays the recovering combinators of
[`foundation/grammar`](../../grammar/DESIGN.md). `means` rules are a
notation *over* them — compiled to combinator compositions, never a
separate parser. This is the means layering applied to the parser
itself: an operator rule plus its place on the binding list *means* a
combinator composition, and show/hide implementation (see *Zoom
navigation*, root [`DESIGN.md`](../../../DESIGN.md)) reveals it. The
reasons:

- *Generality.* An operator-precedence (Pratt) engine is excellent at
  expressions but dispatches on a single token. Statements, sequences,
  optional and repeated parts, lookahead, layout, and context-dependent
  parsing are what combinators are for, and languages beyond arithmetic
  need them.
- *Composition.* Combinators compose; a closed operator table does not.
  Embedding one language's grammar in another (STATUS, *Language
  composition*) means composing parsers.
- *Continuity.* Total, recovering parsing already lives in the
  combinators (gaps instead of failure), and `precedenceTable` already
  shows the pattern: an operator table is one meta-combinator, not a
  format feature. Compiling rules to the same combinators makes parity
  with `ExprLanguage` a matter of producing the same composition.

Rules split by shape. An *operator-shaped* rule — placeholders at one or
both edges, on the binding list (`a + b`, `-a`, `a %`, `a ^ b`) — joins
a single **operator combinator**: a precedence-climbing loop that
generalizes today's `precedenceTable` (left-associative infix only) to
prefix, postfix, right- and non-associative infix (`Associativity`),
driven by the binding list. This is where Pratt's idea lives — as one
combinator, not as the engine. Every other rule — bracketed or
keyword-led (`(a)`, `|a|`, `if c then a else b`) or a plain sequence —
compiles to sequence and choice (`SequenceRule`), and serves as an atom
of the operator combinator. A literal-led rule whose placeholders are
*all* `where`-bound to leaf productions is lexical, not an operator:
`-n means literal(neg(n)) where n: number` reads a token, never a
sub-expression, so it compiles as an operand sequence — the production
the hand-written `signum` fused by hand. Authors write `means` rules
either way; they write combinators only when zooming into
implementation, which stays available as the escape hatch for what rules
can't express.

Caveats this design accepts:

- *Left recursion.* Operator rules avoid it by construction (the loop
  replaces recursion). A left-recursive non-operator rule would make a
  PEG-style combinator loop at the same position — today's `Ref` guard
  already degrades that to a gap. Warth's seed-growing left recursion
  (OMeta, Ohm) is the upgrade if real grammars need it.
- *Ordered choice.* Combinator choice takes the first alternative that
  applies, so overlapping rules resolve by order rather than reporting
  ambiguity. Detecting overlap and flagging it in the definition view is
  a possible refinement.
- *Two layers to learn.* Mitigated by keeping means the only authoring
  surface: whether a rule lands in the operator combinator or in a
  sequence is decided by its shape, not by the author.

Recovery stays in the combinators (a missing operand folds to a gap, as
`chainLeft` does today), keeping `Unrecognized`/`Phrase` out of authored
definitions.

## Printing (`Print`, `PrintIndex`, `PrintView`)

Read right to left, a constructor meaning is the printer: the engine
prints a tree back through the rule set that built it, and
minimal-parenthesis printing follows from the binding list — replacing
hand-written printers like `Expr.pp` (see *Read left to right* in
[`../DESIGN.md`](../DESIGN.md)).
