package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.language.{Expr, ExprLanguage, SheetLanguage}

// The in-memory language definitions — what `lang:` documents encode.
// `exprDef`/`sheetDef` are the regression anchors: DefLanguage applied
// to them must be behaviorally identical to ExprLanguage/SheetLanguage,
// and `langDefDoc` must round-trip them through `langDefFromDocument`.
import GrammarDef._
import NodeShape._
import LeafKind._

private val mulDiv = Map("*" -> Expr.MulTag, "/" -> Expr.DivTag)
private val addSub = Map("+" -> Expr.AddTag, "-" -> Expr.SubTag)

// The expression grammar as data — the same composition ExprParse
// writes by hand, with "expr" named so parens recurses to the
// precedence level (not the phrase wrapper).
private val exprGrammar: GrammarDef =
  Phrase(
    Named("expr", Precedence(
      atom = Choice(List(
        Tok("int", IntLit),
        Tok("float", FloatLit),
        Tok("ref", RefLit("ref:")),
        Tok("hole", HoleLeaf),
        Parens(Ref("expr")),
        Signum,
        Junk(
          Set("op", "junk"),
          Set("int", "float", "ref", "junk", "hole")))),
      levels = List(mulDiv, addSub))),
    stops = Set.empty)

val exprDef = LanguageDef(
  typeRef = ExprLanguage.typeRef,
  title = "lang: expression",
  name = ExprLanguage.name,
  description = "the arithmetic-expression language",
  schema = List(
    NodeKind("literal", Leaf),
    NodeKind("ref", Leaf),
    NodeKind("gap", Leaf),
    NodeKind("add", Fixed(2)),
    NodeKind("sub", Fixed(2)),
    NodeKind("mul", Fixed(2)),
    NodeKind("div", Fixed(2))),
  syntax = Some(SyntaxDef("expr", exprGrammar)),
  views = List(
    ViewBinding("print", "expr-print"),
    ViewBinding("eval", "expr-eval")),
  cellParser = None)

val sheetDef = LanguageDef(
  typeRef = SheetLanguage.typeRef,
  title = "lang: sheet",
  name = SheetLanguage.name,
  description = "the spreadsheet language — a grid of cells",
  schema = List(
    NodeKind("sheet", Any),
    NodeKind("hrow", Any),
    NodeKind("row", Any),
    NodeKind("label", Leaf),
    NodeKind("literal", Leaf),
    NodeKind("ref", Leaf),
    NodeKind("formula", Any)),
  syntax = None,
  views = List(
    ViewBinding("sheet", "sheet-grid"),
    ViewBinding("formulas", "sheet-formulas")),
  // Cells parse with the expression language's grammar — by ref, so
  // editing lang: expression heals sheet cells too.
  cellParser = Some(
    CellParserDef.Language(ExprLanguage.typeRef, CellFallback.Label)))
