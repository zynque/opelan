package opelan.examples.expr

import opelan.foundation.grammar._

// The expression syntax's authored leaf layer — what no rule pattern
// can spell. The literal tokens (`+ - * / ( )`) derive from the
// rules themselves where they compile (LiteralLexicon.withLiterals);
// `exprTokens` is the composed lexicon direct parsing uses.
//
//   float  digits.digits      int           digits        ref  ref:digits
//   gap   ?                  unrecognized  a maximal run of anything
//                                           else, stopping at whitespace
//                                           and + - * / ( ) ?

// Chars that begin a derived literal or the `?` leaf — the
// `unrecognized` run's stop set (still hand-authored; PLAN's
// *Surfaces as definitions* derives run-leaf stop sets from the
// literal set).
private val structural = "+-*/()?"

private val digits = runOf(1)(_.isDigit)

val exprLexicon = Lexicon(List(
  "float"        -> chain(digits, exactly("."), digits),
  "int"          -> digits,
  "ref"          -> chain(exactly("ref:"), digits),
  "gap"          -> exactly("?"),
  "unrecognized" -> runOf(1)(c => !c.isWhitespace && !structural.contains(c))),
  // What no production places becomes a gap: unrecognized text starts
  // one — infix literals (`+ - * /`) join at composition — and it runs
  // over operand-ish kinds, stopping at brackets and further literals.
  gapStart = Set("unrecognized"),
  gapRun = Set("int", "float", "ref", "unrecognized", "gap"))
