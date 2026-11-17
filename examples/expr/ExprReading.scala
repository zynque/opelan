package opelan.examples.expr

import opelan.foundation.document._
import opelan.foundation.langdef.{ViewContext, ambientRefs}
import opelan.foundation.langdef.doc.termRefs
import opelan.foundation.means.RuleSet
import opelan.foundation.means.engine.{Semantics, semanticsOf}

// What an expression view reads constructs by: a rule set and the
// term anchors its constructs carry. The host's resolved dialect
// (ViewContext.dialect) supplies both — an authored `a // b` in a
// stored lang: expression evaluates and prints, its anchors named by
// that very document — else the code-side definition.
case class ExprReading(
    rules: RuleSet, terms: Map[String, ExternalNodeReference]) {
  lazy val semantics: Semantics = semanticsOf(rules)

  def tag(term: String): NodeData =
    terms.get(term).map(NodeData.ExternalNodeRef(_))
      .getOrElse(NodeData.GapData(term))
}

lazy val codeExprReading: ExprReading = ExprReading(exprRules, exprTerms)

def exprReading(ctx: ViewContext): ExprReading =
  ctx.dialect.flatMap(d => d.syntax.flatMap(_.rules)
    .map(ExprReading(_, ambientRefs ++ termRefs(d))))
    .getOrElse(codeExprReading)
