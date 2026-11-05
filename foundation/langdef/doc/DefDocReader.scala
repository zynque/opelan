package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._
import opelan.foundation.means._
import opelan.foundation.means.text._

// Tolerant readers over a `lang:` document — every lookup optional,
// every leaf read string-or-nothing. `root` is the definition's root:
// the content node of a typed def doc, or the document root for a bare
// outline. `cons` names the meta construct a node applies, by identity.
private class DefDocReader(
    doc: Document[NodeData], root: Int,
    url: String = "", version: Int = 0) {

  // Term name → its declaration node's id: the first meaning-position
  // node carrying that name (the def's derived vocabulary anchors).
  lazy val termIds: Map[String, Int] = deriveTermIds(doc, root)

  def data(id: Int): Option[NodeData] = doc.getNode(id).map(_.data)
  def children(id: Int): List[Int] = doc.childrenOf(id)
  def str(id: Int): Option[String] =
    data(id).collect { case NodeData.StringData(s) => s }
  def childStr(id: Int, i: Int): Option[String] =
    children(id).lift(i).flatMap(str)

  def cons(id: Int): Option[String] = externalRefOf(doc, id).flatMap(metaNameOf)

  def section(name: String): Option[Int] = children(root).find(cons(_).contains(name))

  def entries(id: Int, name: String): List[Int] = children(id).filter(cons(_).contains(name))

  def shapeFrom(id: Option[Int]): NodeShape = id.flatMap(cons) match {
    case Some("leaf")  => NodeShape.Leaf
    case Some("fixed") => id.flatMap(i => children(i).headOption).flatMap(data) match {
      case Some(NodeData.IntData(n)) => NodeShape.Fixed(n)
      case _                         => NodeShape.Any
    }
    case _ => NodeShape.Any
  }

  // A `rules` section: each `rule` node through the recovering reader
  // (RuleDocDecode — a malformed rule becomes a gap rule), `binding`
  // tiers as pattern texts, `example` pairs.
  def ruleSetFrom(id: Int): RuleSet = RuleSet(
    rules = entries(id, "rule").map(ruleFromDoc(doc, url, version, termIds, _)),
    tiers = entries(id, "binding").flatMap(b =>
      entries(b, "tier").map(t =>
        Tier(children(t).flatMap(str).map(readPattern)))),
    examples = entries(id, "example").flatMap(e =>
      childStr(e, 0).map(i => Example(i, childStr(e, 1).getOrElse("")))))

  def inputParserFrom(id: Int): Option[InputParserDef] =
    children(id).headOption.flatMap { c =>
      cons(c) match {
        case Some("language") =>
          Some(InputParserDef.Language(
            children(c).headOption.flatMap(externalRefOf(doc, _))
              .getOrElse(ExternalNodeReference("", 0, 0)),
            children(c).lift(1).flatMap(cons) match {
              case Some("as-gap") => InputFallback.Gap
              case _              => InputFallback.Label
            }))
        case Some("builtin") => childStr(c, 0).map(InputParserDef.Builtin(_))
        case _               => None
      }
    }
}
