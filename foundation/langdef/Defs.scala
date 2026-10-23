package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.language.Language

// The meta-language definition: the type all `lang:` documents carry.
// Its grammar isn't expressed as a GrammarDef — the def-doc format IS
// the codec (DefDocEncode/Decode) — so DocLanguage is a code-resident
// Language whose "def" view decodes the doc and prints the definition.
// Its own langDoc is a def doc describing that format: self-describing,
// and the regress stops here — its schema entries are the constructs
// every def doc (itself included) imports. Concrete languages (the
// expression and sheet examples) live alongside it in the examples
// package.
import NodeShape._

val langDefTypeRef =
  ExternalNodeReference("opelan:languages/language", 0, 0)

// The meta constructs, in schema order. Names are unique so code can key
// on them; documents never store them — they import the schema entries.
private[langdef] val metaKinds: List[NodeKind] = List(
  NodeKind("title", Fixed(1)),     // document-structure: title's text
  NodeKind("imports", Any),        // document-structure: the dependencies
  NodeKind("definition", Any),     // content root: the sections below
  NodeKind("description", Fixed(1)),
  NodeKind("name", Fixed(1)),
  NodeKind("schema", Any),
  NodeKind("syntax", Any),         // builtin lexicon name + grammar (+ rules)
  NodeKind("views", Any),
  NodeKind("inputs", Fixed(1)),
  NodeKind("kind", Fixed(2)),      // schema entry: name + shape
  NodeKind("view", Fixed(2)),      // view entry: name + builtin
  NodeKind("language", Fixed(2)),  // input grammar by language ref + fallback
  NodeKind("builtin", Fixed(1)),   // input grammar by builtin name
  NodeKind("leaf", Fixed(0)),      // shapes
  NodeKind("any", Fixed(0)),
  NodeKind("fixed", Fixed(1)),
  NodeKind("as-label", Fixed(0)),  // input fallbacks
  NodeKind("as-gap", Fixed(0)),
  NodeKind("tok", Fixed(2)),       // grammar combinators
  NodeKind("choice", Any),
  NodeKind("parens", Fixed(1)),
  NodeKind("signum", Fixed(0)),
  NodeKind("junk", Fixed(2)),
  NodeKind("chain", Fixed(2)),
  NodeKind("ops", Any),
  NodeKind("op", Fixed(2)),        // symbol + ref to the kind it builds
  NodeKind("precedence", Fixed(2)),
  NodeKind("levels", Any),
  NodeKind("level", Any),
  NodeKind("phrase", Fixed(2)),
  NodeKind("named", Fixed(2)),
  NodeKind("use", Fixed(1)),       // ref to a named grammar
  NodeKind("hole", Fixed(0)),
  NodeKind("int", Fixed(0)),       // token leaf kinds
  NodeKind("float", Fixed(0)),
  NodeKind("text", Fixed(0)),
  NodeKind("gap", Fixed(0)),
  NodeKind("ref", Fixed(1)),
  NodeKind("setting", Fixed(2)),  // view setting: key + value
  NodeKind("rules", Any),         // a def's means rules + binding + examples
  NodeKind("rule", Any),          // one means rule — its text (transitional)
                                  // or pattern + meaning (+ where)
  NodeKind("binding", Any),       // the binding list: tiers, tightest first
  NodeKind("tier", Any),          // one binding level: pattern texts
  NodeKind("example", Fixed(2)),  // worked example: input + expected
  NodeKind("pattern", Any),       // a rule's pattern: piece nodes
  NodeKind("meaning", Any),       // a rule's meaning: one term subtree
  NodeKind("where", Any),         // a rule's where-constraints
  NodeKind("constraint", Any),    // placeholder names, kind text last
  NodeKind("placeholder", Any),   // pattern piece: name leaf (+ glued)
  NodeKind("literal", Any),       // pattern piece: text leaf (+ glued)
  NodeKind("glued", Fixed(0)),    // a piece glued to its predecessor
  NodeKind("quote", Any),         // meaning builtin: a desugared subtree
  NodeKind("reparse", Any),       // meaning builtin: an undesugared phrase
  NodeKind("source", Fixed(1)))   // meaning builtin: bound source text

val docLanguageDef = LanguageDef(
  typeRef = langDefTypeRef,
  title = "lang: language",
  name = "Language definitions",
  description = "the language in which lang: documents are written",
  schema = metaKinds,
  syntax = None,
  views = List(ViewBinding("def", "def-view")),
  // Projected inputs in a def doc — rule lines, binding patterns,
  // example lines — commit their text as a plain string leaf; the
  // recovering decode (readRuleOrHole & co.) turns it back into content.
  inputParser = Some(InputParserDef.Builtin("text")))

val DocLanguage: Language = new DefLanguage(docLanguageDef, docImpls)
