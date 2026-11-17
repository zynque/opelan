package opelan.foundation.means.engine

import opelan.foundation.grammar._
import opelan.foundation.means._

// A rule set's literals, derived: `a + b means add(a, b)` already
// spells `+` — the lexicon's literal tokens are the patterns' literal
// pieces, longest-first, so an authored rule line is all a literal
// needs (`a // b` tokenizes as soon as its definition exists). Leaf
// productions (number, ref, text) stay authored — a pattern never
// says what characters a token is made of.
//
// A derived literal's token kind is its text: `sequenceRule` and the
// operator table match literals by token text, so the kind's only
// job is recovery. An *interior* literal — a placeholder on both
// sides, `a + b`'s `+` — joins `gapStart`, where a stray operator
// begins a gap run, like the hand-authored `op` did. Brackets and
// sequence edges don't — `(` never began a gap.
//
// A literal ending in a word character gets a right-edge word
// boundary (`mod` must not tokenize `modulo`); the left edge needs
// none, scanning only ever starts at token boundaries.
//
// Ordered choice, longest first: `##` precedes `#`, `**` precedes
// `*`. Derived literals precede the authored rules — a literal is a
// reserved spelling, so a `ref`-spelled literal wins over the `ref:`
// leaf at its own boundary (a collision to name, not to hide).

// Every literal spelling in `set`, in order of first appearance.
def literalsOf(set: RuleSet): List[String] =
  set.rules.flatMap(_.pattern.pieces).collect {
    case Piece.Literal(text, _) => text
  }.distinct

// Literal spellings between two placeholders in some pattern — the
// operator position: `a + b`'s `+`, `a is not b`'s `is`. A stray one
// at operand position begins a gap run.
def infixLiteralsOf(set: RuleSet): Set[String] =
  set.rules.flatMap { rule =>
    val p = rule.pattern.pieces
    p.indices.collect {
      case i if i > 0 && i < p.length - 1 &&
        p(i - 1).isInstanceOf[Piece.Placeholder] &&
        p(i + 1).isInstanceOf[Piece.Placeholder] => p(i)
    }.collect { case Piece.Literal(text, _) => text }
  }.toSet

// Lexicon entries for the spelled literals, longest first — the
// token kind is the spelling itself.
def literalRules(spelled: List[String]): List[(String, LexicalRule)] =
  spelled.sortBy(-_.length).map(text => text -> literalRule(text))

// `exactly(text)`, plus a right word boundary when the literal ends
// in a letter or digit (`if` must not tokenize `iffy`).
def literalRule(text: String): LexicalRule = {
  val base = exactly(text)
  val bounded = text.lastOption.exists(_.isLetterOrDigit)
  (input, at) => {
    val s = base(input, at)
    if (s.end < 0 || !bounded || s.end >= input.length ||
        !input.charAt(s.end).isLetterOrDigit) s
    else Scan(-1, s.examined)
  }
}

// `base` plus the literals `set` spells — derived first (reserved
// spellings), then the authored rules. `gapStart` gains the infix
// literals; `gapRun` stays authored — a gap never ran over an
// operator.
def withLiterals(set: RuleSet, base: Lexicon): Lexicon =
  base.copy(
    rules = literalRules(literalsOf(set)) ++ base.rules,
    gapStart = base.gapStart ++ infixLiteralsOf(set).map(TokenKind(_)))
