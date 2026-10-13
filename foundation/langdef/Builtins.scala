package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.grammar.GTok
import opelan.foundation.language.{
  evalTree, exprGToks, formulasView, parseCellText, printTree,
  readLocExpr, sheetView}

// The named behaviors a language definition can bind — the part of a
// language that still lives in code. A builtin view interprets the
// typed document's content subtree into a view tree; a builtin lexer
// turns cell text into generic tokens; a builtin cell parser turns
// committed cell text into a replacement subtree.

type BuiltinView = (Document[NodeData], Int) => DetachedNode[NodeData]

val builtinViews: Map[String, BuiltinView] = Map(
  "expr-print"     -> ((doc, id) => printTree(readLocExpr(doc, id))),
  "expr-eval"      -> ((doc, id) => evalTree(doc, readLocExpr(doc, id))),
  "sheet-grid"     -> ((doc, id) => sheetView(doc, id)),
  "sheet-formulas" -> ((doc, id) => formulasView(doc, id)))

val builtinLexers: Map[String, String => Vector[GTok]] = Map(
  "expr" -> exprGToks)

val builtinCellParsers: Map[String, String => DetachedNode[NodeData]] = Map(
  "sheet-cell" -> parseCellText)
