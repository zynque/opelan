package opelan.foundation.langdef

import opelan.foundation.document._

// A language definition as data — the in-memory form of what a `lang:`
// document encodes: identity (title/name/description), schema, a
// compositional grammar, view bindings to named builtins, and a cell
// grammar. A def is interpreted into a Language by DefLanguage and
// encoded into a document by langDefToDetached; semantics named in it
// (lexer, views, builtin cell parsers) resolve through the builtins
// registry — honest scaffolding while behavior lives in code.
case class LanguageDef(
    typeRef: ExternalNodeReference,
    title: String,
    name: String,
    description: String,
    schema: List[NodeKind],
    syntax: Option[SyntaxDef],
    views: List[ViewBinding],
    cellParser: Option[CellParserDef])

// A node kind in the language's schema. Descriptive for now — nothing
// checks arity yet — but the doc encoding carries it. `ref` is the
// kind's identity: the address of its schema entry in the def document
// (`url@v#node`). Documents use the construct by that ref, so the name
// is only a label — renaming the entry renames every use. A def written
// in code has no refs until encoded (see kindRefs).
case class NodeKind(
    name: String, shape: NodeShape,
    ref: Option[ExternalNodeReference] = None)

enum NodeShape {
  case Leaf            // a data leaf (literal, ref, gap, label…)
  case Fixed(n: Int)   // a tag node with exactly n children
  case Any             // a tag node with variable children
}

// The text syntax: a named builtin lexicon — the character-level lexical
// rules the grammar's token kinds name — plus the grammar itself.
case class SyntaxDef(lexicon: String, grammar: GrammarDef)

// A derived view bound to a builtin: view "eval" → builtin "expr-eval".
case class ViewBinding(name: String, builtin: String)

// The grammar for projected cell editors — the text committed in a
// cell's box becomes a replacement subtree. A Language ref names
// *another language's grammar* (e.g. the sheet's cells parse with the
// expression language), so editing that language's definition document
// heals both surfaces.
enum CellParserDef {
  case Builtin(name: String)
  case Language(ref: ExternalNodeReference, fallback: CellFallback)
}

// What a cell commit produces when the text doesn't fit the grammar:
// Label keeps it as a string value (sheets), Hole keeps it as a gap.
enum CellFallback { case Label, Hole }

// A grammar as a combinator composition — the data twin of
// foundation/grammar. Named/Ref encode recursion (a precedence grammar
// its own parens refer back to); a doc encoding binds names the same
// way. Token leaves map through LeafKind — named, since how "42"
// becomes IntData(42) is semantics. Hole is the recovering read's
// residue: an unrecognizable grammar node becomes a production that
// never applies.
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
  case Hole
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
