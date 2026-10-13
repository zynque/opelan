package opelan.foundation.langdef

import opelan.foundation.document._

// Tolerant readers over a `lang:` document's outline — every lookup
// optional, every leaf read string-or-nothing. `root` is the outline's
// root: the content node of a typed def doc, or the document root for
// a bare outline (as a hand-built or damaged doc may be).
// `grammarFrom` decodes combinator-shaped subtrees case by case; its
// `seen` ancestor set guards the walk the way Document.preorder's
// visited set does — a malformed document can list a node as its own
// descendant.
private class DefDocReader(doc: Document[NodeData], root: Int) {

  def data(id: Int): Option[NodeData] = doc.getNode(id).map(_.data)
  def kids(id: Int): List[Int] = doc.childrenOf(id)
  def str(id: Int): Option[String] =
    data(id).collect { case NodeData.StringData(s) => s }
  def childStr(id: Int, i: Int): Option[String] =
    kids(id).lift(i).flatMap(str)

  def section(tag: String): Option[Int] =
    kids(root).find(id => data(id).contains(NodeData.StringData(tag)))

  // A bare leaf child of the root holding a string is prose — the
  // description. (The leaf check alone isn't enough: an untyped doc's
  // root can carry a reference leaf too.)
  def description: String =
    kids(root).collectFirst {
      case id if kids(id).isEmpty => str(id)
    }.flatten.getOrElse("")

  def rootTitle(url: String): String =
    data(root).collect { case NodeData.StringData(t) => t }
      .getOrElse(url)

  def grammarFrom(id: Int, seen: Set[Int] = Set.empty): GrammarDef =
    if (seen(id)) GrammarDef.Hole
    else grammarNode(id, seen + id)

  private def grammarNode(id: Int, seen: Set[Int]): GrammarDef =
    data(id).collect { case NodeData.StringData(tag) => tag } match {
      case Some("tok") =>
        GrammarDef.Tok(childStr(id, 0).getOrElse(""), leafFrom(kids(id).lift(1)))
      case Some("choice") =>
        GrammarDef.Choice(kids(id).map(k => grammarFrom(k, seen)))
      case Some("parens") =>
        GrammarDef.Parens(kids(id).headOption.map(k => grammarFrom(k, seen))
          .getOrElse(GrammarDef.Hole))
      case Some("signum") => GrammarDef.Signum
      case Some("junk") =>
        GrammarDef.Junk(setFrom(childStr(id, 0)), setFrom(childStr(id, 1)))
      case Some("chain") =>
        GrammarDef.ChainLeft(opsFrom(kids(id).lift(1)),
          kids(id).headOption.map(k => grammarFrom(k, seen))
            .getOrElse(GrammarDef.Hole))
      case Some("precedence") =>
        GrammarDef.Precedence(
          kids(id).headOption.map(k => grammarFrom(k, seen))
            .getOrElse(GrammarDef.Hole),
          levelsFrom(kids(id).lift(1)))
      case Some("phrase") =>
        GrammarDef.Phrase(
          kids(id).headOption.map(k => grammarFrom(k, seen))
            .getOrElse(GrammarDef.Hole),
          setFrom(childStr(id, 1)))
      case Some("named") =>
        GrammarDef.Named(childStr(id, 0).getOrElse(""),
          kids(id).lift(1).map(k => grammarFrom(k, seen))
            .getOrElse(GrammarDef.Hole))
      case Some("ref") =>
        GrammarDef.Ref(childStr(id, 0).getOrElse(""))
      case _ => GrammarDef.Hole
    }

  private def leafFrom(kid: Option[Int]): LeafKind =
    kid.flatMap(str) match {
      case Some("int")   => LeafKind.IntLit
      case Some("float") => LeafKind.FloatLit
      case Some("hole")  => LeafKind.HoleLeaf
      case Some("ref")   =>
        LeafKind.RefLit(kid.flatMap(id => childStr(id, 0)).getOrElse(""))
      case _             => LeafKind.Text
    }

  private def setFrom(s: Option[String]): Set[String] =
    s.map(_.split(" ").filter(_.nonEmpty).toSet).getOrElse(Set.empty)

  private def opsFrom(kid: Option[Int]): Map[String, String] =
    kid.toList.flatMap(kids).flatMap { opId =>
      for { sym <- childStr(opId, 0); tag <- childStr(opId, 1) }
        yield sym -> tag
    }.toMap

  private def levelsFrom(kid: Option[Int]): List[Map[String, String]] =
    kid.toList.flatMap(kids).map(l => opsFrom(Some(l)))

  def cellParserFrom(id: Int): Option[CellParserDef] = {
    val cs = kids(id)
    cs.find(c => data(c).contains(NodeData.StringData("language")))
      .map { langId =>
        val ref = kids(langId).headOption
          .flatMap(rid => data(rid).collect {
            case NodeData.ExternalNodeRef(r) => r })
          .getOrElse(ExternalNodeReference("", 0, 0))
        val fb = childStr(langId, 1) match {
          case Some("hole") => CellFallback.Hole
          case _            => CellFallback.Label
        }
        CellParserDef.Language(ref, fb)
      }
      .orElse(cs.find(c => data(c).contains(NodeData.StringData("builtin")))
        .flatMap(bid => childStr(bid, 0).map(CellParserDef.Builtin(_))))
  }
}
