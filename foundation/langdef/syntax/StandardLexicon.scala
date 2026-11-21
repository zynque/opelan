package opelan.foundation.langdef.syntax

import opelan.foundation.grammar._
import opelan.foundation.langdef.Impls

// The standard leaf vocabulary's token layer — the characters no rule
// pattern displays, shared by every dialect that selects it by name
// (PLAN, *A shared leaf vocabulary*):
//
//   float  digits.digits      int   digits      ref  ref:digits
//   gap    ?                  unrecognized  a maximal run of anything
//                                           else, stopping at
//                                           whitespace and + - * / ( ) ?
//
// The literal tokens (`+`, `*`, …) derive from a def's own rules at
// compile (LiteralLexicon.withLiterals), so this lexicon carries none
// — only the leaves and the recovery run. `ref:` and `?` are
// foundation machinery: `ref:N` is an InternalNodeRef's text form,
// `?` the workbench gap. What a `where` term binds (number, ref, the
// narrowing names) lives in standardTerms (Leaves.scala); the two
// halves meet at their token-kind names.

// Chars that begin a derived literal or the `?` leaf — the
// `unrecognized` run's stop set (still hand-authored; PLAN's
// *Surfaces as definitions* derives run-leaf stop sets from the
// literal set).
private val structural = "+-*/()?"

private val digits = runOf(1)(_.isDigit)

// Named "standard" for SyntaxDef.lexicon — `syntax "standard"` selects
// it. A dialect needing different leaves (markdown's text runs)
// authors its own in its impls.
val standardLexicon = Lexicon(List(
  "float"        -> chain(digits, exactly("."), digits),
  "int"          -> digits,
  "ref"          -> chain(exactly("ref:"), digits),
  "gap"          -> exactly("?"),
  "unrecognized" -> runOf(1)(
    c => !c.isWhitespace && !structural.contains(c))),
  // What no production places becomes a gap: unrecognized text starts
  // one — infix literals (`+ - * /`) join at composition — and it runs
  // over operand-ish kinds, stopping at brackets and further literals.
  gapStart = Set("unrecognized"),
  gapRun = Set("int", "float", "ref", "unrecognized", "gap"))

// The lexicons a def can name without impls wiring.
val standardLexicons: Map[String, Lexicon] =
  Map("standard" -> standardLexicon)

// A def's lexicon by name: the impls' first — a dialect's own names
// and any override of a standard name — then the standard lexicons.
def lexiconFor(impls: Impls, name: String): Option[Lexicon] =
  impls.lexicons.get(name).orElse(standardLexicons.get(name))
