package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.means._

// Tolerant readers over a `lang:` document — every lookup optional,
// every leaf read string-or-nothing. `root` is the definition's root:
// the content node of a typed def doc, or the document root for a bare
// outline. `cons` names the meta construct a node applies, by identity.
// `grammarFrom` decodes combinator subtrees case by case; its `seen`
// ancestor set guards the walk the way Document.preorder's visited set
// does — a malformed document can list a node as its own descendant.
private class DefDocReader(doc: Document[NodeData], root: Int) {

  def data(id: Int): Option[NodeData] = doc.getNode(id).map(_.data)
  def kids(id: Int): List[Int] = doc.childrenOf(id)
  def str(id: Int): Option[String] =
    data(id).collect { case NodeData.StringData(s) => s }
  def childStr(id: Int, i: Int): Option[String] =
    kids(id).lift(i).flatMap(str)

  def cons(id: Int): Option[String] = externalRefOf(doc, id).flatMap(metaNameOf)

  def section(name: String): Option[Int] = kids(root).find(cons(_).contains(name))

  def entries(id: Int, name: String): List[Int] = kids(id).filter(cons(_).contains(name))

  // A reference leaf read as the name of what it points at (an op's
  // kind, a use's rule); a leftover string is taken as the name itself.
  def refName(id: Option[Int]): String = id.flatMap(i => data(i)).flatMap {
    case NodeData.InternalNodeRef(t) => definitionName(doc, t)
    case NodeData.StringData(s)      => Some(s)
    case _                           => None
  }.getOrElse("")

  def shapeFrom(id: Option[Int]): NodeShape = id.flatMap(cons) match {
    case Some("leaf")  => NodeShape.Leaf
    case Some("fixed") => id.flatMap(i => kids(i).headOption).flatMap(data) match {
      case Some(NodeData.IntData(n)) => NodeShape.Fixed(n)
      case _                         => NodeShape.Any
    }
    case _ => NodeShape.Any
  }

  def grammarFrom(id: Int, seen: Set[Int] = Set.empty): GrammarDef =
    if (seen(id)) GrammarDef.Hole
    else grammarNode(id, seen + id)

  private def grammarNode(id: Int, seen: Set[Int]): GrammarDef = {
    def sub(i: Int) = kids(id).lift(i).map(grammarFrom(_, seen)).getOrElse(GrammarDef.Hole)
    cons(id) match {
      case Some("tok")    => GrammarDef.Tok(childStr(id, 0).getOrElse(""), leafFrom(kids(id).lift(1)))
      case Some("choice") => GrammarDef.Choice(kids(id).map(grammarFrom(_, seen)))
      case Some("parens") => GrammarDef.Parens(sub(0))
      case Some("signum") => GrammarDef.Signum
      case Some("junk")   => GrammarDef.Junk(setFrom(childStr(id, 0)), setFrom(childStr(id, 1)))
      case Some("chain")  => GrammarDef.ChainLeft(opsFrom(kids(id).lift(1)), sub(0))
      case Some("precedence") =>
        GrammarDef.Precedence(sub(0), kids(id).lift(1).toList.flatMap(kids).map(l => opsFrom(Some(l))))
      case Some("phrase") => GrammarDef.Phrase(sub(0), setFrom(childStr(id, 1)))
      case Some("named")  => GrammarDef.Named(childStr(id, 0).getOrElse(""), sub(1))
      case Some("use")    => GrammarDef.Ref(refName(kids(id).headOption))
      case _              => GrammarDef.Hole
    }
  }

  private def leafFrom(kid: Option[Int]): LeafKind = kid.flatMap(cons) match {
    case Some("int")   => LeafKind.IntLit
    case Some("float") => LeafKind.FloatLit
    case Some("gap")   => LeafKind.HoleLeaf
    case Some("ref")   => LeafKind.RefLit(kid.flatMap(childStr(_, 0)).getOrElse(""))
    case _             => LeafKind.Text
  }

  private def setFrom(s: Option[String]): Set[String] =
    s.map(_.split(" ").filter(_.nonEmpty).toSet).getOrElse(Set.empty)

  private def opsFrom(kid: Option[Int]): Map[String, String] =
    kid.toList.flatMap(kids).filter(cons(_).contains("op")).flatMap { opId =>
      childStr(opId, 0).map(_ -> refName(kids(opId).lift(1)))
    }.toMap

  // A `rules` section: each `rule` node's text through the recovering
  // rule reader (a malformed line becomes a hole rule), `binding` tiers
  // as pattern texts, `example` pairs.
  def ruleSetFrom(id: Int): RuleSet = RuleSet(
    rules = entries(id, "rule").map(k =>
      childStr(k, 0).map(readRuleOrHole).getOrElse(holeRule)),
    tiers = entries(id, "binding").flatMap(b =>
      entries(b, "tier").map(t =>
        Tier(kids(t).flatMap(str).map(readPattern)))),
    examples = entries(id, "example").flatMap(e =>
      childStr(e, 0).map(i => Example(i, childStr(e, 1).getOrElse("")))))

  def inputParserFrom(id: Int): Option[InputParserDef] =
    kids(id).headOption.flatMap { c =>
      cons(c) match {
        case Some("language") =>
          Some(InputParserDef.Language(
            kids(c).headOption.flatMap(externalRefOf(doc, _))
              .getOrElse(ExternalNodeReference("", 0, 0)),
            kids(c).lift(1).flatMap(cons) match {
              case Some("as-gap") => InputFallback.Hole
              case _              => InputFallback.Label
            }))
        case Some("builtin") => childStr(c, 0).map(InputParserDef.Builtin(_))
        case _               => None
      }
    }
}
