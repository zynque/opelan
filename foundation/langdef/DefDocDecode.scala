package opelan.foundation.langdef

import opelan.foundation.document._

// The recovering read of a `lang:` document — the inverse of
// DefDocEncode. Every section is optional and every read tolerant:
// missing children default, unrecognized grammar nodes become
// GrammarDef.Hole, and a def doc under construction keeps decoding
// (Never Broken applies to language definitions too).
def langDefFromDocument(url: String, doc: Document[NodeData]): LanguageDef = {
  val r = new DefDocReader(doc)
  val title = r.rootTitle(url)

  LanguageDef(
    typeRef = ExternalNodeReference(url, 0, 0),
    title = title,
    name = r.section("name").flatMap(r.childStr(_, 0)).getOrElse(title),
    description = r.description,
    schema = r.section("schema").toList.flatMap { sid =>
      r.kids(sid).filter(k =>
          r.data(k).contains(NodeData.StringData("kind")))
        .map(k => NodeKind(
          r.childStr(k, 0).getOrElse(""),
          shapeFrom(r.childStr(k, 1))))
    },
    syntax = r.section("syntax").map { sid =>
      SyntaxDef(
        r.childStr(sid, 0).getOrElse(""),
        r.kids(sid).lift(1).map(k => r.grammarFrom(k))
          .getOrElse(GrammarDef.Hole))
    },
    views = r.section("views").toList.flatMap { vid =>
      r.kids(vid).filter(k =>
          r.data(k).contains(NodeData.StringData("view")))
        .map(k => ViewBinding(
          r.childStr(k, 0).getOrElse(""),
          r.childStr(k, 1).getOrElse("")))
    },
    cellParser = r.section("cells").flatMap(r.cellParserFrom))
}

private def shapeFrom(s: Option[String]): NodeShape = s match {
  case Some("leaf")                  => NodeShape.Leaf
  case Some("any")                   => NodeShape.Any
  case Some(s) if s.startsWith("fixed ") =>
    s.drop(6).toIntOption.map(NodeShape.Fixed(_)).getOrElse(NodeShape.Any)
  case _                             => NodeShape.Any
}
