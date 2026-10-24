package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.grammar.Lexicon

// The code-side semantics a language definition names. A LanguageDef
// references lexicons, views, and input parsers by name — behavior lives
// in code while the demo bootstraps — and each language carries an
// Impls bundle holding its own implementations (see the examples
// package for the concrete bundles). docImpls is the meta-language's:
// the "def" view that renders a decoded definition.

type BuiltinView =
  (Document[NodeData], Int, ViewContext) => DetachedNode[NodeData]
type BuiltinInputParser = String => DetachedNode[NodeData]

case class Impls(
    lexicons: Map[String, Lexicon] = Map.empty,
    leaves: Map[String, LeafDef] = Map.empty,
    views: Map[String, BuiltinView] = Map.empty,
    inputParsers: Map[String, BuiltinInputParser] = Map.empty)

// What a builtin view gets from the language rendering it: the view's
// settings as written in the def doc, the language's current input
// grammar — so a view can retry holes against it (a hole whose text
// now parses renders healed) — and `interpretDef`, a way to run a
// decoded definition as a live language (the "def" view checks worked
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
  views = Map("def-view" -> ((doc, id, ctx) => defView(doc, id, ctx))),
  // "text": the committed string becomes the node's StringData — how a
  // def doc's projected inputs (rule lines and friends) write back.
  inputParsers = Map("text" -> (t => sl(t))))
