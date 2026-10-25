package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.means.Meaning

// The recovering read of a `lang:` document — the inverse of
// DefDocEncode. Constructs are recognized by identity (which
// lang: language vocabulary entry a node's import names), never by
// name, so a def doc whose meta constructs were renamed still decodes.
// Every section is optional and every read tolerant: missing children
// default, unrecognized grammar nodes become GrammarDef.Hole, and a def
// doc under construction keeps decoding (Never Broken applies to
// language definitions too).
//
// `version` is the version of `url` being read: terms' refs pin it, so
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
    vocabulary = r.section("vocabulary").toList.flatMap { sid =>
      r.entries(sid, "term").map(k => NodeTerm(
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

// A def's terms with their identities — and its rules' construct
// meanings pointed at them, its `use` operands pointed at the
// pattern's piece nodes: a decoded def has all three (the structured
// encoding writes refs, never names); a def written in code gets the
// ones its encoding assigns.
def withTermRefs(d: LanguageDef): LanguageDef = {
  val url = d.typeRef.documentUrl
  val doc = langDefDoc(d)
  val vocabulary =
    if (d.vocabulary.forall(_.ref.isDefined)) d.vocabulary
    else langDefFromDocument(url, doc).vocabulary
  val ids = vocabulary.flatMap(t => t.ref.map(t.name -> _.nodeId)).toMap
  // The encoding's piece ids, per rule — the `use` operands' link
  // targets.
  val ruleIds = contentId(doc).toList.flatMap(cid =>
    doc.childrenOf(cid).filter(metaConsOf(doc, _).contains("syntax"))
      .flatMap(s => doc.childrenOf(s)
        .filter(metaConsOf(doc, _).contains("rules")))
      .flatMap(rs => doc.childrenOf(rs)
        .filter(metaConsOf(doc, _).contains("rule"))))
  def refify(pieces: Map[String, Int])(m: Meaning): Meaning =
    if (Set("use", "source", "builtin")(m.tag))
      m.copy(arguments = m.arguments.map(a =>
        Meaning.nameOf(a).flatMap(pieces.get) match {
          case Some(t) =>
            a.copy(reference = Some(ExternalNodeReference(url, 0, t)))
          case None    => a
        }))
    else if (!Meaning.isConstruct(m)) m
    else m.copy(
      reference = ids.get(m.tag).map(ExternalNodeReference(url, 0, _))
        .orElse(m.reference),
      arguments = m.arguments.map(refify(pieces)))
  d.copy(
    vocabulary = vocabulary,
    syntax = d.syntax.map(s => s.copy(rules = s.rules.map(set =>
      set.copy(rules = set.rules.zipWithIndex.map { case (r, i) =>
        r.copy(
          id = ruleIds.lift(i),
          meaning = refify(ruleIds.lift(i)
            .map(pieceIdsFor(doc, _)).getOrElse(Map.empty))(r.meaning))
      })))))
}

// Term name → the construct's identity, for code that builds or reads
// documents in a defined language.
def termRefs(d: LanguageDef): Map[String, ExternalNodeReference] =
  withTermRefs(d).vocabulary.flatMap(t => t.ref.map(t.name -> _)).toMap
