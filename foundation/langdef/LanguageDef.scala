package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.means.RuleSet

// A language definition as data — the in-memory form of what a `lang:`
// document encodes: identity (title/name/description), vocabulary, a
// compositional grammar, view bindings to named builtins, and an input
// grammar. A def is interpreted into a Language by DefLanguage and
// encoded into a document by langDefToDetached; semantics named in it
// (lexer, views, builtin input parsers) resolve through the builtins
// registry — honest scaffolding while behavior lives in code.
case class LanguageDef(
    typeRef: ExternalNodeReference,
    title: String,
    name: String,
    description: String,
    vocabulary: List[NodeTerm],
    syntax: Option[SyntaxDef],
    views: List[ViewBinding],
    inputParser: Option[InputParserDef])

// A term the language's documents can hold — one `term(name, shape)`
// entry in the def's vocabulary. Descriptive for now — nothing checks
// arity yet — but the doc encoding carries it. `ref` is the term's
// identity: the address of its entry in the def document
// (`url@v#node`). Documents use the construct by that ref, so the name
// is only a label — renaming the entry renames every use. A def written
// in code has no refs until encoded (see termRefs).
case class NodeTerm(
    name: String, shape: NodeShape,
    ref: Option[ExternalNodeReference] = None)

enum NodeShape {
  case Leaf            // a data leaf (literal, ref, gap, label…)
  case Fixed(n: Int)   // a tag node with exactly n children
  case Any             // a tag node with variable children
}

// The text syntax: a named builtin lexicon — the character-level lexical
// rules the grammar's token kinds name — plus the grammar itself, in one
// of two forms. With `rules` unset, `grammar` is the whole combinator
// composition. With `rules` set, the means rules compile over `grammar`
// as the operand atom — the productions rules don't yet express (leaf
// tokens, the signum, the junk fallback) until `where`-terms bind
// placeholders to a lexical rule vocabulary.
case class SyntaxDef(
    lexicon: String,
    grammar: GrammarDef,
    rules: Option[RuleSet] = None)

// A derived view bound to a builtin: view "eval" → builtin "expr-eval",
// with optional settings the builtin reads (the sheet's currency).
case class ViewBinding(
    name: String, builtin: String,
    settings: Map[String, String] = Map.empty)

// The grammar for projected inputs — the text committed in an input's
// box becomes a replacement subtree. A Language ref names
// *another language's grammar* (e.g. the sheet's cells parse with the
// expression language), so editing that language's definition document
// heals both surfaces.
enum InputParserDef {
  case Builtin(name: String)
  case Language(ref: ExternalNodeReference, fallback: InputFallback)
}

// What an input commit produces when the text doesn't fit the grammar:
// Label keeps it as a string value (sheets), Hole keeps it as a gap.
enum InputFallback { case Label, Hole }

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
  case HoleLeaf                 // GapData(text) — the hole keeps its source
  case Text                     // StringData(text)
}
