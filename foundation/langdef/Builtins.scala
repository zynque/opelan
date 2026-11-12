package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.grammar.Lexicon
import opelan.foundation.langdef.syntax._
import opelan.foundation.langdef.view._

// The code-side semantics a language definition names. A LanguageDef
// references lexicons, views, and input parsers by name — behavior lives
// in code while the demo bootstraps — and each language carries an
// Impls bundle holding its own implementations (see the examples
// package for the concrete bundles). docImpls is the meta-language's:
// the "edit" and "summary" views that render a decoded definition.

type BuiltinView =
  (Document[NodeData], Int, ViewContext) => DetachedNode[NodeData]
type BuiltinInputParser = String => DetachedNode[NodeData]
// The finishing pass committed input content gets: structure text
// can't carry (a sheet's cell names → `ref` constructs) is
// materialized before the commit lands.
type CommitLink = DetachedNode[NodeData] => DetachedNode[NodeData]

case class Impls(
    lexicons: Map[String, Lexicon] = Map.empty,
    leaves: Map[String, LeafDef] = Map.empty,
    views: Map[String, BuiltinView] = Map.empty,
    inputParsers: Map[String, BuiltinInputParser] = Map.empty,
    // A word naming a reference the committed parse resolves later:
    // unparsed text mentioning one stays a healable gap rather than
    // falling back to a label (InputGrammar).
    refName: Option[String => Boolean] = None,
    commitLink: Option[CommitLink] = None)

// What a builtin view gets from the language rendering it: the view's
// settings as written in the def doc, the language's current input
// grammar — so a view can retry gaps against it (a gap whose text
// now parses renders healed) — and `interpretDef`, a way to run a
// decoded definition as a live language (the "edit" view checks worked
// examples through it; absent where no resolution context exists).
case class ViewContext(
    settings: Map[String, String] = Map.empty,
    parseInput: String => Option[DetachedNode[NodeData]] = _ => None,
    interpretDef: LanguageDef => Option[opelan.foundation.language.Language]
      = _ => None,
    // The rendered document's own URL, when the host knows it — a def
    // doc's URL is the identity of the language it defines, so decoding
    // it for interpretation needs it.
    docUrl: Option[String] = None)

val docImpls = Impls(
  views = Map(
    "def-view"     -> ((doc, id, ctx) => defView(doc, id, ctx)),
    "summary-view" -> ((doc, id, ctx) => defSummary(doc, id, ctx))),
  // "text": the committed string becomes the node's StringData — how a
  // def doc's projected inputs (rule lines and friends) write back.
  inputParsers = Map("text" -> (t => sl(t))))
