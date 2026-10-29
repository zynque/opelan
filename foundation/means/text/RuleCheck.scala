package opelan.foundation.means.text

import opelan.foundation.means._

// Well-formedness issues, as readable messages — reported, never thrown;
// a rule set with issues still compiles as far as it can (*Never broken*).

// `leaves` is the leaf vocabulary `where` terms resolve against —
// the def's builtin leaf productions (`lexicalTerms`); empty flags
// every `where` term as unknown.
def ruleIssues(rule: Rule, leaves: Set[String] = Set.empty): List[String] = {
  val bound = rule.pattern.placeholders
  val shown = printRule(rule)
  val duplicates = bound.diff(bound.distinct).distinct
    .map(name => s"'$name' appears twice in the pattern of: $shown")
  val unbound = Meaning.placeholders(rule.meaning).distinct
    .filterNot(bound.contains)
    .map(name => s"'$name' is not in the pattern of: $shown")
  val stray = rule.where.flatMap(_.placeholders).distinct
    .filterNot(bound.contains)
    .map(name => s"where names '$name', which is not in the pattern of: $shown")
  val unknown = rule.where.map(_.term).distinct.filterNot(leaves)
    .map(t => s"where names '$t', which is not a leaf production")
  val empty =
    if (rule.pattern.pieces.isEmpty) List(s"empty pattern: $shown") else Nil
  empty ++ duplicates ++ unbound ++ stray ++ unknown
}

// Set-level issues: the binding list must name operator-shaped rules,
// each at most once, and every operator-shaped rule needs a tier.
// `leaves` is the leaf vocabulary `where` terms check against.
def ruleSetIssues(
    ruleSet: RuleSet, leaves: Set[String] = Set.empty): List[String] = {
  val byKey = ruleSet.rules.groupBy(_.pattern.key)
  val tiered = ruleSet.tiers.flatMap(_.patterns)
  def show(pattern: Pattern) = printPattern(pattern)
  val unknown = tiered.filterNot(p => byKey.contains(p.key))
    .map(p => s"binding list names '${show(p)}', which has no rule")
  val notOperators = tiered.filter(p =>
    byKey.contains(p.key) && !isOperatorPattern(p))
    .map(p => s"'${show(p)}' is not an operator; it needs no binding")
  val listedTwice = tiered.groupBy(_.key).collect {
    case (_, ps) if ps.length > 1 =>
      s"'${show(ps.head)}' is on the binding list more than once"
  }.toList
  val tieredKeys = tiered.map(_.key).toSet
  // An operator-shaped rule that is literal-led and binds every
  // placeholder through `where` declares operand intent — compileRules
  // emits it as an operand sequence when the terms resolve to leaf
  // productions, so it needs no binding. A `where` naming a term the
  // leaf vocabulary doesn't know is reported by `ruleIssues` instead.
  def operandBound(r: Rule): Boolean =
    r.pattern.pieces.headOption.exists(_.isInstanceOf[Piece.Literal]) &&
      r.pattern.placeholders.forall(
        r.where.flatMap(_.placeholders).toSet.contains)
  val untiered = ruleSet.rules.filter(r =>
    isOperatorPattern(r.pattern) && !tieredKeys.contains(r.pattern.key) &&
      !operandBound(r))
    .map(r => s"'${show(r.pattern)}' is an operator with no binding")
  val definedTwice = byKey.collect {
    case (_, rules) if rules.length > 1 =>
      s"'${show(rules.head.pattern)}' is defined more than once"
  }.toList
  ruleSet.rules.flatMap(r => ruleIssues(r, leaves)) ++ definedTwice ++
    unknown ++ notOperators ++ listedTwice ++ untiered
}
