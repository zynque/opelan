package opelan.foundation.grammar

// Building blocks for lexical rules. Each reports honestly how far it
// read, so incremental reparsing can tell which edits could change it.

private def noMatch(examined: Int) = Scan(-1, examined)

// A run of at least `minimum` characters satisfying `accept`.
def runOf(minimum: Int)(accept: Char => Boolean): LexicalRule = (text, at) => {
  var i = at
  while (i < text.length && accept(text.charAt(i))) i += 1
  Scan(if (i - at >= minimum) i else -1, i + 1)
}

// Exactly `literal`.
def exactly(literal: String): LexicalRule = (text, at) => {
  var k = 0
  while (k < literal.length && at + k < text.length &&
         text.charAt(at + k) == literal.charAt(k)) k += 1
  if (k == literal.length) Scan(at + k, at + k) else noMatch(at + k + 1)
}

// One character from `characters`.
def oneOf(characters: String): LexicalRule = (text, at) =>
  if (at < text.length && characters.contains(text.charAt(at))) Scan(at + 1, at + 1)
  else noMatch(at + 1)

// Rules in sequence, each starting where the previous ended.
def chain(rules: LexicalRule*): LexicalRule = (text, at) =>
  rules.foldLeft(Scan(at, at)) { (acc, rule) =>
    if (acc.end < 0) acc
    else {
      val next = rule(text, acc.end)
      Scan(next.end, acc.examined.max(next.examined))
    }
  }

// The first rule that matches.
def firstOf(rules: LexicalRule*): LexicalRule = (text, at) => {
  var seen = at
  rules.iterator.map { rule =>
    val scan = rule(text, at)
    seen = seen.max(scan.examined)
    scan
  }.find(_.end >= 0).map(_.copy(examined = seen)).getOrElse(noMatch(seen))
}

// `rule`, or nothing at all.
def optionally(rule: LexicalRule): LexicalRule = (text, at) => {
  val scan = rule(text, at)
  if (scan.end >= 0) scan else Scan(at, scan.examined)
}
