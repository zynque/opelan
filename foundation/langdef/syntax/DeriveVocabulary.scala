package opelan.foundation.langdef.syntax

import opelan.foundation.langdef._
import opelan.foundation.means._

// A def's vocabulary derived from its rules. Construct terms come
// from construct meanings: `add(a, b)` declares a two-child `add`
// term, and nested constructs declare theirs too (`add(a, mul(b, 2))`
// makes mul a term). Leaf terms are the lexical vocabulary the rules
// name — every term a `where` clause binds — sorted, so encoding is
// deterministic. Construct order is first appearance, outermost
// first; a repeated name keeps its first declaration (the same
// first-wins the compile and print sides use). Impls' leaf
// productions and operand marking live code-side (lexicalTerms).
def vocabularyFrom(set: RuleSet): List[NodeTerm] =
  whereLeafNames(set).toList.sorted.map(
    NodeTerm(_, NodeShape.Leaf)) ++
  set.rules.flatMap(r => builds(r.meaning)).map(b =>
    NodeTerm(b.tag, NodeShape.Fixed(b.arguments.length), b.reference))
    .distinctBy(_.name)

// The leaf terms a rule set's `where` clauses bind — the narrowing
// half of the lexical vocabulary (`where n: number`, `where c, r:
// whole number`).
def whereLeafNames(set: RuleSet): Set[String] =
  set.rules.flatMap(_.where.map(_.term)).toSet

// Every construct term in a meaning, outermost first — each names a
// construct the rules can emit, so each needs a declared term. A builtin
// term declares nothing and its operands aren't terms at all — `use`/
// `source`/`builtin` take *names*, so the name leaf under one is never
// collected as a nullary construct.
private def builds(m: Meaning): List[Meaning] =
  if (Meaning.isConstruct(m))
    m +: m.arguments.flatMap(builds)
  else Nil
