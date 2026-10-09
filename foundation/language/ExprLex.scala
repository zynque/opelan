package opelan.foundation.language

// Tokens for the expression cell syntax. Every token carries its source
// span (char offsets into the input) so parse results can be mapped back
// to text ranges — the basis for translating editor deltas into reparsed
// fragments later.
//
//   digits        IntTok          + -           OpTok
//   ( )           LParen / RParen ?             HoleTok (explicit empty hole)
//   anything else JunkTok (a maximal run, may contain digits, stops at
//                 whitespace and the structural chars + - ( ) ?)
enum Tok {
  def from: Int
  def to: Int

  case IntTok(from: Int, to: Int) extends Tok
  case OpTok(op: Char, from: Int, to: Int) extends Tok
  case LParen(from: Int, to: Int) extends Tok
  case RParen(from: Int, to: Int) extends Tok
  case HoleTok(from: Int, to: Int) extends Tok
  case JunkTok(from: Int, to: Int) extends Tok
}

private def isStructural(c: Char): Boolean =
  c.isWhitespace || c == '+' || c == '-' || c == '(' || c == ')' || c == '?'

def lex(input: String): Vector[Tok] = {
  val toks = Vector.newBuilder[Tok]
  var i = 0
  while (i < input.length) {
    val c = input.charAt(i)
    if (c.isWhitespace) i += 1
    else if (c.isDigit) {
      val from = i
      while (i < input.length && input.charAt(i).isDigit) i += 1
      toks += Tok.IntTok(from, i)
    } else if (c == '+' || c == '-') {
      toks += Tok.OpTok(c, i, i + 1); i += 1
    } else if (c == '(') { toks += Tok.LParen(i, i + 1); i += 1 }
    else if (c == ')') { toks += Tok.RParen(i, i + 1); i += 1 }
    else if (c == '?') { toks += Tok.HoleTok(i, i + 1); i += 1 }
    else {
      val from = i
      while (i < input.length && !isStructural(input.charAt(i))) i += 1
      toks += Tok.JunkTok(from, i)
    }
  }
  toks.result()
}
