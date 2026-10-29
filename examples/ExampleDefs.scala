package opelan.examples

import opelan.foundation.document._
import opelan.foundation.langdef._
import opelan.foundation.langdef.syntax._
import opelan.foundation.language.Language

// The example language definitions as data — what their `lang:`
// documents encode. `exprDef`/`sheetDef` ARE the languages now:
// ExprLanguage/SheetLanguage are DefLanguage over these defs, so a
// stored lang: doc and the registry fallback run the same machinery.
// `langDefDoc` must round-trip them through `langDefFromDocument`.
// Each def is paired with the Impls bundle holding the code-side
// semantics it names.
import GrammarDef._
import NodeShape._
import LeafKind._

import opelan.foundation.means._
import opelan.foundation.means.engine.printViewWith
import opelan.foundation.means.text._

// The languages' identity refs — the URLs their `lang:` documents live
// at. Constants, not `ExprLanguage.typeRef`: the objects interpreting
// these defs would otherwise be read while the defs initialize.
val exprTypeRef =
  ExternalNodeReference("opelan:languages/int-expression", 0, 0)
val sheetTypeRef = ExternalNodeReference("opelan:languages/sheet", 0, 0)

private val mulDiv = Map("*" -> Expr.MulTag, "/" -> Expr.DivTag)
private val addSub = Map("+" -> Expr.AddTag, "-" -> Expr.SubTag)

// The expression grammar as data — the same composition ExprParse
// writes by hand, with "expr" named so parens recurses to the
// precedence level (not the phrase wrapper).
private val exprGrammarDef: GrammarDef = exprGrammarWith(List(mulDiv, addSub))

// The operand fallback the means rules compile over — just the
// recovering unrecognized run now that leaf tokens come from
// `where`-bound rules (`n means n where n: number`) and signed
// numbers are the `+n`/`-n` rules, all bound through exprImpls' leaf
// productions.
private val exprAtom: GrammarDef =
  Unrecognized(Set("op", "unrecognized"),
    Set("int", "float", "ref", "unrecognized", "hole"))

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
        Unrecognized(
          Set("op", "unrecognized"),
          Set("int", "float", "ref", "unrecognized", "hole")))),
      levels = levels)),
    stops = Set.empty)

val exprDef = LanguageDef(
  typeRef = exprTypeRef,
  title = "lang: expression",
  name = "Expressions",
  description = "the arithmetic-expression language",
  // Fully derived: where-bound leaf terms plus the construct terms
  // of construct meanings (DeriveVocabulary.scala). The encoded def
  // carries no vocabulary section — each construct's first meaning
  // occurrence is its declaration.
  vocabulary = vocabularyFrom(exprRules),
  syntax = Some(SyntaxDef("expr", exprAtom, Some(exprRules))),
  views = List(
    ViewBinding("print", "expr-print"),
    ViewBinding("eval", "expr-eval")),
  inputParser = None)

// The expression grammar in combinator form — the fixture for
// GrammarDef interpretation and its rule-diffing while both forms
// coexist (the def itself carries means rules now).
val exprCombinatorDef: LanguageDef =
  exprDef.copy(syntax = Some(SyntaxDef("expr", exprGrammarDef)))

// The demo's starting point for lang: expression: the `/` rule and its
// binding are missing, so the budget's per-person formula sits as a hole
// until the audience adds them to the definition document. The vocabulary
// still declares `div`, so term ids match exprDef's.
val demoExprDef = exprDef.copy(syntax =
  Some(SyntaxDef("expr", exprAtom, Some(withoutPattern(exprRules, "a / b")))))

// A rule set minus one pattern — its rule and every binding entry.
private def withoutPattern(set: RuleSet, text: String): RuleSet = {
  val key = readPattern(text).key
  set.copy(
    rules = set.rules.filterNot(_.pattern.key == key),
    tiers = set.tiers.map(t => Tier(t.patterns.filterNot(_.key == key))))
}

val sheetDef = LanguageDef(
  typeRef = sheetTypeRef,
  title = "lang: sheet",
  name = "Sheet",
  description = "the spreadsheet language — a grid of cells",
  vocabulary = List(
    NodeTerm("sheet", Any),
    NodeTerm("hrow", Any),
    NodeTerm("row", Any),
    NodeTerm("label", Leaf),
    NodeTerm("literal", Leaf),
    NodeTerm("ref", Leaf),
    NodeTerm("formula", Any)),
  syntax = None,
  views = List(
    ViewBinding("sheet", "sheet-grid", Map("currency" -> "$")),
    ViewBinding("formulas", "sheet-formulas")),
  // Cells parse with the expression language's grammar — by pinned
  // ref, so a new lang: expression version heals sheet cells once the
  // pin bumps to it.
  inputParser = Some(
    InputParserDef.Language(exprTypeRef, InputFallback.Label)))

// The code-side semantics each example language's def names — kept
// beside the defs that reference them.
val exprImpls = Impls(
  lexicons = Map("expr" -> exprLexicon),
  // The leaf productions `where` terms bind to — the named leaf
  // vocabulary. Every leaf `where` doesn't bind is an operand
  // production; bound ones are narrowing names (`where a, b: int`).
  // Each names which token kinds it accepts and their leaf data;
  // alternatives try in order (`number` accepts float then int).
  leaves = {
    val numbers = LeafDef(List("float" -> FloatLit, "int" -> IntLit))
    Map(
      "literal"      -> numbers,
      "number"       -> numbers,
      "whole number" -> LeafDef(List("int" -> IntLit)),
      "int"          -> LeafDef(List("int" -> IntLit)),
      "float"        -> LeafDef(List("float" -> FloatLit)),
      "ref"          -> LeafDef(List("ref" -> RefLit("ref:"))),
      "gap"          -> LeafDef(List("hole" -> HoleLeaf)))
  },
  views = Map(
    "expr-print" -> ((doc, id, _) =>
      printViewWith(exprRules, exprTag, exprViewLeaf)(doc, id)),
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
