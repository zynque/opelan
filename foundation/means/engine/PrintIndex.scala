package opelan.foundation.means.engine

import opelan.foundation.document._
import opelan.foundation.grammar.Assoc
import opelan.foundation.means._

// The rule-derived lookup both printers share (`printWith` over
// detached trees, `printViewWith` over document nodes): which
// construct data a rule prints — its meaning's ref, or `termTag`
// when the meaning names none — each operator pattern's print level
// from the binding list, and the precedence context a placeholder's
// operand prints under. Extracted so the string emitter and the
// view-tree emitter make the same minimal-parens decisions.
private[means] case class PrintIndex(
    set: RuleSet, termTag: String => NodeData) {
  val tiers: List[List[Rule]] = orderedOperatorRules(set)
  val count: Int = tiers.length

  // Pattern key → print level: higher binds tighter. Tier 0 is the
  // tightest, so level = count - index; a construct that isn't an
  // operator (sequence rule, leaf rule) prints at atom level — its
  // own literals delimit it.
  val levels: Map[List[String], Int] =
    tiers.zipWithIndex.flatMap { case (rs, i) =>
      rs.map(r => r.pattern.key -> (count - i)) }.toMap

  val builders: Map[NodeData, Rule] = set.rules.reverse.flatMap { r =>
    if (Meaning.isConstruct(r.meaning))
      List(r.meaning.reference.map(NodeData.ExternalNodeRef(_))
        .getOrElse(termTag(r.meaning.tag)) -> r)
    else Nil
  }.toMap

  // Each infix rule's associativity as the worked examples derive
  // it — the operand the chain continues prints at the rule's own
  // level, the regrouping one tighter.
  private val assocs: Map[List[String], Assoc] =
    set.rules.map(r => r.pattern.key -> operatorAssoc(set, r)).toMap

  // The ctx a pattern placeholder's tree prints under. Edge
  // placeholders get the precedence context their position implies;
  // inner ones (sequence rules only — operators have literal cores)
  // take none.
  def operandCtx(rule: Rule, pieceIndex: Int, child: NodeData): Int = {
    val pl = levels.getOrElse(rule.pattern.key, 0)
    rule.shape match {
      case Shape.Infix =>
        assocs.getOrElse(rule.pattern.key, Assoc.Left) match {
          case Assoc.Left  => if (pieceIndex == 0) pl else pl + 1
          case Assoc.Right => if (pieceIndex == 0) pl + 1 else pl
          case Assoc.Non   => pl + 1
        }
      case Shape.Postfix => pl
      case Shape.Prefix  => if (isTierPrefix(child, pl)) pl else pl + 1
      case _             => 0
    }
  }

  // A same-tier prefix operand nests without parens — the operand slot
  // a prefix operator parses is the level's operand, which accepts
  // tier-i prefixes.
  private def isTierPrefix(child: NodeData, pl: Int): Boolean =
    builders.get(child).exists(r =>
      isOperatorPattern(r.pattern) && r.shape == Shape.Prefix &&
        levels.get(r.pattern.key).contains(pl))

  // Placeholder name → the argument position it fills, so its child is
  // the tree it prints. A name repeated (`add(a, a)`) binds the first;
  // a pattern placeholder the meaning never names prints as a hole.
  def argumentIndex(rule: Rule): Map[String, Int] =
    if (Meaning.isConstruct(rule.meaning))
      rule.meaning.arguments.zipWithIndex.flatMap { case (a, i) =>
        if (a.tag == "use")
          a.arguments.headOption.flatMap(Meaning.nameOf).map(_ -> i)
        else None
      }.reverse.toMap
    else Map.empty
}
