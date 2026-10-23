package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.language.Language

// The meta-language definition: the type all `lang:` documents carry.
// Its grammar isn't expressed as a GrammarDef — the def-doc format IS
// the codec (DefDocEncode/Decode) — so DocLanguage is a code-resident
// Language whose "def" view decodes the doc and prints the definition.
// Its own langDoc is a def doc describing that format: self-describing,
// and the regress stops here — its vocabulary entries are the constructs
// every def doc (itself included) imports. Concrete languages (the
// expression and sheet examples) live alongside it in the examples
// package.
import NodeShape._

val langDefTypeRef =
  ExternalNodeReference("opelan:languages/language", 0, 0)

// The meta constructs, in vocabulary order. Names are unique so code can
// key on them; documents never store them — they import the entries.
private[langdef] val metaTerms: List[NodeTerm] = List(
  NodeTerm("title", Fixed(1)),     // document-structure: title's text
  NodeTerm("imports", Any),        // document-structure: the dependencies
  NodeTerm("definition", Any),     // content root: the sections below
  NodeTerm("description", Fixed(1)),
  NodeTerm("name", Fixed(1)),
  NodeTerm("vocabulary", Any),
  NodeTerm("syntax", Any),         // builtin lexicon name + grammar (+ rules)
  NodeTerm("views", Any),
  NodeTerm("inputs", Fixed(1)),
  NodeTerm("term", Fixed(2)),      // vocabulary entry: name + shape
  NodeTerm("view", Fixed(2)),      // view entry: name + builtin
  NodeTerm("language", Fixed(2)),  // input grammar by language ref + fallback
  NodeTerm("builtin", Fixed(1)),   // input grammar by builtin name
  NodeTerm("leaf", Fixed(0)),      // shapes
  NodeTerm("any", Fixed(0)),
  NodeTerm("fixed", Fixed(1)),
  NodeTerm("as-label", Fixed(0)),  // input fallbacks
  NodeTerm("as-gap", Fixed(0)),
  NodeTerm("tok", Fixed(2)),       // grammar combinators
  NodeTerm("choice", Any),
  NodeTerm("parens", Fixed(1)),
  NodeTerm("signum", Fixed(0)),
  NodeTerm("junk", Fixed(2)),
  NodeTerm("chain", Fixed(2)),
  NodeTerm("ops", Any),
  NodeTerm("op", Fixed(2)),        // symbol + ref to the term it builds
  NodeTerm("precedence", Fixed(2)),
  NodeTerm("levels", Any),
  NodeTerm("level", Any),
  NodeTerm("phrase", Fixed(2)),
  NodeTerm("named", Fixed(2)),
  NodeTerm("use", Fixed(1)),       // ref to a named grammar
  NodeTerm("hole", Fixed(0)),
  NodeTerm("int", Fixed(0)),       // token piece terms
  NodeTerm("float", Fixed(0)),
  NodeTerm("text", Fixed(0)),
  NodeTerm("gap", Fixed(0)),
  NodeTerm("ref", Fixed(1)),
  NodeTerm("setting", Fixed(2)),  // view setting: key + value
  NodeTerm("rules", Any),         // a def's means rules + binding + examples
  NodeTerm("rule", Any),          // one means rule — its text (transitional)
                                  // or pattern + meaning (+ where)
  NodeTerm("binding", Any),       // the binding list: tiers, tightest first
  NodeTerm("tier", Any),          // one binding level: pattern texts
  NodeTerm("example", Fixed(2)),  // worked example: input + expected
  NodeTerm("pattern", Any),       // a rule's pattern: piece nodes
  NodeTerm("meaning", Any),       // a rule's meaning: one term subtree
  NodeTerm("where", Any),         // a rule's where-constraints
  NodeTerm("constraint", Any),    // placeholder names, term text last
  NodeTerm("placeholder", Any),   // pattern piece: name leaf (+ glued)
  NodeTerm("literal", Any),       // pattern piece: text leaf (+ glued)
  NodeTerm("glued", Fixed(0)),    // a piece glued to its predecessor
  NodeTerm("quote", Any),         // meaning builtin: a desugared subtree
  NodeTerm("reparse", Any),       // meaning builtin: an undesugared phrase
  NodeTerm("source", Fixed(1)))   // meaning builtin: bound source text

val docLanguageDef = LanguageDef(
  typeRef = langDefTypeRef,
  title = "lang: language",
  name = "Language definitions",
  description = "the language in which lang: documents are written",
  vocabulary = metaTerms,
  syntax = None,
  views = List(ViewBinding("def", "def-view")),
  // Projected inputs in a def doc — rule lines, binding patterns,
  // example lines — commit their text as a plain string leaf; the
  // recovering decode (readRuleOrHole & co.) turns it back into content.
  inputParser = Some(InputParserDef.Builtin("text")))

val DocLanguage: Language = new DefLanguage(docLanguageDef, docImpls)
