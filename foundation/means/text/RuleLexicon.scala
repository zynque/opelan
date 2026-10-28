package opelan.foundation.means.text

import opelan.foundation.grammar._

// Helpers first: top-level values initialize in file order.
private val digitRun = runOf(1)(_.isDigit)

private val singleCharacterSymbols = "()[]{},;"

private def isSymbolCharacter(c: Char): Boolean =
  !c.isWhitespace && !c.isLetterOrDigit && c != '_' && c != '\'' &&
    !singleCharacterSymbols.contains(c)

// The lexical rules of rule text — character-level, tried in order where
// the rule grammar is parsing:
//
//   number  digits, optionally .digits                       100  1.5
//   word    letters/digits/_ (a digit start is a number)     add  then  a
//   quoted  '…' — always a literal, never a placeholder      'x'  'means'
//   single  ( ) [ ] { } , ; one at a time
//   symbol  a run of any other non-space characters           -  =>  ..
val ruleLexicon = Lexicon(List(
  "number" -> firstOf(chain(digitRun, exactly("."), digitRun), digitRun),
  "word"   -> runOf(1)(c => c.isLetterOrDigit || c == '_'),
  "quoted" -> chain(exactly("'"), runOf(0)(_ != '\''), optionally(exactly("'"))),
  "single" -> oneOf(singleCharacterSymbols),
  "symbol" -> runOf(1)(isSymbolCharacter)))

val ruleTokenKinds = Set("number", "word", "quoted", "single", "symbol")

// Words with a role in rule text; as literals they must be quoted.
val ruleKeywords = Set("means", "=>", "where")

// The provisional plain-text placeholder convention (an open question
// in DESIGN.md §4): a lone lowercase letter, optionally numbered — `a`,
// `b`, `n`, `x2`. Every other word is literal; quote to force one.
def isPlaceholderWord(word: String): Boolean =
  word.nonEmpty && word.head.isLower && word.head.isLetter &&
    word.tail.forall(_.isDigit)

// A quoted token's content: its quotes removed (an unclosed quote runs
// to the end of the text).
def unquote(text: String): String = {
  val inner = text.drop(1)
  if (inner.endsWith("'")) inner.dropRight(1) else inner
}
