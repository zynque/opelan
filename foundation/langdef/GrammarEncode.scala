package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.Detached._

// A grammar combinator as a node subtree. The sub-grammar comes first
// (parens inner, chain atom, precedence atom, phrase inner, named's
// body) with parameters after — matching how the combinators read.
// An operator's tag is an internal ref to the schema entry of the kind
// it builds (`kindIds`); a kind missing from the schema stays a string
// the reader degrades. `use` is written with the rule's name and linked
// to its `named` node once the document exists (linkUses).
private[langdef] def grammarNode(
    g: GrammarDef, kindIds: Map[String, Int]): DetachedNode[NodeData] = {
  def go(g: GrammarDef): DetachedNode[NodeData] = g match {
    case GrammarDef.Tok(kind, leaf) => n(pending("tok"), sl(kind), leafNode(leaf))
    case GrammarDef.Choice(alts)    => n(pending("choice"), alts.map(go)*)
    case GrammarDef.Parens(inner)   => n(pending("parens"), go(inner))
    case GrammarDef.Signum          => DetachedNode.leaf(pending("signum"))
    case GrammarDef.Junk(start, run) =>
      n(pending("junk"), sl(words(start)), sl(words(run)))
    case GrammarDef.ChainLeft(ops, atom) =>
      n(pending("chain"), go(atom), n(pending("ops"), opPairs(ops)*))
    case GrammarDef.Precedence(atom, levels) =>
      n(pending("precedence"), go(atom), n(pending("levels"),
        levels.map(l => n(pending("level"), opPairs(l)*))*))
    case GrammarDef.Phrase(inner, stops) =>
      n(pending("phrase"), go(inner), sl(words(stops)))
    case GrammarDef.Named(name, inner) => n(pending("named"), sl(name), go(inner))
    case GrammarDef.Ref(name)          => n(pending("use"), sl(name))
    case GrammarDef.Hole               => DetachedNode.leaf(pending("hole"))
  }
  def opPairs(ops: Map[String, String]): List[DetachedNode[NodeData]] =
    ops.toList.sortBy(_._1).map { case (sym, tag) =>
      n(pending("op"), sl(sym), DetachedNode.leaf(kindIds.get(tag)
        .map(NodeData.InternalNodeRef(_)).getOrElse(NodeData.StringData(tag))))
    }
  go(g)
}

private def words(s: Set[String]): String = s.toList.sorted.mkString(" ")

private def leafNode(l: LeafKind): DetachedNode[NodeData] = l match {
  case LeafKind.IntLit         => DetachedNode.leaf(pending("int"))
  case LeafKind.FloatLit       => DetachedNode.leaf(pending("float"))
  case LeafKind.HoleLeaf       => DetachedNode.leaf(pending("gap"))
  case LeafKind.Text           => DetachedNode.leaf(pending("text"))
  case LeafKind.RefLit(prefix) => n(pending("ref"), sl(prefix))
}

// Point each `use` at its `named` rule: the name leaf becomes an
// internal ref to the named node. Runs while constructs are still
// pending markers, so they're recognizable without imports.
private[langdef] def linkUses(
    doc: Document[NodeData], root: Int): Document[NodeData] = {
  val ids = doc.subtreeIds(root)
  def firstKid(id: Int) = doc.childrenOf(id).headOption
  def str(id: Int) = doc.getNode(id).map(_.data).collect {
    case NodeData.StringData(s) => s }
  def tagged(name: String) =
    ids.filter(id => doc.getNode(id).exists(_.data == pending(name)))
  val named = tagged("named").flatMap(id => firstKid(id).flatMap(str).map(_ -> id)).toMap
  tagged("use").flatMap(firstKid).foldLeft(doc) { (d, kid) =>
    str(kid).flatMap(named.get)
      .flatMap(t => updateNodeData(kid, NodeData.InternalNodeRef(t), d).toOption)
      .getOrElse(d)
  }
}
