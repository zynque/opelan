package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._
import opelan.foundation.langdef.syntax.vocabularyFrom
import opelan.foundation.means.Meaning
import opelan.foundation.means.engine.foundationDefs

// The recovering read of a `lang:` document — the inverse of
// DefDocEncode. Constructs are recognized by identity (which
// lang: language vocabulary entry a node's import names), never by
// name, so a def doc whose meta constructs were renamed still decodes.
// Every section is optional and every read tolerant: missing children
// default, unrecognized content degrades, and a def
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
    // The vocabulary: terms the rules declare — `where`-bound leaf
    // terms and construct-application pattern declarations, each
    // entry's ref its declaration node's id through termIds — plus
    // the `vocabulary` section's entries (a rule-less def's declared
    // terms, or non-foundational imports), and — for a rule-bearing
    // def — the ambient foundation: foundational terms resolve by
    // name without a stored entry, so a decoded `add` is the
    // foundational `add`.
    vocabulary = {
      val derived = r.section("syntax").toList
        .flatMap(sid => r.entries(sid, "rules"))
        .headOption.toList.flatMap(rs =>
          vocabularyFrom(r.ruleSetFrom(rs)).map(t =>
            t.copy(ref = t.ref.orElse(r.termIds.get(t.name)
              .map(ExternalNodeReference(url, version, _))))))
      val section = r.section("vocabulary").toList
        .flatMap(sid => r.entries(sid, "term").map(r.termEntry))
      val named = (derived ++ section).map(_.name).toSet
      val floor =
        if (derived.isEmpty) Nil
        else foundationDefs.map(d => NodeTerm(
          d.name, NodeShape.Fixed(d.arity), Some(d.ref)))
          .filterNot(t => named(t.name))
      derived ++ section.filterNot(t => derived.exists(_.name == t.name)) ++ floor
    },
    syntax = r.section("syntax").map { sid =>
      SyntaxDef(
        r.childStr(sid, 0).getOrElse(""),
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
    if (d.vocabulary.forall(t =>
        t.shape == NodeShape.Leaf || t.ref.isDefined)) d.vocabulary
    else langDefFromDocument(url, doc).vocabulary
  // Term name → its full ref — an imported term names a node of
  // another document, so the ref is kept whole, not re-anchored here.
  val ids = vocabulary.flatMap(t => t.ref.map(t.name -> _)).toMap
  // The encoding's piece ids, per rule — the `use` operands' link
  // targets.
  val ruleIds = contentId(doc).toList.flatMap(cid =>
    doc.childrenOf(cid).filter(metaConsOf(doc, _).contains("syntax"))
      .flatMap(s => doc.childrenOf(s)
        .filter(metaConsOf(doc, _).contains("rules")))
      .flatMap(rs => doc.childrenOf(rs)
        .filter(metaConsOf(doc, _).contains("rule"))))
  def refify(pieces: Map[String, Int])(m: Meaning): Meaning =
    if (Set("use", "source", "builtin", "literal")(m.tag))
      m.copy(arguments = m.arguments.map(a =>
        Meaning.nameOf(a).flatMap(pieces.get) match {
          case Some(t) =>
            a.copy(reference = Some(ExternalNodeReference(url, 0, t)))
          case None    =>
            if (Meaning.nameOf(a).isDefined) a else refify(pieces)(a)
        }))
    else if (!Meaning.isConstruct(m)) m
    else m.copy(
      reference = ids.get(m.tag).orElse(m.reference),
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

// Term name → the construct's vocabulary-entry identity inside this
// def — references meanings use and renames retarget. The
// *content*-side map (which construct ref a parsed or built node
// carries) is `DefLanguage.termTags`, where foundational names
// resolve to the foundation's own refs.
def termRefs(d: LanguageDef): Map[String, ExternalNodeReference] =
  withTermRefs(d).vocabulary.flatMap(t => t.ref.map(t.name -> _)).toMap
