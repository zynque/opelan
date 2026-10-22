package opelan.foundation.langdef

import opelan.foundation.document._

// The recovering read of a `lang:` document — the inverse of
// DefDocEncode. Constructs are recognized by identity (which
// lang: language schema entry a node's import names), never by name, so
// a def doc whose meta constructs were renamed still decodes. Every
// section is optional and every read tolerant: missing children
// default, unrecognized grammar nodes become GrammarDef.Hole, and a def
// doc under construction keeps decoding (Never Broken applies to
// language definitions too).
//
// `version` is the version of `url` being read: kinds' refs pin it, so
// a construct added in a later version is addressable.
def langDefFromDocument(
    url: String, doc: Document[NodeData], version: Int = 0): LanguageDef = {
  // A seeded def doc is typed (type ref, imports, content); a hand-built
  // or damaged one may be a bare outline — read either.
  val root = if (isTyped(doc)) contentId(doc).getOrElse(doc.rootId)
    else doc.rootId
  val r = new DefDocReader(doc, root)
  val title = Store.labelOf(doc).getOrElse(url)

  LanguageDef(
    typeRef = ExternalNodeReference(url, 0, 0),
    title = title,
    name = r.section("name").flatMap(r.childStr(_, 0)).getOrElse(title),
    description = r.section("description").flatMap(r.childStr(_, 0)).getOrElse(""),
    schema = r.section("schema").toList.flatMap { sid =>
      r.entries(sid, "kind").map(k => NodeKind(
        r.childStr(k, 0).getOrElse(""),
        r.shapeFrom(r.kids(k).lift(1)),
        Some(ExternalNodeReference(url, version, k))))
    },
    syntax = r.section("syntax").map { sid =>
      SyntaxDef(
        r.childStr(sid, 0).getOrElse(""),
        r.kids(sid).lift(1).map(k => r.grammarFrom(k))
          .getOrElse(GrammarDef.Hole),
        r.entries(sid, "rules").headOption.map(r.ruleSetFrom))
    },
    views = r.section("views").toList.flatMap { vid =>
      r.entries(vid, "view").map(k => ViewBinding(
        r.childStr(k, 0).getOrElse(""),
        r.childStr(k, 1).getOrElse(""),
        r.entries(k, "setting").flatMap(s =>
          r.childStr(s, 0).map(_ -> r.childStr(s, 1).getOrElse(""))).toMap))
    },
    inputParser = r.section("inputs").flatMap(r.inputParserFrom))
}

// A def's kinds with their identities: a decoded def has them; a def
// written in code gets the ones its encoding assigns.
def withKindRefs(d: LanguageDef): LanguageDef =
  if (d.schema.forall(_.ref.isDefined)) d
  else d.copy(schema =
    langDefFromDocument(d.typeRef.documentUrl, langDefDoc(d)).schema)

// Kind name → the construct's identity, for code that builds or reads
// documents in a defined language.
def kindRefs(d: LanguageDef): Map[String, ExternalNodeReference] =
  withKindRefs(d).schema.flatMap(k => k.ref.map(k.name -> _)).toMap
