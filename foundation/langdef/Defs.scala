package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.language.Language

// The meta-language definition: the type all `lang:` documents carry.
// Its grammar isn't expressed as a GrammarDef — the def-doc format IS
// the codec (DefDocEncode/Decode) — so DocLanguage is a code-resident
// Language whose "def" view decodes the doc and prints the definition.
// Its own langDoc is a def doc describing that format: self-describing,
// and the regress stops here. Concrete languages (the expression and
// sheet examples) live alongside it in the examples package.
import GrammarDef._
import NodeShape._

val langDefTypeRef =
  ExternalNodeReference("opelan:languages/language", 0, 0)

val docLanguageDef = LanguageDef(
  typeRef = langDefTypeRef,
  title = "lang: language",
  name = "Language definitions",
  description = "the language in which lang: documents are written",
  schema = List(
    NodeKind("section", Any),      // name / schema / syntax / views / cells
    NodeKind("kind", Fixed(2)),    // schema entry: name + shape
    NodeKind("view", Fixed(2)),    // view entry: name + builtin
    NodeKind("language", Any),     // cell grammar by language ref
    NodeKind("builtin", Any),      // cell grammar by builtin name
    NodeKind("grammar", Any)),     // combinator-tagged subtrees
  syntax = None,
  views = List(ViewBinding("def", "def-view")),
  cellParser = None)

val DocLanguage: Language = new DefLanguage(docLanguageDef, docImpls)
