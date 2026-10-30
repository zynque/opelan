package opelan.foundation.means.engine

import opelan.foundation.document._
import opelan.foundation.grammar.toDetached
import opelan.foundation.means._
import opelan.foundation.means.text._

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
  val index = PrintIndex(set, termTag)
  import index._

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

  // Walk a construct rule's pattern: placeholders render their argument
  // tree, literals verbatim; `glued` suppresses the space before a
  // piece, as in the rule's own text.
  def walk(
      node: DetachedNode[NodeData], rule: Rule,
      ctxOf: (Int, NodeData) => Int): String = {
    val args = argumentIndex(rule)
    rule.pattern.pieces.zipWithIndex.map { case (piece, i) =>
      val text = piece match {
        case Piece.Literal(text, _) => text
        case Piece.Placeholder(name, _) =>
          args.get(name).flatMap(node.children.lift) match {
            case Some(child) => render(child, ctxOf(i, child.data))
            case None        => "?"
          }
      }
      if (i == 0 || piece.glued) text else " " + text
    }.mkString
  }

  (tree => render(tree, 0))
}

// A meaning term as the detached tree `printWith` renders — the
// projection of a stored meaning back toward phrase spelling (DESIGN
// §4, *Phrases desugar at entry*): `use`/`source`/`builtin` operands
// are name leaves, `quote` its stored subtree, and a construct the
// data a parse would emit over the argument trees.
def meaningTree(
    m: Meaning, tag: String => NodeData): DetachedNode[NodeData] =
  m.tag match {
    case "use" | "source" | "builtin" =>
      DetachedNode(NodeData.StringData(
        m.arguments.headOption.flatMap(Meaning.nameOf).getOrElse("")), Nil)
    case "quote" =>
      m.quoted.map(toDetached).getOrElse(
        DetachedNode(NodeData.GapData(""), Nil))
    case _ => DetachedNode(
      m.reference.map(NodeData.ExternalNodeRef(_)).getOrElse(tag(m.tag)),
      m.arguments.map(meaningTree(_, tag)))
  }
