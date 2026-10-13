package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.grammar.GTok
import opelan.foundation.language.{
  evalTree, exprGToks, formulasView, parseCellText, printTree,
  readLocExpr, sheetView}

// The code-side semantics each foundational language definition
// references. A LanguageDef names behaviors — lexers, views, cell
// parsers — but behavior lives in code while the demo bootstraps, so
// each foundational def carries an Impls bundle holding its own
// implementations. A def decoded from a document inherits the bundle of
// the foundational language whose type ref it claims (see implsFor in
// Defs.scala): editing lang: expression's grammar re-parses cells with
// the same lexer, and renamed views simply fail to resolve.

type BuiltinView = (Document[NodeData], Int) => DetachedNode[NodeData]
type BuiltinLexer = String => Vector[GTok]
type BuiltinCellParser = String => DetachedNode[NodeData]

case class Impls(
    lexers: Map[String, BuiltinLexer] = Map.empty,
    views: Map[String, BuiltinView] = Map.empty,
    cellParsers: Map[String, BuiltinCellParser] = Map.empty)

val exprImpls = Impls(
  lexers = Map("expr" -> exprGToks),
  views = Map(
    "expr-print" -> ((doc, id) => printTree(readLocExpr(doc, id))),
    "expr-eval"  -> ((doc, id) => evalTree(doc, readLocExpr(doc, id)))))

val sheetImpls = Impls(
  views = Map(
    "sheet-grid"     -> ((doc, id) => sheetView(doc, id)),
    "sheet-formulas" -> ((doc, id) => formulasView(doc, id))),
  cellParsers = Map("sheet-cell" -> parseCellText))

val docImpls = Impls(
  views = Map("def-view" -> ((doc, id) => defView(doc, id))))
