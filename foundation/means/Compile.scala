package opelan.foundation.means

import opelan.foundation.document._
import opelan.foundation.grammar._

// Compile a rule set to a parser (DESIGN.md §4, *Engine*): operator-
// shaped rules join the operator table in binding-list order; every
// other rule compiles to a sequence and serves as an operand.
//
//   compileRules(exprRules, leafAtom, termTag, "expr", lexicals)
//
// A named leaf production, as compileRules sees it: `where` binds a
// placeholder to one by name — a sequence placeholder then parses
// the production's token, and an operator operand bound to it is
// checked after binding (a violation surfaces the whole application
// as a hole). `operand` marks the language's leaf vocabulary —
// terms that stand alone as operands, as the def's vocabulary
// declares them.
case class LexicalTerm(
    parser: Parser,
    operand: Boolean,
    check: NodeData => Boolean)

// `atom` supplies the operand fallback rules don't yet describe —
// the recovering `unrecognized` run — while leaf tokens and
// anything narrower comes from the rules themselves: `where`
// constraints bind placeholders to named leaf productions in
// `lexicals` (DefLanguage's impls), the language's leaf terms are
// operands, and a lone-placeholder rule bound that way
// (`n means number(n) where n: digits`) is an operand production of
// its own. `tag` turns a constructor name into node
// data (DefLanguage.termTag), so built nodes name their construct
// by reference. `name` is the rule the parser memoizes under:
// every unbound placeholder recurses through it, so a language
// change can invalidate by name like the combinator grammar's
// `named`/`use` pair. Callers wrap the result in `phrase` for
// to-EOF recovery, like any grammar.
//
// Compiles as far as it can (Never Broken): rules the binding list
// forgot join loosest, a pattern a sequence can't compile (leading
// placeholder — `a , b , c` would left-recurse) is skipped, and a
// malformed meaning degrades to a hole at apply time. Associativity
// defaults to left until worked examples override it. A `where`
// term `lexicals` doesn't name leaves the placeholder unconstrained,
// and `where` on an operator's operands is carried but not yet
// enforced — operands there parse through the whole grammar.
def compileRules(
    set: RuleSet,
    atom: Parser,
    tag: String => NodeData,
    name: String,
    lexicals: Map[String, LexicalTerm] = Map.empty): Parser = {
  // `self` is the parser placeholders recurse through — the named
  // rule, so recursion memoizes and records the call for invalidation
  // (the same shape as `named`/`use` in the combinator form). The
  // inner lambda keeps its initialization trivial: `compiled` forces
  // `operand`, which names `self`, so `self` must be set first.
  lazy val self: Parser = rule(name)((in, pos) => compiled(in, pos))
  lazy val compiled: Parser = operatorTable(operand, operatorTiers)
  lazy val operand: Parser =
    choice(sequences.map(sequenceParser) ++ leafOperands ++
      lexicals.values.collect { case t if t.operand => t.parser } :+ atom*)
  lazy val operatorTiers: List[List[Operator]] =
    orderedOperatorRules(set).map(_.filterNot(operandBound))
      .filter(_.nonEmpty).map(_.map(toOperator))
  lazy val sequences: List[Rule] =
    set.rules.filter(r => isSequencePattern(r.pattern) || operandBound(r))
  lazy val leafOperands: List[Parser] =
    set.rules.flatMap(leafOperand)

  // This rule's `where` constraints resolved to leaf terms — a
  // placeholder's bound production when the constraint names a term
  // `lexicals` knows, the constraint dropped otherwise.
  def boundLexicals(theRule: Rule): Map[String, LexicalTerm] =
    theRule.where.flatMap(c => lexicals.get(c.term).toList.flatMap(t =>
      c.placeholders.map(_ -> t))).toMap

  // A literal-led rule whose placeholders are all `where`-bound to
  // leaf productions is lexical, not an operator — `-n means
  // literal(neg(n)) where n: number` is an operand production like
  // the hand-written signum: its placeholders read tokens, never
  // sub-expressions, so no precedence applies to them. Operator-
  // shaped by its edges, it compiles to a sequence instead.
  def operandBound(theRule: Rule): Boolean =
    isOperatorPattern(theRule.pattern) &&
      theRule.pattern.pieces.head.isInstanceOf[Piece.Literal] &&
      theRule.pattern.placeholders.forall(boundLexicals(theRule).contains)

  // A lone-placeholder rule bound to a leaf production is an
  // operand: the production's token applies, then the meaning —
  // `n means n` passes the token's leaf through `use`. An unbound
  // lone placeholder stays dead rather than recursing into itself.
  def leafOperand(theRule: Rule): Option[Parser] =
    theRule.pattern.pieces match {
      case List(Piece.Placeholder(name, _)) =>
        boundLexicals(theRule).get(name).map { bound =>
          (in: In, pos: Int) => {
            val applied = bound.parser(in, pos)
            if (!applied.ok) applied
            else applied.copy(tree = applyMeaning(theRule.meaning,
              Map(name -> applied.tree), in, applied.tree.from,
              applied.tree.to, self, tag))
          }
        }
      case _ => None
    }

  def toOperator(theRule: Rule): Operator = {
    val names = theRule.pattern.placeholders
    val bound = boundLexicals(theRule)
    Operator(
      name = theRule.pattern.key.mkString(" "),
      shape = theRule.shape match {
        case Shape.Infix   => OperatorShape.Infix
        case Shape.Postfix => OperatorShape.Postfix
        case _             => OperatorShape.Prefix
      },
      literals = theRule.pattern.pieces.collect {
        case Piece.Literal(text, _) => text },
      build = (operands, in, from, to) =>
        // An operand bound against a `where` term is checked after
        // binding: a violation makes the whole application the hole.
        if (names.zip(operands).exists { case (n, t) =>
              bound.get(n).exists(lt => !lt.check(t.data)) })
          gapNode(in, from, to)
        else applyMeaning(theRule.meaning, names.zip(operands).toMap,
          in, from, to, self, tag))
  }

  def sequenceParser(theRule: Rule): Parser =
    sequenceRule(theRule, self, tag, boundLexicals(theRule))

  self
}

// Operator-shaped rules in binding order: each tier's patterns
// resolve to their rules, and any operator the list forgot appends
// as one loosest tier — it compiles rather than disappearing. Also
// the printer's binding-strength table (printWith).
private[means] def orderedOperatorRules(set: RuleSet): List[List[Rule]] = {
  val operators = set.rules.filter(r => isOperatorPattern(r.pattern))
  val byKey = operators.map(r => r.pattern.key -> r).toMap
  val tiered = set.tiers.map(t => t.patterns.flatMap(p => byKey.get(p.key)))
  val placed = tiered.flatten.map(_.pattern.key).toSet
  val untiered = operators.filterNot(r => placed.contains(r.pattern.key))
  if (untiered.isEmpty) tiered else tiered :+ untiered
}
