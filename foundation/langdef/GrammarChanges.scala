package opelan.foundation.langdef

import opelan.foundation.document.NodeData
import opelan.foundation.means._

// The named rules of a grammar, by name. When a name is defined twice
// the first definition wins, everywhere it is used.
def namedRules(top: GrammarDef): Map[String, GrammarDef] = {
  def collect(g: GrammarDef): List[(String, GrammarDef)] = g match {
    case GrammarDef.Named(n, inner)     => (n -> inner) :: collect(inner)
    case GrammarDef.Choice(alts)        => alts.flatMap(collect)
    case GrammarDef.Parens(inner)       => collect(inner)
    case GrammarDef.ChainLeft(_, atom)  => collect(atom)
    case GrammarDef.Precedence(atom, _) => collect(atom)
    case GrammarDef.Phrase(inner, _)    => collect(inner)
    case GrammarDef.Tok(_, _)           => Nil
    case GrammarDef.Signum              => Nil
    case GrammarDef.Junk(_, _)          => Nil
    case GrammarDef.Ref(_)              => Nil
    case GrammarDef.Hole                => Nil
  }
  collect(top).reverse.toMap
}

// The rules a language change touches: named rules whose definitions
// differ, plus rules present in only one grammar. A rule that merely
// *calls* a changed rule is not listed — an incremental session drops
// its results through the call dependencies it recorded.
def changedRules(before: GrammarDef, after: GrammarDef): Set[String] = {
  val (a, b) = (namedRules(before), namedRules(after))
  (a.keySet ++ b.keySet).filter(name => a.get(name) != b.get(name))
}

// The memo names a syntax change touches — what a carried-over session
// must drop. Combinator defs diff by named rule, as before. With a rule
// set, the top name (the lexicon's — every placeholder recurses through
// it) covers operator changes and operand structure, and each sequence
// rule whose own definition changed adds its `means:` name — those are
// the only entries that could survive the top name's fall. A form
// switch, or a change to the atom grammar, marks everything the new
// parser can name.
def syntaxChanges(before: SyntaxDef, after: SyntaxDef): Set[String] =
  if (before == after) Set.empty
  else (before.rules, after.rules) match {
    case (None, None) => changedRules(before.grammar, after.grammar)
    case (Some(b), Some(a)) if before.grammar == after.grammar =>
      if (b.rules == a.rules && b.tiers == a.tiers) Set.empty
      else Set(after.lexicon) ++ changedSequenceNames(b, a)
    case _ =>
      Set(after.lexicon) ++ namedRules(after.grammar).keySet ++
        sequenceRuleNames(before.rules) ++ sequenceRuleNames(after.rules)
  }

// Sequence rules whose definitions differ between sets — their
// `means:` names are what the compiled grammar memoizes under.
private def changedSequenceNames(b: RuleSet, a: RuleSet): Set[String] = {
  def byKey(s: RuleSet) = s.rules.filter(r => isSequencePattern(r.pattern))
    .map(r => r.pattern.key -> r).toMap
  val (bm, am) = (byKey(b), byKey(a))
  (bm.keySet ++ am.keySet).filter(k => bm.get(k) != am.get(k))
    .map(k => s"means:${k.mkString(" ")}")
}

// Every memo name a rule set compiles under — the conservative answer
// when a change can't be described finer than "the rules differ".
private def sequenceRuleNames(set: Option[RuleSet]): Set[String] =
  set.toList.flatMap(_.rules).filter(r => isSequencePattern(r.pattern))
    .map(r => sequenceRuleName(r.pattern)).toSet

// The content migration between two kind-tag tables (kind name → the
// data a parse emits): a tag that changed — a construct ref re-pinning
// to a newer def version, or a kind gone from the schema — rewrites to
// what a fresh parse would emit; every other node data is untouched.
def retagBy(
    before: Map[String, NodeData],
    tag: String => NodeData): NodeData => NodeData = {
  val diff = before.iterator
    .map { case (kind, d) => d -> tag(kind) }
    .filter { case (a, b) => a != b }
    .toMap
  d => diff.getOrElse(d, d)
}
