package opelan.foundation.means

import opelan.foundation.document.NodeData
import opelan.foundation.grammar._

// Read one rule from text:
//
//   pattern means meaning [where a, b: kind; c: kind]
//
// `=>` is accepted wherever `means` is — the terse spelling. The text is
// parsed by RuleGrammar, which always produces a tree; this turns the
// tree into a Rule, or says what's missing. Callers decide how to keep
// malformed text (a document decoder will hold it as a hole rule, per
// *Never broken*).
def readRule(text: String): Either[String, Rule] = {
  val parts = ruleGrammar(In(text, ruleLexicon), 0).tree.kids
  val (pattern, keyword, meaning, where) = (parts(0), parts(1), parts(2), parts(3))
  if (!isToken(keyword)) Left("missing 'means'")
  else if (pattern.kids.isEmpty) Left("empty pattern")
  else if (!isTagged(meaning)) Left("empty meaning")
  else for {
    constraints <- if (isTagged(where)) readWhere(where) else Right(Nil)
  } yield Rule(Pattern(pieces(tokensIn(pattern))), readMeaning(meaning), constraints)
}

// A malformed rule becomes a rule that never applies — the Never
// Broken counterpart of `readRule`'s error report (DESIGN.md,
// STATUS Phase C: "a malformed rule into a hole rule"). Nothing in
// it compiles — an empty pattern is neither operator- nor
// sequence-shaped — but `ruleIssues` still reports it, so the
// mistake shows up in checking rather than disappearing.
def readRuleOrHole(text: String): Rule =
  readRule(text).getOrElse(Rule(Pattern(Nil), Meaning.Phrase(Nil)))

// A bare pattern — how the binding list names a rule (`a * b`).
def readPattern(text: String): Pattern = {
  val tree = many(NodeData.StringData("pattern"))(anyToken)(In(text, ruleLexicon), 0).tree
  Pattern(pieces(tokensIn(tree)))
}

private def isToken(t: PTree): Boolean = t.data match {
  case NodeData.StringData(kind) => t.kids.isEmpty && ruleTokenKinds(kind)
  case _                         => false
}

private def isTagged(t: PTree): Boolean = t.data.isInstanceOf[NodeData.StringData]

private def tagOf(t: PTree): String = t.data match {
  case NodeData.StringData(s) => s
  case _                      => ""
}

// The tokens under a node, in order, each with its absolute start —
// a kid's `from` is relative to its parent, so the base accumulates
// on the way down.
private def tokensIn(t: PTree): List[(Int, PTree)] = {
  def go(t: PTree, base: Int): List[(Int, PTree)] =
    if (isToken(t)) List((base + t.from, t))
    else t.kids.flatMap(k => go(k, base + t.from))
  go(t, 0)
}

// Placeholder words become placeholders, everything else literal; a piece
// is glued when no whitespace separates it from the previous one. The
// first piece is never glued — nothing precedes it.
private def pieces(tokens: List[(Int, PTree)]): List[Piece] =
  tokens.zipWithIndex.map { case ((start, t), i) =>
    val glued = i > 0 && {
      val (previousStart, previous) = tokens(i - 1)
      previousStart + previous.length == start
    }
    tagOf(t) match {
      case "word" if isPlaceholderWord(t.text) => Piece.Placeholder(t.text, glued)
      case "quoted"                            => Piece.Literal(unquote(t.text), glued)
      case _                                   => Piece.Literal(t.text, glued)
    }
  }

private def readMeaning(t: PTree): Meaning = tagOf(t) match {
  case "build" =>
    val arguments = t.kids(2)
    val list =
      if (tagOf(arguments) != "arguments") Nil
      else arguments.kids(0) :: arguments.kids(1).kids.map(_.kids(1))
    Meaning.Build(t.kids(0).text, list.map(readMeaning))
  case "use" => Meaning.Use(t.kids(0).text)
  case _     => Meaning.Phrase(pieces(tokensIn(t)))
}

// `a, b: number; c: whole number` — one constraint per group.
private def readWhere(t: PTree): Either[String, List[Constraint]] = {
  val groups = t.kids(1) :: t.kids(2).kids.map(_.kids(1))
  groups.foldRight[Either[String, List[Constraint]]](Right(Nil)) { (g, acc) =>
    val names = tokensIn(g.kids(0)).map(_._2.text).filterNot(_ == ",")
    val typed = g.kids(1)
    val kind = if (tagOf(typed) == "typed") tokensIn(typed.kids(1)).map(_._2.text) else Nil
    if (tagOf(typed) != "typed") Left("where needs 'placeholders: kind'")
    else if (names.isEmpty || kind.isEmpty) Left("incomplete where clause")
    else acc.map(Constraint(names, kind.mkString(" ")) :: _)
  }
}
