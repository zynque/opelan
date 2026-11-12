package opelan.foundation.means.engine

import opelan.foundation.document._
import opelan.foundation.grammar.Associativity
import opelan.foundation.means._

// The rule-derived lookup both printers share (`printWith` over
// detached trees, `printViewWith` over document nodes): which
// construct data a rule prints — its meaning's ref, or `termTag`
// when the meaning names none — each operator pattern's print level
// from the binding list, and the precedence context a placeholder's
// operand prints under. Extracted so the string emitter and the
// view-tree emitter make the same minimal-parentheses decisions.
private[means] case class PrintIndex(
    set: RuleSet, termTag: String => NodeData,
    nameOf: ExternalNodeReference => Option[String] = (_ => None),
    defUrl: Option[String] = None) {
  val tiers: List[List[Rule]] = orderedOperatorRules(set)
  val count: Int = tiers.length

  // Pattern key → print level: higher binds tighter. Tier 0 is the
  // tightest, so level = count - index; a construct that isn't an
  // operator (sequence rule, leaf rule) prints at atom level — its
  // own literals delimit it.
  val levels: Map[List[String], Int] =
    tiers.zipWithIndex.flatMap { case (rs, i) =>
      rs.map(r => r.pattern.key -> (count - i)) }.toMap

  // A node prints through the rule it applies: key the construct's
  // own declaration ref (a `*` node) and, for a construct meaning,
  // the meaning's ref as before — a stored `mul(a, b)` still prints
  // `a * b`. List-valued: a name may declare several arities (`+n`
  // beside `a + b`); `builderFor` selects by child count.
  val builders: Map[NodeData, List[Rule]] = set.rules.flatMap { r =>
    (if (Meaning.isConstruct(r.meaning))
      List(r.meaning.reference.map(NodeData.ExternalNodeRef(_))
        .getOrElse(termTag(r.meaning.tag)) -> r)
    else Nil) ++
    r.pattern.constructForm.map { case (name, _) =>
      termTag(name) -> r }.toList
  }.map { case (d, r) => canonical(d) -> r }
    // Same-doc keys arrive two ways — termTagOf emits an
    // InternalNodeRef, a stored meaning's reference the external
    // form — so lookups key on the canonical external form.
    .groupBy(_._1).map { case (d, rs) => d -> rs.map(_._2) }

  private def canonical(data: NodeData): NodeData =
    data match {
      case NodeData.InternalNodeRef(id) =>
        defUrl.map(url => NodeData.ExternalNodeRef(
          ExternalNodeReference(url, 0, id))).getOrElse(data)
      case _ => data
    }

  // The rule a node prints through: the one whose arity matches the
  // node's children — the declaration's placeholder count for a
  // declared-construct node, the meaning's arguments for a node the
  // meaning itself materialized.
  def builderFor(data: NodeData, arity: Int): Option[Rule] = {
    // A ref anchored in another encoding of the language names its
    // construct through `nameOf`, then keys by that term's data.
    val key = data match {
      case NodeData.ExternalNodeRef(r) if !builders.contains(data) =>
        nameOf(r).map(termTag).map(canonical).getOrElse(data)
      case _ => canonical(data)
    }
    val candidates = builders.getOrElse(key, Nil)
    // A node that declares its own construct prints through that
    // rule before one whose meaning merely coincides — the `%` in
    // `a % means a / 100` doesn't swallow `/`'s own spelling.
    candidates.find(r =>
      r.pattern.constructForm.exists(_._2.length == arity)).orElse(
        candidates.find(_.meaning.arguments.length == arity))
  }

  // Each infix rule's associativity as the worked examples derive
  // it — the operand the chain continues prints at the rule's own
  // level, the regrouping one tighter.
  private val assocs: Map[List[String], Associativity] =
    set.rules.map(r => r.pattern.key -> operatorAssoc(set, r)).toMap

  // The ctx a pattern placeholder's tree prints under. Edge
  // placeholders get the precedence context their position implies;
  // inner ones (sequence rules only — operators have literal cores)
  // take none.
  def operandCtx(rule: Rule, pieceIndex: Int, child: NodeData): Int = {
    val pl = levels.getOrElse(rule.pattern.key, 0)
    rule.shape match {
      case Shape.Infix =>
        assocs.getOrElse(rule.pattern.key, Associativity.Left) match {
          case Associativity.Left  => if (pieceIndex == 0) pl else pl + 1
          case Associativity.Right => if (pieceIndex == 0) pl + 1 else pl
          case Associativity.NonAssociative   => pl + 1
        }
      case Shape.Postfix => pl
      case Shape.Prefix  => if (isTierPrefix(child, pl)) pl else pl + 1
      case _             => 0
    }
  }

  // A same-tier prefix operand nests without parentheses — the operand slot
  // a prefix operator parses is the level's operand, which accepts
  // tier-i prefixes.
  private def isTierPrefix(child: NodeData, pl: Int): Boolean =
    builders.getOrElse(child, Nil).exists(r =>
      isOperatorPattern(r.pattern) && r.shape == Shape.Prefix &&
        levels.get(r.pattern.key).contains(pl))

  // Placeholder name → the argument position it fills, so its child is
  // the tree it prints. A name repeated (`add(a, a)`) binds the first;
  // a pattern placeholder the meaning never names prints as a gap.
  def argumentIndex(rule: Rule): Map[String, Int] =
    if (Meaning.isConstruct(rule.meaning))
      rule.meaning.arguments.zipWithIndex.flatMap { case (a, i) =>
        if (a.tag == "use")
          a.arguments.headOption.flatMap(Meaning.nameOf).map(_ -> i)
        else None
      }.reverse.toMap
    else Map.empty
}
