package opelan.foundation.language

import opelan.foundation.grammar.GTok

// Tokens for the expression cell syntax. Every token carries its source
// span (char offsets into the input) so parse results can be mapped back
// to text ranges — the basis for translating editor deltas into reparsed
// fragments later.
//
//   digits        IntTok          digits.digits   FloatTok
//   ref:N         RefTok          + - * /         OpTok
//   ( )           LParen / RParen ?               HoleTok (explicit empty hole)
//   anything else JunkTok (a maximal run, may contain digits, stops at
//                 whitespace and the structural chars + - * / ( ) ?)
enum Tok {
  def from: Int
  def to: Int

  case IntTok(from: Int, to: Int) extends Tok
  case FloatTok(from: Int, to: Int) extends Tok
  case RefTok(from: Int, to: Int) extends Tok
  case OpTok(op: Char, from: Int, to: Int) extends Tok
  case LParen(from: Int, to: Int) extends Tok
  case RParen(from: Int, to: Int) extends Tok
  case HoleTok(from: Int, to: Int) extends Tok
  case JunkTok(from: Int, to: Int) extends Tok
}

private def isStructural(c: Char): Boolean =
  c.isWhitespace || c == '+' || c == '-' || c == '*' || c == '/' ||
    c == '(' || c == ')' || c == '?'

def lex(input: String): Vector[Tok] = {
  val toks = Vector.newBuilder[Tok]
  var i = 0
  while (i < input.length) {
    val c = input.charAt(i)
    if (c.isWhitespace) i += 1
    else if (c.isDigit) {
      val from = i
      while (i < input.length && input.charAt(i).isDigit) i += 1
      // A digit run followed by '.' and more digits is a float.
      if (i + 1 < input.length && input.charAt(i) == '.' &&
          input.charAt(i + 1).isDigit) {
        i += 1
        while (i < input.length && input.charAt(i).isDigit) i += 1
        toks += Tok.FloatTok(from, i)
      } else toks += Tok.IntTok(from, i)
    } else if (c == '+' || c == '-' || c == '*' || c == '/') {
      toks += Tok.OpTok(c, i, i + 1); i += 1
    } else if (c == '(') { toks += Tok.LParen(i, i + 1); i += 1 }
    else if (c == ')') { toks += Tok.RParen(i, i + 1); i += 1 }
    else if (c == '?') { toks += Tok.HoleTok(i, i + 1); i += 1 }
    else {
      val refTo = refAt(input, i)
      if (refTo >= 0) { toks += Tok.RefTok(i, refTo); i = refTo }
      else {
        val from = i
        while (i < input.length && !isStructural(input.charAt(i))) i += 1
        toks += Tok.JunkTok(from, i)
      }
    }
  }
  toks.result()
}

// The end offset of a "ref:digits" token starting at i, or -1.
private def refAt(input: String, i: Int): Int =
  if (input.startsWith("ref:", i)) {
    var j = i + 4
    while (j < input.length && input.charAt(j).isDigit) j += 1
    if (j > i + 4) j else -1
  } else -1

// The generic-token view of an expr lex — the builtin "expr" lexer in
// the language-def registry. Kinds are the grammar's vocabulary.
def exprGToks(input: String): Vector[GTok] =
  lex(input).map { t =>
    val kind = t match {
      case _: Tok.IntTok   => "int"
      case _: Tok.FloatTok => "float"
      case _: Tok.RefTok   => "ref"
      case _: Tok.OpTok    => "op"
      case _: Tok.LParen   => "lpar"
      case _: Tok.RParen   => "rpar"
      case _: Tok.HoleTok  => "hole"
      case _: Tok.JunkTok  => "junk"
    }
    GTok(kind, input.substring(t.from, t.to), t.from, t.to)
  }
