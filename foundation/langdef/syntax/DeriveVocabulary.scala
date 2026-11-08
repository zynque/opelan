package opelan.foundation.langdef.syntax

import opelan.foundation.langdef._
import opelan.foundation.means._

// A def's vocabulary derived from its rules — the terms its rules
// *declare*: construct-application patterns declare constructs
// (`square(a) means mul(a, a)` declares `square`), and `where`
// clauses declare the lexical terms they bind (`where n: number` —
// sorted, so encoding is deterministic). Meanings never declare —
// they only reference, bound to their term's ref on read
// (RuleRead's resolver): `div(a, b)` is a use of `div`, not its
// declaration. Impls' leaf productions live code-side
// (lexicalTerms); which are operands is authored by lone-placeholder
// operand rules.
def vocabularyFrom(set: RuleSet): List[NodeTerm] =
  whereLeafNames(set).toList.sorted.map(
    NodeTerm(_, NodeShape.Leaf)) ++
  set.rules.flatMap(r => r.pattern.constructForm).map { case (name, ps) =>
    NodeTerm(name, NodeShape.Fixed(ps.length)) }
    .distinctBy(_.name)

// The leaf terms a rule set's `where` clauses bind — the narrowing
// half of the lexical vocabulary (`where n: number`, `where c, r:
// whole number`).
def whereLeafNames(set: RuleSet): Set[String] =
  set.rules.flatMap(_.where.map(_.term)).toSet
