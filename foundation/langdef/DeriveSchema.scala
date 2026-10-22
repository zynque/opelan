package opelan.foundation.langdef

import opelan.foundation.means._

// A def's construct kinds derived from its rules' construct meanings:
// `add(a, b)` declares a two-child `add` kind, and nested Builds
// declare theirs too (`add(a, mul(b, 2))` makes mul a kind). Order is
// first appearance, outermost first; a repeated tag keeps its first
// declaration (the same first-wins the compile and print sides use).
// What the operand atom produces — leaf kinds like literal/ref/gap —
// isn't in the rules and still lists by hand, until `where`-kinds
// describe the lexical vocabulary too.
def schemaFrom(set: RuleSet): List[NodeKind] =
  set.rules.flatMap(r => builds(r.meaning)).map(b =>
    NodeKind(b.tag, NodeShape.Fixed(b.arguments.length), b.reference))
    .distinctBy(_.name)

// Every construct term in a meaning, outermost first — each names a
// construct the rules can emit, so each needs a kind. A builtin term
// declares nothing and its operands aren't terms at all — `use`/
// `source`/`builtin` take *names*, so the name leaf under one is never
// collected as a nullary construct.
private def builds(m: Meaning): List[Meaning] =
  if (Meaning.isConstruct(m))
    m +: m.arguments.flatMap(builds)
  else Nil
