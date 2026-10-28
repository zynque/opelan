package opelan.examples

import opelan.foundation.grammar._

// The expression syntax's lexical rules — character-level, tried in
// order at whatever position the grammar is parsing (no token stream).
// Each rule's name is a token kind the expression grammar refers to.
//
//   float  digits.digits      int           digits        ref  ref:digits
//   op     + - * /            lpar          (             rpar  )
//   hole   ?                  unrecognized  a maximal run of anything
//                                           else, stopping at whitespace
//                                           and + - * / ( ) ?

private val structural = "+-*/()?"

private val digits = runOf(1)(_.isDigit)

val exprLexicon = Lexicon(List(
  "float"        -> chain(digits, exactly("."), digits),
  "int"          -> digits,
  "ref"          -> chain(exactly("ref:"), digits),
  "op"           -> oneOf("+-*/"),
  "lpar"         -> exactly("("),
  "rpar"         -> exactly(")"),
  "hole"         -> exactly("?"),
  "unrecognized" -> runOf(1)(c => !c.isWhitespace && !structural.contains(c))))
