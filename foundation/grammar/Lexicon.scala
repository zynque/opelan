package opelan.foundation.grammar

// Scannerless lexing: there is no token stream. A lexicon is an ordered
// set of named, character-level lexical rules, tried at a position only
// when a combinator asks what starts there — the first rule that matches
// wins (PEG ordered choice), so order expresses priority (`float` before
// `int`, `ref` before `junk`). A grammar's token kinds ("int", "rpar")
// are the names of these rules.

// A lexical match at a position: the rule that matched and its range.
case class Token(kind: String, text: String, from: Int, to: Int)

// A node's source span in the input text.
case class Span(from: Int, to: Int)

// One lexical rule's attempt at a position: where the match ends (-1 for
// no match) and the exclusive bound of characters read to decide —
// `input.length + 1` when the end of input itself was observed.
case class Scan(end: Int, examined: Int)

type LexicalRule = (String, Int) => Scan

case class Lexicon(rules: List[(String, LexicalRule)])

// The kind of a character no lexical rule accepts: it still forms a
// one-character token, so text is never lost and recovery can place it.
val UnknownKind = ""

def skipSpace(input: String, pos: Int): Int = {
  var i = pos
  while (i < input.length && input.charAt(i).isWhitespace) i += 1
  i
}

// What starts at `pos` once whitespace is skipped, and how far deciding
// that read. None only at the end of input.
case class Scanned(token: Option[Token], examined: Int)

def scanAt(in: In, pos: Int): Scanned = {
  val text = in.input
  val start = skipSpace(text, pos)
  if (start >= text.length) Scanned(None, text.length + 1)
  else {
    var seen = start + 1
    val hit = in.lexicon.rules.iterator.map { case (kind, rule) =>
      val scan = rule(text, start)
      seen = seen.max(scan.examined)
      (kind, scan.end)
    }.find(_._2 > start)
    val (kind, end) = hit.getOrElse((UnknownKind, start + 1))
    Scanned(Some(Token(kind, text.substring(start, end), start, end)), seen)
  }
}
