package opelan.foundation.langdef.view

import opelan.foundation.document._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc._
import opelan.foundation.means.{
  meaningTree, printPattern, printRule, printWith, Meaning, Rule, RuleSet}
import opelan.foundation.view.ViewTree._

// The rules section of the "def" view, rendered as projected inputs:
// rule lines bind to their `rule` node — a commit rewrites it as
// pattern + meaning nodes through commitMetaInput — while binding
// patterns and example fields bind to their text leaves. A malformed
// line reads as a hole rule, never an error.
// Falls back to None when the document has no `rules` node (a def
// decoded but never written as one), so callers can print instead.
// `check` runs a worked example — input text, expected text — and
// returns a failure detail the line shows as a hole.
private[langdef] def docRuleLines(
    doc: Document[NodeData], rootId: Int, d: LanguageDef,
    check: (String, String) => Option[String])
    : Option[List[DetachedNode[NodeData]]] =
  for {
    syn <- doc.childrenOf(rootId).find(docCons(doc, _).contains("syntax"))
    rs  <- doc.childrenOf(syn).find(docCons(doc, _).contains("rules"))
  } yield doc.childrenOf(rs).flatMap(ruleLine(doc, _, d, check))

// One `rules` entry as a line: a `rule` edits its text — a text-form
// rule shows its leaf, a structured one its canonical spelling — and
// the input binds to the rule node itself, so a commit rewrites it as
// pattern + meaning nodes (commitMetaInput). A `binding` shows its
// tiers as comma-separated pattern inputs; an `example` is input and
// expected side by side, checked live — a mismatch trails a hole
// saying what the input read as.
private def ruleLine(
    doc: Document[NodeData], id: Int, d: LanguageDef,
    check: (String, String) => Option[String])
    : List[DetachedNode[NodeData]] =
  docCons(doc, id) match {
    case Some("rule") =>
      List(doc.childrenOf(id).headOption match {
        case Some(t)
            if doc.getNode(t).exists(
              _.data.isInstanceOf[NodeData.StringData]) =>
          input(id, txt(leafText(doc, t)))
        case Some(_) =>
          input(id, txt(committedRuleText(d, ruleFromDoc(doc, "", 0, id))))
        case None    => hole("rule")
      })
    case Some("binding") =>
      txt("binding:") +: doc.childrenOf(id)
        .filter(docCons(doc, _).contains("tier"))
        .map(t => flow((txt("  ") +: commaInputs(doc, t))*))
    case Some("example") =>
      List(flow((doc.childrenOf(id) match {
        case a :: b :: _ =>
          val (inText, outText) = (leafText(doc, a), leafText(doc, b))
          List(txt("e.g. "), input(a, txt(inText)),
            txt(" means "), input(b, txt(outText))) ++
            check(inText, outText).map(t => List(txt(" "), hole(t)))
              .getOrElse(Nil)
        case a :: _      => List(txt("e.g. "), input(a, txt(leafText(doc, a))))
        case _           => List(hole("example"))
      })*))
    case _ => Nil
  }

// A stored rule's line text: `printRule`'s canonical spelling, except
// a meaning carrying `quote` terms — concrete content only a phrase
// desugars to — which projects back through the language's own
// printer, so `a % means a / 100` reads as authored, not `div(a, 100)`.
private def committedRuleText(d: LanguageDef, r: Rule): String =
  (for {
    set <- d.syntax.flatMap(_.rules) if hasQuote(r.meaning)
  } yield printPattern(r.pattern) + " means " +
    printWith(set, termTagOf(d), meaningLeaf)(
      meaningTree(r.meaning, termTagOf(d))) +
    whereText(r)).getOrElse(printRule(r))

private def hasQuote(m: Meaning): Boolean =
  m.tag == "quote" || m.arguments.exists(hasQuote)

private def meaningLeaf(data: NodeData): String = data match {
  case NodeData.StringData(s) => s
  case other                  => NodeDataText.show(other)
}

private def whereText(r: Rule): String =
  r.where.map(c => c.placeholders.mkString(", ") + ": " + c.term)
    .mkString(" where ", "; ", "")

private def commaInputs(
    doc: Document[NodeData], tierId: Int): List[DetachedNode[NodeData]] =
  doc.childrenOf(tierId).map(p => input(p, txt(leafText(doc, p))))
    .flatMap(List(_, txt(", "))).dropRight(1) match {
      case Nil  => List(txt(""))
      case kids => kids
    }

// A rule set printed rather than edited — the fallback when the doc
// carries no `rules` node (e.g. a def decoded from an older doc) and
// for surfaces that only render text.
private[langdef] def ruleLines(set: RuleSet): List[DetachedNode[NodeData]] =
  set.rules.map(r => txt(printRule(r))) ++
    (if (set.tiers.isEmpty) Nil
     else txt("binding:") +: set.tiers.map(t =>
       txt("  " + t.patterns.map(printPattern).mkString(", "))))
