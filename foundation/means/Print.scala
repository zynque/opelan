package opelan.foundation.means

import opelan.foundation.document._
import opelan.foundation.grammar.toDetached

// A rule set read backwards — printing trees as text the rules read
// back (DESIGN.md §4, *The meaning side*): every construct-meaning
// rule is a template whose literals print verbatim and whose
// placeholders print their argument trees, and the binding list
// supplies the parentheses — the same minimal-parens dance Expr.print
// encodes by hand, derived instead.
//
// An operator rule prints each edge operand in the context the parser
// gave it: left-associative infix keeps its left operand at its own
// level and asks one tighter of the right; a postfix applies to the
// whole level's result; a prefix asks one tighter — except a same-tier
// prefix, which nests without parens (`- -a`). Non-operator constructs
// print placeholders unparenthesized — their literals delimit the
// content — and leaves and construct nodes no rule claims print through
// `leaf` / as `?` (Never Broken).
//
// `termTag` is the same constructor the compile takes (DefLanguage's
// term-name → node-data map), so a printed node is identified by the
// data a parse would emit — refs, not names. The trees are detached
// nodes in parser-output form (constructs by ExternalNodeRef).
def printWith(
    set: RuleSet,
    termTag: String => NodeData,
    leaf: NodeData => String): DetachedNode[NodeData] => String = {
  val tiers = orderedOperatorRules(set)
  val count = tiers.length
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

  // `ctx` is the print level a node must meet to stand bare.
  def render(node: DetachedNode[NodeData], ctx: Int): String = {
    val (body, level) = emit(node)
    if (level < ctx) s"($body)" else body
  }

  def emit(node: DetachedNode[NodeData]): (String, Int) =
    builders.get(node.data) match {
      case Some(rule) if isOperatorPattern(rule.pattern) =>
        (walk(node, rule, operandCtx(rule, _, _)),
         levels.getOrElse(rule.pattern.key, Int.MaxValue))
      case Some(rule) => (walk(node, rule, (_, _) => 0), Int.MaxValue)
      case None =>
        (if (node.children.isEmpty) leaf(node.data) else "?",
         Int.MaxValue)
    }

  // The ctx a pattern placeholder's tree prints under. Edge
  // placeholders get the precedence context their position implies;
  // inner ones (sequence rules only — operators have literal cores)
  // take none.
  def operandCtx(
      rule: Rule, pieceIndex: Int, child: DetachedNode[NodeData]): Int = {
    val pl = levels.getOrElse(rule.pattern.key, 0)
    rule.shape match {
      case Shape.Infix => if (pieceIndex == 0) pl else pl + 1
      case Shape.Postfix => pl
      case Shape.Prefix => if (isTierPrefix(child, pl)) pl else pl + 1
      case _ => 0
    }
  }

  // A same-tier prefix operand nests without parens — the operand slot
  // a prefix operator parses is the level's operand, which accepts
  // tier-i prefixes.
  def isTierPrefix(
      node: DetachedNode[NodeData], pl: Int): Boolean =
    builders.get(node.data).exists(r =>
      isOperatorPattern(r.pattern) && r.shape == Shape.Prefix &&
        levels.get(r.pattern.key).contains(pl))

  // Walk a construct rule's pattern: placeholders render their argument
  // tree, literals verbatim; `glued` suppresses the space before a
  // piece, as in the rule's own text.
  def walk(
      node: DetachedNode[NodeData], rule: Rule,
      ctxOf: (Int, DetachedNode[NodeData]) => Int): String = {
    val args = argumentIndex(rule)
    rule.pattern.pieces.zipWithIndex.map { case (piece, i) =>
      val text = piece match {
        case Piece.Literal(text, _) => text
        case Piece.Placeholder(name, _) =>
          args.get(name).flatMap(node.children.lift) match {
            case Some(child) => render(child, ctxOf(i, child))
            case None        => "?"
          }
      }
      if (i == 0 || piece.glued) text else " " + text
    }.mkString
  }

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

  (tree => render(tree, 0))
}

// A meaning term as the detached tree `printWith` renders — the
// projection of a stored meaning back toward phrase spelling (DESIGN
// §4, *Phrases desugar at entry*): `use`/`source`/`builtin` operands
// are name leaves, `quote` its stored subtree, `reparse` its template
// text, and a construct the data a parse would emit over the argument
// trees.
def meaningTree(
    m: Meaning, tag: String => NodeData): DetachedNode[NodeData] =
  m.tag match {
    case "use" | "source" | "builtin" =>
      DetachedNode(NodeData.StringData(
        m.arguments.headOption.flatMap(Meaning.nameOf).getOrElse("")), Nil)
    case "reparse" =>
      DetachedNode(NodeData.StringData(
        m.phrase.map(printPattern).getOrElse("")), Nil)
    case "quote" =>
      m.quoted.map(toDetached).getOrElse(
        DetachedNode(NodeData.GapData(""), Nil))
    case _ => DetachedNode(
      m.reference.map(NodeData.ExternalNodeRef(_)).getOrElse(tag(m.tag)),
      m.arguments.map(meaningTree(_, tag)))
  }
