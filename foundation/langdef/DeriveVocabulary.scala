package opelan.foundation.langdef

import opelan.foundation.means._

// A def's vocabulary derived from its rules' construct meanings:
// `add(a, b)` declares a two-child `add` term, and nested constructs
// declare theirs too (`add(a, mul(b, 2))` makes mul a term). Order is
// first appearance, outermost first; a repeated tag keeps its first
// declaration (the same first-wins the compile and print sides use).
// What the operand atom produces — leaf terms like literal/ref/gap —
// isn't in the rules and still lists by hand, until `where`-terms
// describe the lexical vocabulary too.
def vocabularyFrom(set: RuleSet): List[NodeTerm] =
  set.rules.flatMap(r => builds(r.meaning)).map(b =>
    NodeTerm(b.tag, NodeShape.Fixed(b.arguments.length), b.reference))
    .distinctBy(_.name)

// Every construct term in a meaning, outermost first — each names a
// construct the rules can emit, so each needs a declared term. A builtin
// term declares nothing and its operands aren't terms at all — `use`/
// `source`/`builtin` take *names*, so the name leaf under one is never
// collected as a nullary construct.
private def builds(m: Meaning): List[Meaning] =
  if (Meaning.isConstruct(m))
    m +: m.arguments.flatMap(builds)
  else Nil
