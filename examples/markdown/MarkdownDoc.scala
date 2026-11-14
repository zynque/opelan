package opelan.examples.markdown

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means._
import opelan.foundation.means.text.readRuleOrGap

// A markdown document's content: a `markdown` node whose children are
// the blocks — `#`/`##` headings, `list`s of `*` items, and `para`
// paragraphs. Tags name the surface constructs the definitions declare:
// a `* t` item stores the `*` construct whose meaning is `item`, as
// `a * b` stores the `*` construct meaning `mul` — the document IS the
// surface syntax. A block's inline content is a `runs` node: plain
// StringData text leaves among `bold`/`italic` span nodes.
//
//   # Title        →  #(runs(…))
//   ## Section     →  ##(runs(…))
//   * item         →  list(*(runs(…))…) — consecutive `* ` lines group
//   para text      →  para(runs(…))
//
// `markdown`, `list`, `para`, `runs`, `bold`, `italic` (and the `h1`,
// `h2`, `item` meanings) are the language's placeholder terms.

val MarkdownTag = "markdown"
val H1Tag = "#"
val H2Tag = "##"
val ItemTag = "*"
val ListTag = "list"
val ParaTag = "para"
val RunsTag = "runs"
val BoldTag = "bold"
val ItalicTag = "italic"

// The lexicon: marker tokens, then the plain-text run. `##`/`**` precede
// `#`/`*` — ordered choice takes the longest marker. `text` stops at
// `*` (the only markup opener inside a run) and the line's end.
val markdownLexicon = Lexicon(List(
  "h2"    -> exactly("##"),
  "h1"    -> exactly("#"),
  "bstar" -> exactly("**"),
  "istar" -> exactly("*"),
  "text"  -> runOf(1)(c => c != '*' && c != '\n')),
  gapStart = Set("h2", "h1", "bstar", "istar", "text"),
  gapRun = Set("h2", "h1", "bstar", "istar", "text"))

// The language's rules — what `lang: markdown`'s `definitions` section
// holds. `d` binds the impls' `blocks` production (the document's block
// sequence); `t` binds `runs` (a line's inline runs) or `plain` (one
// text token — emphasis operands don't nest). The means placeholder
// convention is single letters — `## t`, not `## text`.
val markdownRules: RuleSet = RuleSet(
  rules = List(
    "d means d where d: blocks",
    "# t means h1(t) where t: runs",
    "## t means h2(t) where t: runs",
    "* t means item(t) where t: runs",
    "t means para(t) where t: runs",
    "**t** means bold(t) where t: plain",
    "*t* means italic(t) where t: plain").map(readRuleOrGap))
