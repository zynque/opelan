package opelan.foundation.means

// Rule identity for language-change diffing (DESIGN §4, *Incremental
// parsing*): rules have stable identities — their node ids in the
// `lang:` document — so the memo key survives renames and only real
// changes invalidate. A placeholder rename isn't a real change:
// `x + y means add(x, y)` and `a + b means add(a, b)` compile to the
// same parser, so diffing compares rules with placeholders renamed
// by position.

// `a + b` → `p0 + p1` through pattern, where, and meaning —
// `use`/`source` operands and `reparse`/`phrase` placeholders rename
// with the pattern; `builtin` operands name builtins rather than
// placeholders, and `quote` subtrees hold concrete content.
def canonicalRule(r: Rule): Rule = {
  val rename = r.pattern.placeholders.distinct.zipWithIndex.map {
    case (n, i) => n -> s"p$i" }.toMap
  def ph(n: String): String = rename.getOrElse(n, n)
  def piece(p: Piece): Piece = p match {
    case Piece.Placeholder(n, g) => Piece.Placeholder(ph(n), g)
    case _                       => p
  }
  def meaning(m: Meaning): Meaning = m.tag match {
    case "use" | "source" | "literal" =>
      m.copy(arguments = m.arguments.map(a =>
        if (Meaning.nameOf(a).isDefined) a.copy(tag = ph(a.tag))
        else meaning(a)))
    case "builtin" => m
    case _ => m.copy(
      arguments = m.arguments.map(meaning),
      phrase = m.phrase.map(p => Pattern(p.pieces.map(piece))))
  }
  r.copy(
    pattern = Pattern(r.pattern.pieces.map(piece)),
    meaning = meaning(r.meaning),
    where = r.where.map(c => c.copy(placeholders = c.placeholders.map(ph))))
}

// The memo name a sequence rule compiles under — its identity for
// invalidation: the rule node's id once rules live in `lang:`
// documents (a rename keeps it, so only real changes invalidate),
// the pattern key for rules written in code.
def sequenceRuleName(theRule: Rule): String =
  theRule.id match {
    case Some(id) => s"means:#$id"
    case None     => s"means:${theRule.pattern.key.mkString(" ")}"
  }

// Do two sets compile to the same parser? The rules compare pairwise
// by `sameRule`, and the binding list compares as *resolved* — tiers
// looked up to their rules, untiered appended — so a pattern renamed
// out from under its tier is a real change (the rule falls loosest)
// while a consistent rename resolves identically.
def sameCompiled(b: RuleSet, a: RuleSet,
    sameRule: (Rule, Rule) => Boolean): Boolean = {
  def tiersSame: Boolean = {
    val (ob, oa) = (orderedOperatorRules(b), orderedOperatorRules(a))
    ob.length == oa.length && ob.zip(oa).forall { case (tb, ta) =>
      tb.length == ta.length && tb.zip(ta).forall(sameRule.tupled) }
  }
  b.rules.length == a.rules.length &&
    b.rules.zip(a.rules).forall(sameRule.tupled) && tiersSame
}

// Operator-shaped rules in binding order: each tier's patterns
// resolve to their rules, and any operator the list forgot appends
// as one loosest tier — it compiles rather than disappearing. Also
// the printer's binding-strength table (printWith).
private[means] def orderedOperatorRules(set: RuleSet): List[List[Rule]] = {
  val operators = set.rules.filter(r => isOperatorPattern(r.pattern))
  val byKey = operators.map(r => r.pattern.key -> r).toMap
  val tiered = set.tiers.map(t => t.patterns.flatMap(p => byKey.get(p.key)))
  val placed = tiered.flatten.map(_.pattern.key).toSet
  val untiered = operators.filterNot(r => placed.contains(r.pattern.key))
  if (untiered.isEmpty) tiered else tiered :+ untiered
}
