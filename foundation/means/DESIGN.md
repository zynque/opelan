# `foundation/means/` — the `means` rule notation (in progress)

Today a language's meaning is scattered: the grammar maps `"*"` to the
tag `"mul"` inside a combinator tree, the vocabulary separately declared
`mul` as `Fixed(2)`, `Expr.pp` re-encodes precedence for printing, and
eval matches `Mul` in Scala — four artifacts held together by a string.
Authoring one requires knowing parser combinators (`Signum`,
`Unrecognized`, `Phrase`, stop sets), much of which is recovery
machinery leaking into the definition.

The target is the way mathematics is *taught*: a notation is introduced
by saying what it means in terms of something already understood
("3 × 4 means 4 + 4 + 4"; "5% means 5 per hundred"). Following OMeta and
VPRI STEPS — parsing and tree rewriting as one kind of pattern match,
and definitions small enough to describe themselves — a language
becomes a set of rules:

```
a + b    means add(a, b)
a - b    means sub(a, b)
a * b    means mul(a, b)
a / b    means div(a, b)
-a       means neg(a)
(a)      means a
|a|      means abs(a)
a %      means a / 100

binding, tightest first:
  -a
  a %
  a * b,  a / b
  a + b,  a - b

examples:
  1 + 2 * 3   means 1 + (2 * 3)
  8 - 3 - 2   means (8 - 3) - 2
```

**The keyword is itself a verbosity choice.** The verbose spelling is
lowercase `means`, emphasized by highlighting rather than capitals; the
terse spelling is `=>` (`a + b => add(a, b)`). Both read back as the
same rule — which spelling a surface shows is a detail-level setting
(see *Zoom navigation* in the root [`DESIGN.md`](../../DESIGN.md)), not
a difference in content. The reader and printer live in
[`text/`](text/DESIGN.md): rules print as role-tagged spans (keyword,
literal, placeholder, constructor tag, constraint term, marker), so a
highlighting surface and plain text come from one source. A
`Rule(pattern, meaning, where)` (`Rule.scala`) has a pattern of literal
and placeholder pieces; a meaning reads as a constructor (`add(a, b)`),
a bare placeholder (`(a) means a`), or a phrase in the same language
(`a % means a / 100`) — surface spellings of one form, the meaning term
below. A rule set adds binding-list tiers and worked examples. A rule's
shape (leaf, closed, prefix, postfix, infix — `Shape.scala`) is derived
from its pattern's edges, and `ruleIssues`/`ruleSetIssues` (in
[`text/RuleCheck.scala`](text/DESIGN.md)) report unbound or duplicate
placeholders and binding-list mistakes. Names are labels for now; a
constructor carries an optional term ref, filled in once rules are read
from a document (`RuleIdentity.scala`).

**One rule, every concern.** At the general level a rule is tree to
tree: the pattern is an abstract syntax tree — a shape of slots and
constructs — and the meaning is a term tree. The textual form (`a + b
means add(a, b)`) is the special case where the pattern is a token
spine, the flat piece list today's `Pattern` type encodes; the tree
pattern-matcher is the general engine of which packrat parsing is the
textual piece. Other projections of the same rule follow from the
pattern's shape: a structural LHS like `term name, shape` renders as a
form — text boxes, dropdowns, buttons — the canonical editor for the
construct the rule declares. The user-authored document is always the
input AST, preserved as-is; the meaning side says how the input is
interpreted — rendered as text, a chart, a 3d scene — it never rewrites
its input. Compilation is the same relation cached in a lower layer
(kernel or host) — a future optimization; interpretation is the model.
As in Scheme, the machinery is a pattern and a template over trees;
identity-by-node-id replaces hygienic renaming, and one rule yields both
matcher and renderer.

Read left to right, a rule is a production; its meaning names a node
term and its arity (the vocabulary); read right to left, a constructor
meaning is the printer — minimal-parenthesis printing follows from the
binding list, replacing hand-written `Expr.pp`. Several surfaces may
mean the same tree (`a × b`, `a times b`, `product of a, b` all `means
mul(a, b)`), so choosing a notation means choosing which rule set prints
the view — *Polysyntactic* as data.

How a meaning term evaluates, and how rules compile to grammar
combinators: [`engine/DESIGN.md`](engine/DESIGN.md). How rules are
stored in a `lang:` document:
[`../langdef/DESIGN.md`](../langdef/DESIGN.md).

**Phrases desugar at entry — projectional all the way.** A phrase
spelling like `a % means a / 100` is not stored as text to reparse
later. In a `lang:` document the meaning field is an input whose grammar
is the language being defined: the phrase parses as it is entered — its
placeholder names recognized against the rule's own pattern (each
becomes a `use` term), its concrete content stored as quoted subtrees —
so `a / 100` lands as `div(use(a), ‹100›)`. A phrase that can't parse
yet, because the rule it needs hasn't been entered, is an ordinary gap
input and heals when that rule arrives — the same gap-retry the
document surface already uses. The ordering sensitivity of a language
applied to itself becomes live feedback while authoring rather than a
deferred choice, and evaluation never sees anything but terms — there
is no `reparse` node in the stored form. `reparse` survives
transitionally for readers with no language context (code- and
text-written rules); it leaves once every meaning enters through a
language-aware input. (The machinery: `MeaningInput.scala` in
[`../langdef`](../langdef/DESIGN.md) and `Desugar.scala` in
[`engine/`](engine/DESIGN.md).)

**Precedence is an ordered list**, tightest first — the PEMDAS card,
not binding-power numbers and not inferred. Associativity defaults to
left and is overridden by example (`a ^ b ^ c means a ^ (b ^ c)`).
Worked examples are live checks (*Learnable and self documenting*,
*Reactive* — see `ExampleCheck.scala` in
[`../langdef/view`](../langdef/view/DESIGN.md)). Grouping needs no
special combinator: `(a) means a` is an ordinary rule.

**Placeholders and `where`.** In `a + b`, `a` and `b` are placeholders;
the rest is literal. A `where` clause constrains what may fill them:
`ref:c,r means cell(c, r) where c, r: whole number`. A projectional
surface distinguishes placeholders structurally (styled chips, as math
italicizes variables), so stored rules need no escaping; the text
surface needs a convention. The provisional one: a placeholder is a lone
lowercase letter, optionally numbered (`a`, `n`, `x2`); every other word
is literal, and quoting (`'x'`) forces a literal. Under *Adjustable
verbosity*, `where` is detail: hidden at the terse level, revealed on
zoom-in (see *Zoom navigation*, root [`DESIGN.md`](../../DESIGN.md)).
Hidden is never absent:

- a `where` implied by the layer below (`add` takes numbers ⇒
  `a, b: number`) shows nothing tersely, and appears marked as inferred
  when zoomed in;
- a `where` narrower than inference leaves a marker in the terse view,
  like a fold indicator;
- a violated `where` is enforced at every zoom level and surfaces as a
  gap.

Detail-level zoom stays within the rule's own layer and never descends
into the meaning's implementation; that is the separate *implementation*
axis.

**Layers form a DAG.** A meaning may refer only to things lower in the
dependency graph, never back up. Dependencies are between *rules*, not
languages, so one language may bundle several layers: `a % means a / 100`
sits above the `/` rule, which sits above `div`, which bottoms out in a
builtin (today's `Impls` registry in
[`../langdef`](../langdef/DESIGN.md) — the "already understood"
arithmetic). Authors don't number layers; a rule's layer is one above
the highest rule its meaning uses. Show/hide implementation walks this
graph downward.

- *Prevention in the interface*: completion on a meaning offers only
  rules already below it, so the structural editor cannot author a cycle
  (*Discoverable*, *Immediately typed*).
- *Tolerance in the model*: text edits and CRDT merges can still produce
  one — two collaborators each adding a rule that uses the other's merge
  cleanly in Automerge. A cycle renders as a gap at the edge that
  closes it, as ref cycles already do in evaluation (*Never broken*).

**Documents keep what the user wrote.** `5%` is stored as `percent(5)`,
not `div(5, 100)`. Lower layers are derived, consistent with views being
derived rather than stored, and printing round-trips the user's
notation.

**When a meaning resolves.** In the sheet, `ref:3,4` (or `C4`) means
"the cell at column 3, row 4" — but storing the position would retarget
the ref when a row is inserted, the classic spreadsheet bug. Instead the
meaning resolves at parse time to the cell's stable node identity
(today's `InternalNodeRef`) and prints back as its current position.
Rules need a way to mark parse-time versus evaluation-time meanings.

More examples the notation should handle: comparison chains
(`a < b < c means a < b and b < c`), juxtaposition
(`n x means n * x where n: number literal`), units
(`n kg means quantity(n, kilogram)`), mixfix
(`if c then a else b means choose(c, a, b)`), sheet ranges
(`a .. b means range(a, b)`), and non-textual input
(`drag cell x onto cell y means set y to ref(x)`).

Open questions: the plain-text placeholder convention; how parse-time
and evaluation-time meanings are marked; and whether alternative
notations live in one language document or as notation documents layered
over a shared one.

The step-by-step plan, with how each step is verified, is in `PLAN.md`
(*Next steps*).
