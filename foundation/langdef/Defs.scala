package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.language.{Expr, ExprLanguage}

// The in-memory language definitions — what `lang:` documents will
// encode once the def-doc format lands (step 9). `exprDef` is the
// regression anchor: DefLanguage(exprDef) must be behaviorally
// identical to ExprLanguage.
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
  name = ExprLanguage.name,
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
  cellParser = None,
  doc = ExprLanguage.langDoc)
