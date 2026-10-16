package opelan.foundation.means

import opelan.foundation.view.Verbosity

// Rule text as role-tagged spans — one source for plain printing and for
// a highlighting surface (keywords emphasized, placeholders styled as
// math italicizes variables).
enum Role {
  case Keyword, Literal, Placeholder, Tag, Punctuation, Kind, Marker, Space
}

case class Styled(text: String, role: Role)

// Verbose spells the keyword `means` and shows `where`; terse spells it
// `=>` and elides `where` behind a marker — hidden, never absent. Terse
// output is a display projection: it does not round-trip the constraints.
def ruleSpans(rule: Rule, verbosity: Verbosity): List[Styled] = {
  val keyword = if (verbosity == Verbosity.Verbose) "means" else "=>"
  val tail =
    if (rule.where.isEmpty) Nil
    else if (verbosity == Verbosity.Terse) List(space, Styled("…", Role.Marker))
    else space :: Styled("where", Role.Keyword) :: space :: whereSpans(rule.where)
  pieceSpans(rule.pattern.pieces) ++
    List(space, Styled(keyword, Role.Keyword), space) ++
    meaningSpans(rule.meaning) ++ tail
}

def printRule(rule: Rule, verbosity: Verbosity = Verbosity.Verbose): String =
  ruleSpans(rule, verbosity).map(_.text).mkString

def printPattern(pattern: Pattern): String =
  pieceSpans(pattern.pieces).map(_.text).mkString

private val space = Styled(" ", Role.Space)
private def punctuation(text: String) = Styled(text, Role.Punctuation)

private def pieceSpans(pieces: List[Piece]): List[Styled] =
  pieces.zipWithIndex.flatMap { case (piece, i) =>
    val gap = if (i == 0 || piece.glued) Nil else List(space)
    gap :+ (piece match {
      case Piece.Placeholder(name, _) => Styled(name, Role.Placeholder)
      case Piece.Literal(text, _)     => Styled(quoteIfNeeded(text), Role.Literal)
    })
  }

// A literal that would read back as something else gets quoted.
private def quoteIfNeeded(text: String): String = {
  val tokens = lexRule(text)
  val plain = tokens.length == 1 && tokens.head.text == text &&
    tokens.head.kind != RuleTokenKind.Quoted &&
    !isPlaceholderWord(text) && !isMeansKeyword(tokens.head) && text != "where"
  if (plain) text else s"'$text'"
}

private def meaningSpans(meaning: Meaning): List[Styled] = meaning match {
  case Meaning.Use(placeholder) => List(Styled(placeholder, Role.Placeholder))
  case Meaning.Phrase(pieces)   => pieceSpans(pieces)
  case Meaning.Build(tag, arguments, _) =>
    (Styled(tag, Role.Tag) :: punctuation("(") ::
      joined(arguments.map(meaningSpans), ",")) :+ punctuation(")")
}

// Items joined by a separator (", " between arguments, "; " between groups).
private def joined(items: List[List[Styled]], separator: String): List[Styled] =
  items.reduceOption((a, b) => a ++ List(punctuation(separator), space) ++ b)
    .getOrElse(Nil)

private def whereSpans(constraints: List[Constraint]): List[Styled] =
  joined(constraints.map { c =>
    joined(c.placeholders.map(name => List(Styled(name, Role.Placeholder))), ",") ++
      List(punctuation(":"), space, Styled(c.kind, Role.Kind))
  }, ";")
