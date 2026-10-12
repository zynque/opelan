package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.grammar._
import Expr._

// A total, recovering parser for the expression cell syntax, written as
// a combinator composition over generic tokens (foundation/grammar).
// Every input produces an Expr; text that fits no production is kept
// verbatim in Hole nodes, so typing continues to heal the tree rather
// than fail.
//
//   expr   := phrase(precedenceTable(atom, levels))
//   atom   := int | float | ref:N | '?' | ( expr ) | signum | junk
//   levels := ["*"->mul, "/"->div] then ["+"->add, "-"->sub]
//             (tightest first — the last level binds loosest)
//
// The combinator vocabulary is the point: the same grammar is written
// as data in langdef/Defs.scala — the interpretation must agree.

// Spans now come from the grammar package; keep the old name working.
type ExprSpan = Span
val ExprSpan = Span

// The parsed expression plus a span map: paths (child indexes from the
// expression root) to source ranges. Enables mapping a text edit onto
// the affected node later.
case class ExprParseResult(expr: Expr, spans: Map[List[Int], ExprSpan])

def parseExpr(input: String): ExprParseResult = {
  val r = exprGrammar(In(input, exprGToks(input)), 0)
  ExprParseResult(exprFromDetached(toDetached(r.tree)), spansOf(r.tree))
}

// The bare grammar — precedence levels over the atom choice. `parens`
// wraps it with a ")"-stopping phrase; the top level phrases it to EOF.
private def exprCore: Parser = precedenceTable(atom, opLevels)

private def exprGrammar: Parser = phrase(exprCore, Set.empty)

private val opLevels = List(
  Map("*" -> Expr.MulTag, "/" -> Expr.DivTag),
  Map("+" -> Expr.AddTag, "-" -> Expr.SubTag))

private def atom: Parser = choice(
  tok("int")(t => NodeData.IntData(t.text.toInt)),
  tok("float")(t => NodeData.FloatData(t.text.toDouble)),
  tok("ref")(t => NodeData.InternalNodeRef(t.text.drop(4).toInt)),
  tok("hole")(_ => NodeData.GapData("")),
  parens(exprCore),
  signum,
  junk(
    Set("op", "junk"),
    Set("int", "float", "ref", "junk", "hole")))
