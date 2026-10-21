package opelan.examples

import opelan.foundation.document._
import opelan.foundation.langdef._
import opelan.foundation.language.Language

// The example language definitions as data — what their `lang:`
// documents encode. `exprDef`/`sheetDef` are the regression anchors:
// DefLanguage applied to them must be behaviorally identical to
// ExprLanguage/SheetLanguage, and `langDefDoc` must round-trip them
// through `langDefFromDocument`. Each def is paired with the Impls
// bundle holding the code-side semantics it names.
import GrammarDef._
import NodeShape._
import LeafKind._

private val mulDiv = Map("*" -> Expr.MulTag, "/" -> Expr.DivTag)
private val addSub = Map("+" -> Expr.AddTag, "-" -> Expr.SubTag)

// The expression grammar as data — the same composition ExprParse
// writes by hand, with "expr" named so parens recurses to the
// precedence level (not the phrase wrapper).
private val exprGrammarDef: GrammarDef = exprGrammarWith(List(mulDiv, addSub))

private def exprGrammarWith(levels: List[Map[String, String]]): GrammarDef =
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
      levels = levels)),
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
  syntax = Some(SyntaxDef("expr", exprGrammarDef)),
  views = List(
    ViewBinding("print", "expr-print"),
    ViewBinding("eval", "expr-eval")),
  inputParser = None)

// The demo's starting point for lang: expression: `/` is missing, so the
// budget's per-person formula sits as a hole until the audience adds the
// operator to the definition document. The schema still declares `div`,
// so kind ids match exprDef's.
val demoExprDef = exprDef.copy(syntax =
  Some(SyntaxDef("expr", exprGrammarWith(List(mulDiv - "/", addSub)))))

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
    ViewBinding("sheet", "sheet-grid", Map("currency" -> "$")),
    ViewBinding("formulas", "sheet-formulas")),
  // Cells parse with the expression language's grammar — by pinned
  // ref, so a new lang: expression version heals sheet cells once the
  // pin bumps to it.
  inputParser = Some(
    InputParserDef.Language(ExprLanguage.typeRef, InputFallback.Label)))

// The code-side semantics each example language's def names — kept
// beside the defs that reference them.

val exprImpls = Impls(
  lexicons = Map("expr" -> exprLexicon),
  views = Map(
    "expr-print" -> ((doc, id, _) => printTree(readLocExpr(doc, id))),
    "expr-eval"  -> ((doc, id, _) => evalTree(doc, readLocExpr(doc, id)))))

val sheetImpls = Impls(
  views = Map(
    "sheet-grid"     -> ((doc, id, ctx) => sheetView(doc, id, ctx)),
    "sheet-formulas" -> ((doc, id, ctx) => formulasView(doc, id, ctx))),
  inputParsers = Map("sheet-cell" -> parseCellText))

// The foundational languages, each paired with the implementations its
// def names. This list is the only built-in registry: a def decoded
// from a document inherits the impls of the language whose type ref it
// claims, so a stored lang: doc keeps working semantics.
val foundation: List[(LanguageDef, Impls)] = List(
  exprDef        -> exprImpls,
  sheetDef       -> sheetImpls,
  docLanguageDef -> docImpls)

def implsFor(d: LanguageDef): Impls =
  foundation.collectFirst {
    case (fd, i) if fd.typeRef.documentUrl == d.typeRef.documentUrl &&
                   fd.typeRef.nodeId == d.typeRef.nodeId => i
  }.getOrElse(Impls())
