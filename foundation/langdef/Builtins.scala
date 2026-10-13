package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.grammar.GTok

// The code-side semantics a language definition names. A LanguageDef
// references lexers, views, and cell parsers by name — behavior lives
// in code while the demo bootstraps — and each language carries an
// Impls bundle holding its own implementations (see the examples
// package for the concrete bundles). docImpls is the meta-language's:
// the "def" view that renders a decoded definition.

type BuiltinView = (Document[NodeData], Int) => DetachedNode[NodeData]
type BuiltinLexer = String => Vector[GTok]
type BuiltinCellParser = String => DetachedNode[NodeData]

case class Impls(
    lexers: Map[String, BuiltinLexer] = Map.empty,
    views: Map[String, BuiltinView] = Map.empty,
    cellParsers: Map[String, BuiltinCellParser] = Map.empty)

val docImpls = Impls(
  views = Map("def-view" -> ((doc, id) => defView(doc, id))))
