package opelan.foundation.langdef.syntax

import opelan.foundation.document.{
  ExternalNodeReference, NodeData}
import opelan.foundation.document.typed.sameNode
import opelan.foundation.grammar.PTree
import opelan.foundation.langdef._
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
    case GrammarDef.Unrecognized(_, _)  => Nil
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
// set: nothing to drop when the sets compile identically (sameCompiled —
// a placeholder rename is no change); otherwise the top name (the
// lexicon's — every placeholder recurses through it) covers operator
// changes and operand structure, each changed or unpaired sequence rule
// adds its memo name — those are the only entries that could survive
// the top name's fall. A form switch, or a change to the atom grammar,
// marks everything the new parser can name.
def syntaxChanges(before: SyntaxDef, after: SyntaxDef): Set[String] =
  if (before == after) Set.empty
  else (before.rules, after.rules) match {
    case (None, None) => changedRules(before.grammar, after.grammar)
    case (Some(b), Some(a)) if before.grammar == after.grammar =>
      if (namesSame(b, a) && sameCompiled(b, a, sameRule)) Set.empty
      else Set(after.lexicon) ++ changedSequenceNames(b, a)
    case _ =>
      Set(after.lexicon) ++ namedRules(after.grammar).keySet ++
        sequenceRuleNames(before.rules) ++ sequenceRuleNames(after.rules)
  }

// The memo-name space a set occupies. Entries keyed under a name the
// change drops are dead weight — and callers the diff can't name would
// keep stale results — so identical compilation alone isn't enough to
// carry a session: a rule re-created as a new node with identical
// content reports its new name rather than carrying nothing.
private def namesSame(b: RuleSet, a: RuleSet): Boolean =
  sequenceRuleNames(Some(b)) == sequenceRuleNames(Some(a))

// Sequence rules whose definitions differ between sets — their memo
// names are what the compiled grammar memoizes under. Rules pair by
// identity: node id once they live in a `lang:` document (a rename
// keeps the pairing), pattern key for code-written rules. An unpaired
// rule names itself — entries it was called from must drop.
private def changedSequenceNames(b: RuleSet, a: RuleSet): Set[String] = {
  def seqs(s: RuleSet) = s.rules.filter(r => isSequencePattern(r.pattern))
  val (bs, as) = (seqs(b), seqs(a).zipWithIndex)
  val claimed = scala.collection.mutable.Set.empty[Int]
  val names = Set.newBuilder[String]
  bs.foreach { br =>
    as.find { case (ar, i) => !claimed(i) && sameIdentity(br, ar) } match {
      case Some((ar, i)) =>
        claimed += i
        if (!sameRule(br, ar)) names += sequenceRuleName(br)
      case None => names += sequenceRuleName(br)
    }
  }
  as.foreach { case (ar, i) =>
    if (!claimed(i)) names += sequenceRuleName(ar) }
  names.result()
}

private def sameIdentity(a: Rule, b: Rule): Boolean =
  (a.id, b.id) match {
    case (Some(x), Some(y)) => x == y
    case (None, None)       => a.pattern.key == b.pattern.key
    case _                  => false
  }

// Rule comparison by compiled meaning, not by pin or spelling:
// placeholders compare by position (canonicalRule — a rename compiles
// identically), and a version bump re-pins a meaning's references to a
// newer reading of the same nodes — the rules it compiles to are
// unchanged, so sessions carry over.
private def sameRule(a: Rule, b: Rule): Boolean = {
  val (x, y) = (canonicalRule(a), canonicalRule(b))
  x.pattern == y.pattern && x.where == y.where &&
    sameMeaning(x.meaning, y.meaning)
}

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
    .map(sequenceRuleName).toSet

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
