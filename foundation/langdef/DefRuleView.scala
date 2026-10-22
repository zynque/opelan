package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.means.{printPattern, printRule, RuleSet}
import opelan.foundation.view.ViewTree._

// The rules section of the "def" view, rendered as projected inputs:
// each rule line, binding pattern, and example field is an `input`
// element bound to its text leaf's node id. A commit rewrites the leaf
// in place (the doc language's "text" input parser) and the next decode
// re-reads it — a malformed line reads as a hole rule, never an error.
// Falls back to None when the document has no `rules` node (a def
// decoded but never written as one), so callers can print instead.
private[langdef] def docRuleLines(
    doc: Document[NodeData], rootId: Int)
    : Option[List[DetachedNode[NodeData]]] =
  for {
    syn <- doc.childrenOf(rootId).find(docCons(doc, _).contains("syntax"))
    rs  <- doc.childrenOf(syn).find(docCons(doc, _).contains("rules"))
  } yield doc.childrenOf(rs).flatMap(ruleLine(doc, _))

// One `rules` entry as a line: a `rule` edits its text leaf; a
// `binding` shows its tiers as comma-separated pattern inputs; an
// `example` is input and expected side by side.
private def ruleLine(
    doc: Document[NodeData], id: Int): List[DetachedNode[NodeData]] =
  docCons(doc, id) match {
    case Some("rule") =>
      List(doc.childrenOf(id).headOption match {
        case Some(t) => input(t, txt(leafText(doc, t)))
        case None    => hole("rule")
      })
    case Some("binding") =>
      txt("binding:") +: doc.childrenOf(id)
        .filter(docCons(doc, _).contains("tier"))
        .map(t => flow((txt("  ") +: commaInputs(doc, t))*))
    case Some("example") =>
      List(flow((doc.childrenOf(id) match {
        case a :: b :: _ => List(txt("e.g. "), input(a, txt(leafText(doc, a))),
          txt(" means "), input(b, txt(leafText(doc, b))))
        case a :: _      => List(txt("e.g. "), input(a, txt(leafText(doc, a))))
        case _           => List(hole("example"))
      })*))
    case _ => Nil
  }

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
