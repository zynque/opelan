package opelan.foundation.means

import opelan.foundation.document.{ExternalNodeReference, NodeData}
import opelan.foundation.grammar.PTree

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

// What a pattern stands for — a term, evaluated (DESIGN.md §4, *A
// meaning is a term, evaluated*): `add(a, b)` applies a construct of
// the defined language; a bare `a` reads as `use(a)`, the bound tree
// itself; a phrase like `a / 100` desugars at entry — read with the
// language's own rules into term trees (`use`, constructs, `quote`),
// never stored as text. `use`, `source`, `builtin`, `quote`,
// `literal` are meaning-language builtins — `lang:`'s own
// constructs: they operate on bound values and never materialize a
// node of their own.
//
// Names here are labels, as in NodeTerm and GrammarDef: in a `lang:`
// document a constructor is a ref to its term's vocabulary entry and a
// placeholder use an internal ref to the pattern's placeholder node
// (DESIGN.md §4, *Everything by reference*). `reference` carries that
// identity once a rule is read from a document — a construct's
// vocabulary entry, a placeholder's pattern piece; rules written in code or text
// have none until encoded, and a placeholder resolves by name within
// its rule. `quoted` holds a `quote` term's subtree — concrete content
// a phrase desugared to, stored as a parse tree whose kids' offsets
// are relative, ready to re-span onto a match. A quoted hole is the
// stored form of a phrase the set couldn't yet read (Never Broken).
case class Meaning(
    tag: String,
    arguments: List[Meaning] = Nil,
    reference: Option[ExternalNodeReference] = None,
    quoted: Option[PTree] = None)

object Meaning {
  // The meaning-language's vocabulary — `lang:`'s own constructs, never
  // the defined language's terms (so a `use` must never reach
  // vocabularyFrom). `builtin` is reserved for evaluation-time meanings
  // (`add(a, b) means builtin num-add`, Phase D); `quote` carries a
  // desugared phrase's concrete subtree — including the hole a phrase
  // that didn't read lands as; `literal` evaluates a value
  // term to leaf data (`-n means literal(neg(n))`, parse-time values).
  val builtins: Set[String] =
    Set("use", "source", "builtin", "quote", "literal")

  def isBuiltin(tag: String): Boolean = builtins(tag)
  def isConstruct(m: Meaning): Boolean = !builtins(m.tag)

  // `add(a, b)` — a construct of the defined language.
  def construct(tag: String, arguments: List[Meaning] = Nil,
      reference: Option[ExternalNodeReference] = None): Meaning =
    Meaning(tag, arguments, reference)

  // A bare name's term — `use`/`source`/`builtin`'s operand; its
  // `reference` is the pattern piece's identity once rules are read
  // from a document.
  def nameTerm(name: String): Meaning = Meaning(name)

  def use(name: String): Meaning = Meaning("use", List(nameTerm(name)))
  def source(name: String): Meaning = Meaning("source", List(nameTerm(name)))

  // A quoted subtree — concrete content a phrase desugared to
  // (`a / 100`'s `100`). At apply the stored subtree is the value,
  // re-spanned onto the match.
  def quote(node: PTree): Meaning = Meaning("quote", quoted = Some(node))

  // A quoted hole — the stored form of a meaning a reader couldn't
  // interpret (a phrase with no language context, or one the set can't
  // yet read): it prints back as its text and heals by re-reading.
  def hole(text: String): Meaning =
    quote(PTree(NodeData.GapData(text), text, 0, text.length))

  // The name a bare term carries — `use`/`source`'s operand — or None
  // for a term that isn't a name leaf.
  def nameOf(m: Meaning): Option[String] =
    if (isBuiltin(m.tag) || m.arguments.nonEmpty) None else Some(m.tag)

  // The placeholder names a meaning binds — `use`/`source` operands
  // (a `quote` holds concrete content only, never bindings).
  def placeholders(meaning: Meaning): List[String] = meaning.tag match {
    case "use" | "source" => meaning.arguments.flatMap(nameOf)
    case _                => meaning.arguments.flatMap(placeholders)
  }
}

// `where c, r: whole number` — what may fill the named placeholders.
// The term is free text until a type vocabulary exists.
case class Constraint(placeholders: List[String], term: String)

// `id` is the rule's stable identity — its `rule` node's id in the
// `lang:` document it was decoded from (DESIGN §4, *Incremental
// parsing*: the memo key survives renames and only real changes
// invalidate). Rules written in code or read from text have none and
// are named by pattern key.
case class Rule(
    pattern: Pattern,
    meaning: Meaning,
    where: List[Constraint] = Nil,
    id: Option[Int] = None) {
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
