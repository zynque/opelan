package opelan.foundation.means

import opelan.foundation.document._
import opelan.foundation.grammar._

// Compile a rule set to a parser (DESIGN.md §4, *Engine*): operator-
// shaped rules join the operator table in binding-list order; every
// other rule compiles to a sequence and serves as an operand.
//
//   compileRules(exprRules, leafAtom, kindTag)
//
// `atom` supplies the operand syntax rules don't yet describe —
// leaf tokens, the signum, the recovering junk fallback — until
// where-constraints bind placeholders to a kind vocabulary. `tag`
// turns a constructor name into node data (DefLanguage.kindTag), so
// built nodes name their construct by reference. The result is the
// bare expression parser; callers wrap it in `rule`/`phrase` like
// any grammar.
//
// Compiles as far as it can (Never Broken): rules the binding list
// forgot join loosest, a pattern a sequence can't compile (leading
// placeholder — `a , b , c` would left-recurse) is skipped, and a
// malformed meaning degrades to a hole at apply time. Associativity
// defaults to left until worked examples override it; `where`
// constraints are carried but not yet enforced.
def compileRules(
    set: RuleSet,
    atom: Parser,
    tag: String => NodeData): Parser = {
  // `self` is the parser placeholders recurse through; the extra
  // indirection keeps its initialization trivial — `compiled` forces
  // `operand`, which names `self`, so `self` must be set first.
  lazy val self: Parser = (in, pos) => compiled(in, pos)
  lazy val compiled: Parser = operatorTable(operand, operatorTiers)
  lazy val operand: Parser =
    choice(sequences.map(sequenceParser) :+ atom*)
  lazy val operatorTiers: List[List[Operator]] =
    orderedOperatorRules(set).map(_.map(toOperator))
  lazy val sequences: List[Rule] = set.rules.filter(isSequenceRule)

  def toOperator(theRule: Rule): Operator = {
    val names = theRule.pattern.placeholders
    Operator(
      name = theRule.pattern.key.mkString(" "),
      shape = theRule.shape match {
        case Shape.Infix   => OperatorShape.Infix
        case Shape.Postfix => OperatorShape.Postfix
        case _             => OperatorShape.Prefix
      },
      literals = theRule.pattern.pieces.collect {
        case Piece.Literal(text, _) => text },
      build = (operands, in, from, to) =>
        applyMeaning(theRule.meaning, names.zip(operands).toMap,
          in, from, to, self, tag))
  }

  def sequenceParser(theRule: Rule): Parser =
    sequenceRule(theRule, self, tag)

  self
}

// Operator-shaped rules in binding order: each tier's patterns
// resolve to their rules, and any operator the list forgot appends
// as one loosest tier — it compiles rather than disappearing.
private def orderedOperatorRules(set: RuleSet): List[List[Rule]] = {
  val operators = set.rules.filter(r => isOperatorPattern(r.pattern))
  val byKey = operators.map(r => r.pattern.key -> r).toMap
  val tiered = set.tiers.map(t => t.patterns.flatMap(p => byKey.get(p.key)))
  val placed = tiered.flatten.map(_.pattern.key).toSet
  val untiered = operators.filterNot(r => placed.contains(r.pattern.key))
  if (untiered.isEmpty) tiered else tiered :+ untiered
}

// A rule that compiles to a sequence: not operator-shaped, and
// starting with a literal — a leading-placeholder pattern like
// `a , b , c` would left-recurse, so it is skipped.
private def isSequenceRule(rule: Rule): Boolean =
  !isOperatorPattern(rule.pattern) &&
    rule.pattern.pieces.headOption.exists(_.isInstanceOf[Piece.Literal])
