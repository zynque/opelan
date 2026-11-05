package opelan.foundation.langdef.syntax

import opelan.foundation.document.{
  ExternalNodeReference, NodeData}
import opelan.foundation.document.typed.sameNode
import opelan.foundation.grammar.PTree
import opelan.foundation.langdef._
import opelan.foundation.means._

// The memo names a syntax change touches — what a carried-over session
// must drop. Nothing to drop when the sets compile identically
// (sameCompiled — a placeholder rename is no change); otherwise the
// top name (the lexicon's — every placeholder recurses through it)
// covers operator changes and operand structure, each changed or
// unpaired sequence rule adds its memo name — those are the only
// entries that could survive the top name's fall.
def syntaxChanges(before: SyntaxDef, after: SyntaxDef): Set[String] =
  if (before == after) Set.empty
  else (before.rules, after.rules) match {
    case (Some(b), Some(a)) =>
      if (namesSame(b, a) && sameCompiled(b, a, sameRule)) Set.empty
      else Set(after.lexicon) ++ changedSequenceNames(b, a)
    case _ =>
      Set(after.lexicon) ++
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
  a.tag == b.tag && sameRef(a.reference, b.reference) &&
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
        sameData(x.data, y.data) && x.children.length == y.children.length &&
        x.children.zip(y.children).forall { case (p, q) =>
          sameTree(Some(p), Some(q)) }
    case (None, None)       => true
    case _                  => false
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
