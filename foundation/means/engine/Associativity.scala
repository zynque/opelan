package opelan.foundation.means.engine

import opelan.foundation.grammar.Assoc
import opelan.foundation.means._

// An infix operator's associativity, read off the rule set's worked
// examples (DESIGN.md §4, *Precedence is an ordered list*): left by
// default — `a + b + c` reads `(a + b) + c` — until an example shows
// a different grouping. A witness is an example whose input chains
// the rule's literals at top level — `a ^ b ^ c` — and whose expected
// spells the grouping: `a ^ (b ^ c)` selects Right, `(a ^ b) ^ c`
// confirms Left. Anything else witnesses nothing — an example that
// merely holds as written (`a + b + c means a + b + c`) is ambiguous
// between grouping and not grouping, so Non stays code-side until a
// notation says it. Examples check against the compiled parser, so a
// witness the derivation disagrees with still reports as a failing
// example rather than silently choosing.
def operatorAssoc(set: RuleSet, theRule: Rule): Assoc =
  if (theRule.shape != Shape.Infix) Assoc.Left
  else set.examples.view.flatMap(witness(theRule, _)).headOption
    .getOrElse(Assoc.Left)

// One example's verdict on `theRule`, or None when it doesn't
// witness: the rule's literals — joined as a chain spells them —
// must appear exactly twice outside parens in the input, giving the
// three operands the expected regroups.
private def witness(theRule: Rule, e: Example): Option[Assoc] = {
  val chain = theRule.pattern.pieces
    .collect { case Piece.Literal(t, _) => t }.mkString(" ")
  if (chain.isEmpty) None else splitTop(e.input, chain) match {
    case List(x, y, z) =>
      val expected = compact(e.expected)
      if (expected == compact(s"$x $chain ($y $chain $z)"))
        Some(Assoc.Right)
      else if (expected == compact(s"($x $chain $y) $chain $z"))
        Some(Assoc.Left)
      else None
    case _ => None
  }
}

// `text` split on `chain`'s occurrences outside parens — `a ^ b ^ c`
// on `^` gives `a`, `b`, `c`. String-level, not lexed: a witness is a
// shape — `a`, `b`, `c` are operand positions, not language tokens.
private def splitTop(text: String, chain: String): List[String] = {
  var depth = 0
  var i = 0
  val pieces = List.newBuilder[String]
  val cur = new StringBuilder
  while (i < text.length) {
    if (depth == 0 && text.startsWith(chain, i)) {
      pieces += cur.toString
      cur.clear()
      i += chain.length
    } else {
      if (text(i) == '(') depth += 1
      else if (text(i) == ')') depth -= 1
      cur += text(i)
      i += 1
    }
  }
  (pieces += cur.toString).result().map(_.trim)
}

// Grouping comparison ignores whitespace — `a^(b^c)` witnesses `^`.
private def compact(s: String): String = s.filterNot(_.isWhitespace)
