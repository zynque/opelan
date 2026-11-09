package opelan.foundation.means.text

import opelan.foundation.document.{NodeData, NodeDataText}
import opelan.foundation.grammar.{ParseInput, scanAt}
import opelan.foundation.means._
import opelan.foundation.view.Verbosity

// Rule text as role-tagged spans — one source for plain printing and for
// a highlighting surface (keywords emphasized, placeholders styled as
// math italicizes variables).
enum Role {
  case Keyword, Literal, Placeholder, Tag, Punctuation, Term, Marker, Space
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

// A meaning's canonical spelling — the constructor form; a quoted
// phrase prints as its stored text (the projection surfaces that
// display phrase spellings go through `printWith` instead).
def printMeaning(meaning: Meaning): String =
  meaningSpans(meaning).map(_.text).mkString

private val space = Styled(" ", Role.Space)
private def punctuation(text: String) = Styled(text, Role.Punctuation)

private[text] def pieceSpans(pieces: List[Piece]): List[Styled] =
  pieces.zipWithIndex.flatMap { case (piece, i) =>
    val gap = if (i == 0 || piece.glued) Nil else List(space)
    gap :+ (piece match {
      case Piece.Placeholder(name, _) => Styled(name, Role.Placeholder)
      case Piece.Literal(text, _)     => Styled(quoteIfNeeded(text), Role.Literal)
    })
  }

// A literal that would read back as something else gets quoted: it must
// be exactly one unquoted token, and neither a placeholder nor a keyword.
private def quoteIfNeeded(text: String): String = {
  val scanned = scanAt(ParseInput(text, ruleLexicon), 0).token
  val plain = scanned.exists(t => t.from == 0 && t.to == text.length && t.kind.id != "quoted") &&
    !isPlaceholderWord(text) && !ruleKeywords(text)
  if (plain) text else s"'$text'"
}

private[text] def meaningSpans(meaning: Meaning): List[Styled] =
  meaning.tag match {
  case "use" =>
    meaning.arguments.flatMap(operandSpans)
  case "source" | "builtin" | "literal" =>
    (Styled(meaning.tag, Role.Keyword) :: punctuation("(") ::
      joined(meaning.arguments.map(operandSpans), ",")) :+ punctuation(")")
  case "quote" =>
    meaning.quoted.toList.map(q => Styled(quoteText(q), Role.Literal))
  case tag =>
    (Styled(tag, Role.Tag) :: punctuation("(") ::
      joined(meaning.arguments.map(meaningSpans), ",")) :+ punctuation(")")
}

// A quoted subtree's text — decoded trees carry no source span, only
// data. A quoted gap prints its bare text: the canonical spelling
// re-reads to the same gap (views mark the `?` separately).
private def quoteText(t: opelan.foundation.grammar.ParseTree): String =
  t.data match {
    case NodeData.GapData(text) => text
    case d if t.children.isEmpty    => NodeDataText.show(d)
    case _                      => t.children.map(quoteText).mkString
  }

// A builtin's operand — `source(a)`/`builtin(num-add)` bind a name, so
// a name leaf prints as a placeholder, anything else as a term.
private def operandSpans(m: Meaning): List[Styled] =
  Meaning.nameOf(m) match {
    case Some(name) => List(Styled(name, Role.Placeholder))
    case None       => meaningSpans(m)
  }

// Items joined by a separator (", " between arguments, "; " between groups).
private def joined(items: List[List[Styled]], separator: String): List[Styled] =
  items.reduceOption((a, b) => a ++ List(punctuation(separator), space) ++ b)
    .getOrElse(Nil)

private[text] def whereSpans(constraints: List[Constraint]): List[Styled] =
  joined(constraints.map { c =>
    joined(c.placeholders.map(name => List(Styled(name, Role.Placeholder))), ",") ++
      List(punctuation(":"), space, Styled(c.term, Role.Term))
  }, ";")
