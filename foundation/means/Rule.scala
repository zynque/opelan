package opelan.foundation.means

import opelan.foundation.document.ExternalNodeReference

// The model behind `pattern means meaning` language definitions
// (DESIGN.md §4, *Defining languages by `means` rules*):
//
//   a + b  means  add(a, b)  where a, b: number
//
// A pattern is a sequence of literal and placeholder pieces. `glued`
// records that no whitespace preceded the piece in the source, so
// `-a` and `ref:c,r` print back as written; it never affects identity
// (see Pattern.key).
enum Piece {
  def glued: Boolean
  case Literal(text: String, glued: Boolean = false)
  case Placeholder(name: String, glued: Boolean = false)
}

case class Pattern(pieces: List[Piece]) {
  // Identity modulo spacing — how the binding list names a rule.
  def key: List[String] = pieces.map {
    case Piece.Literal(text, _)     => s"'$text"
    case Piece.Placeholder(name, _) => name
  }

  def placeholders: List[String] =
    pieces.collect { case Piece.Placeholder(name, _) => name }
}

// What a pattern stands for:
//   Build   a tree constructor — `add(a, b)` builds an `add` node;
//   Use     a bare placeholder — `(a) means a`;
//   Phrase  a phrase of the same language, read with its own rules —
//           `a % means a / 100`.
//
// Names here are labels, as in NodeKind and GrammarDef: in a `lang:`
// document a constructor is a ref to its kind's schema entry and a
// placeholder use an internal ref to the pattern's placeholder node
// (DESIGN.md §4, *Everything by reference*). `reference` carries that
// identity once a rule is read from a document; rules written in code or
// text have none until encoded, and a placeholder resolves by name
// within its rule.
enum Meaning {
  case Build(tag: String, arguments: List[Meaning],
      reference: Option[ExternalNodeReference] = None)
  case Use(placeholder: String)
  case Phrase(pieces: List[Piece])
}

object Meaning {
  def placeholders(meaning: Meaning): List[String] = meaning match {
    case Build(_, arguments, _) => arguments.flatMap(placeholders)
    case Use(placeholder)       => List(placeholder)
    case Phrase(pieces)         => Pattern(pieces).placeholders
  }
}

// `where c, r: whole number` — what may fill the named placeholders.
// The kind is free text until a type vocabulary exists.
case class Constraint(placeholders: List[String], kind: String)

case class Rule(
    pattern: Pattern,
    meaning: Meaning,
    where: List[Constraint] = Nil) {
  def shape: Shape = shapeOf(pattern)
}

// One line of the binding list: patterns that bind equally tightly.
case class Tier(patterns: List[Pattern])

// A worked example — concrete text in the language and what it means,
// e.g. `1 + 2 * 3 means 1 + (2 * 3)`. Checked later against the rules.
case class Example(input: String, expected: String)

// A language's rules, its binding list (tightest first), and examples.
case class RuleSet(
    rules: List[Rule],
    tiers: List[Tier] = Nil,
    examples: List[Example] = Nil)
