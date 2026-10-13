package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.Detached._

// A LanguageDef as a document: a typed outline (type = the
// language-definition language, langDefTypeRef) whose content subtree
// carries the definition as data. The grammar is encoded as
// combinator-shaped subtrees — each GrammarDef case a tagged node — so
// a language doc is both readable and interpretable. Sets and op maps
// sort before encoding so the bytes (and their persisted text) are
// deterministic.
//
//   lang: expression                 root label (Store.labelOf)
//     "the arithmetic-expression language"
//     name    "Expressions"
//     schema  kind ("literal" "leaf") kind ("add" "fixed 2") ...
//     syntax  "expr"                   builtin lexer name
//       grammar nodes …
//     views   view ("print" "expr-print") ...
//     cells   language <ext ref> "label"   (or: builtin "sheet-cell")
def langDefDoc(d: LanguageDef): Document[NodeData] =
  makeTyped(langDefTypeRef, NodeData.StringData(d.title), langDefToDetached(d))

def langDefToDetached(d: LanguageDef): DetachedNode[NodeData] =
  n(s(d.title), List(
    Some(sl(d.description)),
    Some(n(s("name"), sl(d.name))),
    Some(schemaNode(d.schema)),
    d.syntax.map(syntaxNode),
    Some(viewsNode(d.views)),
    d.cellParser.map(cellsNode)).flatten*)

private def schemaNode(ks: List[NodeKind]): DetachedNode[NodeData] =
  n(s("schema"), ks.map(k =>
    n(s("kind"), sl(k.name), sl(shapeText(k.shape))))*)

private def shapeText(sh: NodeShape): String = sh match {
  case NodeShape.Leaf     => "leaf"
  case NodeShape.Any      => "any"
  case NodeShape.Fixed(n) => s"fixed $n"
}

private def syntaxNode(sd: SyntaxDef): DetachedNode[NodeData] =
  n(s("syntax"), sl(sd.lexer), grammarNode(sd.grammar))

private def viewsNode(vs: List[ViewBinding]): DetachedNode[NodeData] =
  n(s("views"), vs.map(v =>
    n(s("view"), sl(v.name), sl(v.builtin)))*)

private def cellsNode(cp: CellParserDef): DetachedNode[NodeData] =
  n(s("cells"), cp match {
    case CellParserDef.Builtin(name) =>
      n(s("builtin"), sl(name))
    case CellParserDef.Language(ref, fb) =>
      n(s("language"), erl(ref), sl(fb match {
        case CellFallback.Label => "label"
        case CellFallback.Hole  => "hole"
      }))
  })

// A grammar combinator as a node subtree. The sub-grammar comes first
// (parens inner, chain atom, precedence atom, phrase inner, named's
// body) with parameters after — matching how the combinators read.
def grammarNode(g: GrammarDef): DetachedNode[NodeData] = g match {
  case GrammarDef.Tok(kind, leaf) =>
    n(s("tok"), sl(kind), leafNode(leaf))
  case GrammarDef.Choice(alts) =>
    n(s("choice"), alts.map(grammarNode)*)
  case GrammarDef.Parens(inner) =>
    n(s("parens"), grammarNode(inner))
  case GrammarDef.Signum =>
    n(s("signum"))
  case GrammarDef.Junk(start, run) =>
    n(s("junk"), sl(start.toList.sorted.mkString(" ")),
      sl(run.toList.sorted.mkString(" ")))
  case GrammarDef.ChainLeft(ops, atom) =>
    n(s("chain"), grammarNode(atom), opsNode(ops))
  case GrammarDef.Precedence(atom, levels) =>
    n(s("precedence"), grammarNode(atom),
      n(s("levels"),
        levels.map(l => n(s("level"), opPairs(l)*))*))
  case GrammarDef.Phrase(inner, stops) =>
    n(s("phrase"), grammarNode(inner),
      sl(stops.toList.sorted.mkString(" ")))
  case GrammarDef.Named(name, inner) =>
    n(s("named"), sl(name), grammarNode(inner))
  case GrammarDef.Ref(name) =>
    n(s("ref"), sl(name))
  case GrammarDef.Hole =>
    n(s("hole"))
}

private def opsNode(ops: Map[String, String]): DetachedNode[NodeData] =
  n(s("ops"), opPairs(ops)*)

private def opPairs(ops: Map[String, String]): List[DetachedNode[NodeData]] =
  ops.toList.sortBy(_._1).map { case (sym, tag) =>
    n(s("op"), sl(sym), sl(tag)) }

private def leafNode(l: LeafKind): DetachedNode[NodeData] = l match {
  case LeafKind.IntLit         => sl("int")
  case LeafKind.FloatLit       => sl("float")
  case LeafKind.HoleLeaf       => sl("hole")
  case LeafKind.Text           => sl("text")
  case LeafKind.RefLit(prefix) => n(s("ref"), sl(prefix))
}
