package opelan.foundation.langdef

import opelan.foundation.document.{
  ExternalNodeReference, NodeData, sameNode}
import opelan.foundation.grammar.PTree
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
      if (b.tiers == a.tiers && rulesSame(b.rules, a.rules)) Set.empty
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
  (bm.keySet ++ am.keySet).filter(k =>
    (bm.get(k), am.get(k)) match {
      case (Some(x), Some(y)) => !sameRule(x, y)
      case (x, y)             => x != y
    }).map(k => s"means:${k.mkString(" ")}")
}

// Rule comparison by identity, not by pin: a version bump re-pins a
// meaning's references to a newer reading of the same nodes — the
// rules it compiles to are unchanged, so sessions carry over.
private def rulesSame(a: List[Rule], b: List[Rule]): Boolean =
  a.length == b.length && a.zip(b).forall(sameRule.tupled)

private def sameRule(a: Rule, b: Rule): Boolean =
  a.pattern == b.pattern && a.where == b.where &&
    sameMeaning(a.meaning, b.meaning)

private def sameMeaning(a: Meaning, b: Meaning): Boolean =
  a.tag == b.tag && a.phrase == b.phrase && sameRef(a.reference, b.reference) &&
    a.arguments.length == b.arguments.length &&
    a.arguments.zip(b.arguments).forall(sameMeaning.tupled) &&
    sameTree(a.quoted, b.quoted)

private def sameRef(
    a: Option[ExternalNodeReference],
    b: Option[ExternalNodeReference]): Boolean =
  (a, b) match {
    case (Some(r), Some(s)) => sameNode(r, s)
    case (None, None)       => true
    case _                  => false
  }

private def sameTree(a: Option[PTree], b: Option[PTree]): Boolean =
  (a, b) match {
    case (Some(x), Some(y)) =>
      x.text == y.text && x.from == y.from && x.length == y.length &&
        sameData(x.data, y.data) && x.kids.length == y.kids.length &&
        x.kids.zip(y.kids).forall { case (p, q) =>
          sameTree(Some(p), Some(q)) }
    case (None, None) => true
    case _            => false
  }

private def sameData(a: NodeData, b: NodeData): Boolean = (a, b) match {
  case (NodeData.ExternalNodeRef(r), NodeData.ExternalNodeRef(s)) =>
    sameNode(r, s)
  case _ => a == b
}

// Every memo name a rule set compiles under — the conservative answer
// when a change can't be described finer than "the rules differ".
private def sequenceRuleNames(set: Option[RuleSet]): Set[String] =
  set.toList.flatMap(_.rules).filter(r => isSequencePattern(r.pattern))
    .map(r => sequenceRuleName(r.pattern)).toSet

// The content migration between two term-tag tables (term name → the
// data a parse emits): a tag that changed — a construct ref re-pinning
// to a newer def version, or a term gone from the vocabulary — rewrites to
// what a fresh parse would emit; every other node data is untouched.
def retagBy(
    before: Map[String, NodeData],
    tag: String => NodeData): NodeData => NodeData = {
  val diff = before.iterator
    .map { case (term, d) => d -> tag(term) }
    .filter { case (a, b) => a != b }
    .toMap
  d => diff.getOrElse(d, d)
}
