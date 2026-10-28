package opelan.foundation.means.engine

import opelan.foundation.document._
import opelan.foundation.document.typed.externalRefOf
import opelan.foundation.means._
import opelan.foundation.view.ViewTree._

// printWith's provenance sibling (DESIGN §4, derived views): the same
// rule-derived minimal-parens printing, walked over a document's
// located nodes and emitted as a view tree — every emitted piece
// carries a `src:` leaf naming the node it was derived from, so a
// click in a derived print view selects its producer. Construct nodes
// print through their rule's pattern inside a provenance flow;
// parentheses inserted for precedence are unprovenanced formatting;
// anything no rule claims prints through `leaf`, tagged to its node.
def printViewWith(
    set: RuleSet,
    termTag: String => NodeData,
    leaf: NodeData => DetachedNode[NodeData])(
    doc: Document[NodeData], rootId: Int): DetachedNode[NodeData] = {
  val index = PrintIndex(set, termTag)
  import index._

  // A node's construct tag, resolved: committed documents internalize
  // refs, so the tag a rule built by ExternalNodeRef arrives as an
  // InternalNodeRef into the imports — externalRefOf reads through.
  def resolved(id: Int): NodeData =
    externalRefOf(doc, id).map(NodeData.ExternalNodeRef(_))
      .getOrElse(doc.getNode(id).map(_.data)
        .getOrElse(NodeData.GapData("")))

  // `ctx` is the print level a node must meet to stand bare.
  def render(id: Int, ctx: Int): DetachedNode[NodeData] = {
    val (body, level) = emit(id)
    if (level < ctx) flow(txt("("), body, txt(")")) else body
  }

  def emit(id: Int): (DetachedNode[NodeData], Int) =
    doc.getNode(id) match {
      case None => (from(id, hole("missing")), Int.MaxValue)
      case Some(node) => builders.get(resolved(id)) match {
        case Some(rule) if isOperatorPattern(rule.pattern) =>
          (flow(src(id) +: walk(node, rule, operandCtx(rule, _, _))*),
           levels.getOrElse(rule.pattern.key, Int.MaxValue))
        case Some(rule) =>
          (flow(src(id) +: walk(node, rule, (_, _) => 0)*), Int.MaxValue)
        case None => (from(id, leaf(node.data)), Int.MaxValue)
      }
    }

  // The pattern's pieces as view children: literals as text runs,
  // placeholders as their argument's subtree. A space separating a
  // placeholder from the text run before it joins that run, so an
  // operator's separators emit as fused runs (" + ") rather than
  // whitespace fragments.
  def walk(node: Node[NodeData], rule: Rule,
      ctxOf: (Int, NodeData) => Int): List[DetachedNode[NodeData]] = {
    val args = argumentIndex(rule)
    rule.pattern.pieces.zipWithIndex.foldLeft(
        List.empty[DetachedNode[NodeData]]) { case (acc, (piece, i)) =>
      piece match {
        case Piece.Literal(text, _) =>
          acc :+ txt(if (i == 0 || piece.glued) text else " " + text)
        case Piece.Placeholder(name, _) =>
          val child = args.get(name).flatMap(node.childIds.lift) match {
            case Some(cid) => render(cid, ctxOf(i, resolved(cid)))
            case None => hole("?")
          }
          if (i == 0 || piece.glued) acc :+ child
          else acc.lastOption match {
            case Some(DetachedNode(NodeData.StringData(t), Nil)) =>
              acc.dropRight(1) :+ txt(t + " ") :+ child
            case _ => acc :+ txt(" ") :+ child
          }
      }
    }
  }

  render(rootId, 0)
}
