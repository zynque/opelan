package opelan.foundation.grammar

import opelan.foundation.document._

// A generalized operator table: precedence climbing over a binding
// list, covering prefix, postfix, and left-, right-, and
// non-associative infix operators — the compile target of `means`
// rules (foundation/means), of which `precedenceTable`
// (single-token left-associative infix only) is the special case.

// How `a op b op c` groups at one binding level: `a ^ b ^ c` wants
// Right, `a ~ b ~ c` is a Non-associative mistake (left unparsed —
// the caller's recovery places it).
enum Assoc { case Left, Right, Non }

// Where an operator's operands sit relative to its literals:
//   Prefix   -a        literals lead, one operand follows
//   Postfix  a %       the operand leads, literals follow
//   Infix    a + b     operand, literals, operand
enum OperatorShape { case Prefix, Postfix, Infix }

// One compiled operator: its literal texts in pattern order (`a is
// not b` has two), its associativity (infix only), and `build` —
// given the operand trees in pattern order, the input, and the
// match's span, produces the node. `name` is the rule's identity,
// for memoization and messages.
case class Operator(
    name: String,
    shape: OperatorShape,
    literals: List[String],
    assoc: Assoc = Assoc.Left,
    build: (List[PTree], In, Int, Int) => PTree)

// `tiers` is the binding list, tightest first; the result parses at
// the loosest level. `atom` — by name, so it may refer back to the
// result through a named rule — parses operands tighter than any
// operator (leaves, brackets, keyword-led forms).
def operatorTable(atom: => Parser, tiers: List[List[Operator]]): Parser = {
  val table = tiers.toVector
  lazy val lazyAtom: Parser = atom

  // An operand as seen at level `i`: a tier-i prefix operator whose
  // operand is itself a level-i operand (so `--a` nests), or the
  // next tighter level — bottoming out at `atom`.
  def operand(i: Int): Parser = (in, pos) => {
    var out: Option[POut] = None
    var seen = pos
    val prefixes = table(i).iterator.filter(_.shape == OperatorShape.Prefix)
    while (out.isEmpty && prefixes.hasNext) {
      val op = prefixes.next()
      matchLiterals(op.literals, in, pos) match {
        case (Some((cur, start)), s) =>
          seen = seen.max(s)
          val r = operand(i)(in, cur)
          seen = seen.max(r.examined)
          out = Some(POut(r.pos,
            op.build(List(r.tree), in, start, r.tree.to.max(cur)),
            ok = true, seen))
        case (None, s) => seen = seen.max(s)
      }
    }
    out.getOrElse {
      val r = (if (i == 0) lazyAtom else level(i - 1))(in, pos)
      r.copy(examined = r.examined.max(seen))
    }
  }

  // Level `i`: an operand, then tier-i infix/postfix operators — a
  // left-associative loop like chainLeft; right-associative operands
  // recurse to this level; non-associative ones take a tighter
  // operand and then refuse a second operator at this level.
  def level(i: Int): Parser = (in, pos) => {
    val first = operand(i)(in, pos)
    if (!first.ok) first
    else {
      var acc = first.tree
      var cur = first.pos
      var seen = first.examined
      var more = true
      while (more) {
        // A miss is still lookahead: the probe that decided no
        // operator starts here read input, so its examined counts.
        val (hit, opSeen) = matchOperator(i, in, cur)
        seen = seen.max(opSeen)
        hit match {
          case Some((op, litEnd)) =>
            op.shape match {
              case OperatorShape.Postfix =>
                acc = op.build(List(acc), in, acc.from, litEnd)
                cur = litEnd
                more = op.assoc != Assoc.Non
              case OperatorShape.Infix =>
                val rhs = (if (op.assoc == Assoc.Right) level(i)
                           else operand(i))(in, litEnd)
                seen = seen.max(rhs.examined)
                // An operator with no literals (juxtaposition, `n x`)
                // applies only when an operand genuinely follows.
                if (op.literals.isEmpty && rhs.pos <= litEnd) more = false
                else {
                  acc = op.build(
                    List(acc, rhs.tree), in, acc.from, rhs.tree.to)
                  cur = rhs.pos
                  more = rhs.ok && op.assoc != Assoc.Non
                }
              case _ => ()
            }
          case None => more = false
        }
      }
      POut(cur, acc, ok = true, seen)
    }
  }

  // The first tier-i infix/postfix operator whose literals start at
  // `cur`, with its end position — plus how far deciding read,
  // whether one matched or not.
  def matchOperator(
      i: Int, in: In, cur: Int): (Option[(Operator, Int)], Int) = {
    var seen = cur
    var found: Option[(Operator, Int)] = None
    val ops = table(i).iterator.filter(_.shape != OperatorShape.Prefix)
    while (found.isEmpty && ops.hasNext) {
      val op = ops.next()
      val (m, s) = matchLiterals(op.literals, in, cur)
      seen = seen.max(s)
      m.foreach { case (end, _) => found = Some((op, end)) }
    }
    (found, seen)
  }

  if (table.isEmpty) lazyAtom else level(table.length - 1)
}

// The literal texts in order, each matched against the next token's
// text — spacing-insensitive (a pattern's `glued` is printing
// guidance, not a match constraint). Result: the end position and
// the first literal's start, plus how far matching read.
private def matchLiterals(
    literals: List[String], in: In, pos: Int): (Option[(Int, Int)], Int) = {
  var cur = pos
  var seen = pos
  var start = -1
  var ok = true
  val it = literals.iterator
  while (ok && it.hasNext) {
    val text = it.next()
    val s = scanAt(in, cur)
    seen = seen.max(s.examined)
    s.token match {
      case Some(t) if t.text == text =>
        if (start < 0) start = t.from
        cur = t.to
      case _ => ok = false
    }
  }
  (if (ok) Some((cur, start)) else None, seen)
}
