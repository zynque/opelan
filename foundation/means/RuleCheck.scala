package opelan.foundation.means

// Well-formedness issues, as readable messages — reported, never thrown;
// a rule set with issues still compiles as far as it can (*Never broken*).

def ruleIssues(rule: Rule): List[String] = {
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
  val empty =
    if (rule.pattern.pieces.isEmpty) List(s"empty pattern: $shown") else Nil
  empty ++ duplicates ++ unbound ++ stray
}

// Set-level issues: the binding list must name operator-shaped rules,
// each at most once, and every operator-shaped rule needs a tier.
def ruleSetIssues(ruleSet: RuleSet): List[String] = {
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
  val untiered = ruleSet.rules.filter(r =>
    isOperatorPattern(r.pattern) && !tieredKeys.contains(r.pattern.key))
    .map(r => s"'${show(r.pattern)}' is an operator with no binding")
  val definedTwice = byKey.collect {
    case (_, rules) if rules.length > 1 =>
      s"'${show(rules.head.pattern)}' is defined more than once"
  }.toList
  ruleSet.rules.flatMap(ruleIssues) ++ definedTwice ++ unknown ++
    notOperators ++ listedTwice ++ untiered
}
