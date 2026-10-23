package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.means.Meaning

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
  val r = new DefDocReader(doc, root, url, version)
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

// A def's kinds with their identities — and its rules' construct
// meanings pointed at them: a decoded def has both (a meaning node's
// data is its kind entry's ref); a def written in code gets the ones
// its encoding assigns.
def withKindRefs(d: LanguageDef): LanguageDef = {
  val schema =
    if (d.schema.forall(_.ref.isDefined)) d.schema
    else langDefFromDocument(d.typeRef.documentUrl, langDefDoc(d)).schema
  val ids = schema.flatMap(k => k.ref.map(k.name -> _.nodeId)).toMap
  val url = d.typeRef.documentUrl
  def refify(m: Meaning): Meaning =
    if (!Meaning.isConstruct(m)) m
    else m.copy(
      reference = ids.get(m.tag).map(ExternalNodeReference(url, 0, _))
        .orElse(m.reference),
      arguments = m.arguments.map(refify))
  d.copy(
    schema = schema,
    syntax = d.syntax.map(s => s.copy(rules = s.rules.map(set =>
      set.copy(rules =
        set.rules.map(r => r.copy(meaning = refify(r.meaning))))))))}

// Kind name → the construct's identity, for code that builds or reads
// documents in a defined language.
def kindRefs(d: LanguageDef): Map[String, ExternalNodeReference] =
  withKindRefs(d).schema.flatMap(k => k.ref.map(k.name -> _)).toMap
