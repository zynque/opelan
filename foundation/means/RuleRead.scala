package opelan.foundation.means

import RuleTokenKind._

// Read one rule from text:
//
//   pattern means meaning [where a, b: kind; c: kind]
//
// `=>` is accepted wherever `means` is — the terse spelling. Left
// carries what's wrong; callers decide how to keep the text (a document
// decoder will hold it as a hole rule, per *Never broken*).
def readRule(text: String): Either[String, Rule] = {
  val tokens = lexRule(text)
  val keywordAt = tokens.indexWhere(isMeansKeyword)
  if (keywordAt < 0) Left("missing 'means'")
  else if (keywordAt == 0) Left("empty pattern")
  else {
    val rest = tokens.drop(keywordAt + 1)
    val whereAt = rest.indexWhere(_.is(Word, "where"))
    val (meaningTokens, whereTokens) =
      if (whereAt < 0) (rest, Vector.empty) else rest.splitAt(whereAt)
    for {
      meaning <- readMeaning(meaningTokens)
      where <- if (whereAt < 0) Right(Nil) else readWhere(whereTokens.drop(1))
    } yield Rule(Pattern(pieces(tokens.take(keywordAt))), meaning, where)
  }
}

// A bare pattern — how the binding list names a rule (`a * b`).
def readPattern(text: String): Pattern = Pattern(pieces(lexRule(text)))

def isMeansKeyword(token: RuleToken): Boolean =
  token.is(Word, "means") || token.is(Symbol, "=>")

// A pattern or phrase: placeholder words become placeholders, everything
// else literal. The first piece is never glued — nothing precedes it.
private[means] def pieces(tokens: Seq[RuleToken]): List[Piece] =
  tokens.toList.zipWithIndex.map { case (t, i) =>
    val glued = i > 0 && t.glued
    if (t.kind == Word && isPlaceholderWord(t.text)) Piece.Placeholder(t.text, glued)
    else Piece.Literal(t.text, glued)
  }

private def readMeaning(tokens: Vector[RuleToken]): Either[String, Meaning] =
  tokens.toList match {
    case Nil => Left("empty meaning")
    case List(t) if t.kind == Word && isPlaceholderWord(t.text) =>
      Right(Meaning.Use(t.text))
    case tag :: open :: _
        if tag.kind == Word && open.is(Symbol, "(") && closesAtEnd(tokens) =>
      splitArguments(tokens.slice(2, tokens.length - 1))
        .foldRight[Either[String, List[Meaning]]](Right(Nil)) { (a, acc) =>
          for { m <- readMeaning(a); ms <- acc } yield m :: ms
        }
        .map(arguments => Meaning.Build(tag.text, arguments))
    case _ => Right(Meaning.Phrase(pieces(tokens)))
  }

// Does the "(" at index 1 close at the last token?
private def closesAtEnd(tokens: Vector[RuleToken]): Boolean = {
  val depths = tokens.drop(1).scanLeft(0)((d, t) =>
    if (t.is(Symbol, "(")) d + 1 else if (t.is(Symbol, ")")) d - 1 else d).drop(1)
  depths.last == 0 && depths.init.forall(_ > 0)
}

// Constructor arguments split at depth-0 commas; `add()` has none.
private def splitArguments(tokens: Vector[RuleToken]): List[Vector[RuleToken]] =
  if (tokens.isEmpty) Nil
  else {
    val (done, last, _) = tokens.foldLeft(
        (List.empty[Vector[RuleToken]], Vector.empty[RuleToken], 0)) {
      case ((done, current, d), t) =>
        if (d == 0 && t.is(Symbol, ",")) (current :: done, Vector.empty, d)
        else {
          val next =
            if (t.is(Symbol, "(")) d + 1 else if (t.is(Symbol, ")")) d - 1 else d
          (done, current :+ t, next)
        }
    }
    (last :: done).reverse
  }

// `a, b: number; c: whole number` — groups split at `;`.
private def readWhere(tokens: Vector[RuleToken]): Either[String, List[Constraint]] =
  splitOn(tokens, ";").foldRight[Either[String, List[Constraint]]](Right(Nil)) {
    (group, acc) =>
      val colon = group.indexWhere(_.is(Symbol, ":"))
      val names = group.take(colon.max(0)).filterNot(_.is(Symbol, ",")).map(_.text)
      val kind = group.drop(colon + 1).map(_.text).mkString(" ")
      if (colon < 0) Left("where needs 'placeholders: kind'")
      else if (names.isEmpty || kind.isEmpty) Left("incomplete where clause")
      else acc.map(Constraint(names.toList, kind) :: _)
  }

private def splitOn(
    tokens: Vector[RuleToken], separator: String): List[Vector[RuleToken]] =
  tokens.foldRight(List(Vector.empty[RuleToken])) { (t, acc) =>
    if (t.is(Symbol, separator)) Vector.empty :: acc
    else (t +: acc.head) :: acc.tail
  }
