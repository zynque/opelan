package opelan.foundation.langdef

import opelan.foundation.document._

// A language definition as data — the in-memory form of what a `lang:`
// document will encode (M2 step 9): schema, a compositional grammar,
// view bindings to named builtins, and the document itself. A def is
// interpreted into a Language by DefLanguage; semantics named in it
// (lexer, views, cell parser) resolve through the builtins registry —
// honest scaffolding while behavior lives in code.
case class LanguageDef(
    typeRef: ExternalNodeReference,
    name: String,
    schema: List[NodeKind],
    syntax: Option[SyntaxDef],
    views: List[ViewBinding],
    cellParser: Option[String],
    doc: Document[NodeData])

// A node kind in the language's schema. Descriptive for now — nothing
// checks arity yet — but the doc encoding carries it.
case class NodeKind(name: String, shape: NodeShape)

enum NodeShape {
  case Leaf            // a data leaf (literal, ref, gap, label…)
  case Fixed(n: Int)   // a tag node with exactly n children
  case Any             // a tag node with variable children
}

// The text syntax: a named builtin lexer plus the grammar it feeds.
case class SyntaxDef(lexer: String, grammar: GrammarDef)

// A derived view bound to a builtin: view "eval" → builtin "expr-eval".
case class ViewBinding(name: String, builtin: String)

// A grammar as a combinator composition — the data twin of
// foundation/grammar. Named/Ref encode recursion (a precedence grammar
// its own parens refer back to); a doc encoding binds names the same
// way. Token leaves map through LeafKind — named, since how "42"
// becomes IntData(42) is semantics.
enum GrammarDef {
  case Tok(kind: String, leaf: LeafKind)
  case Choice(alts: List[GrammarDef])
  case Parens(inner: GrammarDef)
  case Signum
  case Junk(start: Set[String], run: Set[String])
  case ChainLeft(ops: Map[String, String], atom: GrammarDef)
  case Precedence(atom: GrammarDef, levels: List[Map[String, String]])
  case Phrase(inner: GrammarDef, stops: Set[String])
  case Named(name: String, g: GrammarDef)
  case Ref(name: String)
}

// How a token's text becomes leaf node data — the named productions a
// grammar's Tok alternatives use.
enum LeafKind {
  case IntLit                   // IntData(text.toInt)
  case FloatLit                 // FloatData(text.toDouble)
  case RefLit(prefix: String)   // InternalNodeRef(text.drop(prefix).toInt)
  case HoleLeaf                 // GapData("")
  case Text                     // StringData(text)
}
