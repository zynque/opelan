package opelan.foundation.means

// Tokens of rule text. `glued` means no whitespace separated the token
// from the one before it — spacing the printer reproduces.
//
//   Word    letters/digits/_ starting with a letter or _     add  then  a
//   Number  digits, optionally .digits                       100  1.5
//   Quoted  '…' — always a literal, never a placeholder      'x'  'means'
//   Symbol  ( ) [ ] { } , ; singly; other symbol runs whole   -  =>  ..
enum RuleTokenKind { case Word, Number, Quoted, Symbol }

case class RuleToken(kind: RuleTokenKind, text: String, glued: Boolean) {
  def is(k: RuleTokenKind, t: String): Boolean = kind == k && text == t
}

// The provisional plain-text placeholder convention (an open question
// in DESIGN.md §4): a lone lowercase letter, optionally numbered — `a`,
// `b`, `n`, `x2`. Every other word is literal; quote to force one.
def isPlaceholderWord(word: String): Boolean =
  word.nonEmpty && word.head.isLower && word.head.isLetter &&
    word.tail.forall(_.isDigit)

private val singleCharacterSymbols = "()[]{},;"

private def isSymbolCharacter(c: Char): Boolean =
  !c.isWhitespace && !c.isLetterOrDigit && c != '_' && c != '\'' &&
    !singleCharacterSymbols.contains(c)

def lexRule(text: String): Vector[RuleToken] = {
  val out = Vector.newBuilder[RuleToken]
  var i = 0
  def run(p: Char => Boolean): Unit =
    while (i < text.length && p(text.charAt(i))) i += 1
  while (i < text.length) {
    val c = text.charAt(i)
    if (c.isWhitespace) i += 1
    else {
      val from = i
      val glued = from > 0 && !text.charAt(from - 1).isWhitespace
      def emit(k: RuleTokenKind, t: String) = out += RuleToken(k, t, glued)
      if (c.isLetter || c == '_') {
        run(ch => ch.isLetterOrDigit || ch == '_')
        emit(RuleTokenKind.Word, text.substring(from, i))
      } else if (c.isDigit) {
        run(_.isDigit)
        if (i + 1 < text.length && text.charAt(i) == '.' &&
            text.charAt(i + 1).isDigit) { i += 1; run(_.isDigit) }
        emit(RuleTokenKind.Number, text.substring(from, i))
      } else if (c == '\'') {
        i += 1; run(_ != '\'')
        emit(RuleTokenKind.Quoted, text.substring(from + 1, i))
        if (i < text.length) i += 1 // an unclosed quote runs to the end
      } else if (singleCharacterSymbols.contains(c)) {
        i += 1; emit(RuleTokenKind.Symbol, c.toString)
      } else {
        run(isSymbolCharacter)
        emit(RuleTokenKind.Symbol, text.substring(from, i))
      }
    }
  }
  out.result()
}
