package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.langdef.doc.termRefs
import opelan.foundation.means.RuleSet
import opelan.foundation.means.engine.{Semantics, semanticsOf}

// What a view reads constructs by: a rule set and the term anchors its
// constructs carry — the host's resolved dialect supplies both
// (ViewContext.dialect), so an authored `a // b` in a stored lang:
// expression evaluates and prints, its anchors named by that very
// document — else a code-side definition supplies them.
case class RuleReading(
    rules: RuleSet, terms: Map[String, ExternalNodeReference]) {
  lazy val semantics: Semantics = semanticsOf(rules)

  def tag(term: String): NodeData =
    terms.get(term).map(NodeData.ExternalNodeRef(_))
      .getOrElse(NodeData.GapData(term))
}

// A def's reading: its rules plus the ambient registry under its own
// vocabulary — a declared term wins over a placeholder or foundation
// name. None when the def declares no rules.
def ruleReading(d: LanguageDef): Option[RuleReading] =
  d.syntax.flatMap(_.rules).map(
    RuleReading(_, ambientRefs ++ termRefs(d)))
