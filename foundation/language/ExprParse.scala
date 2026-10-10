package opelan.foundation.language

import Expr._

// A total, recovering parser for the expression cell syntax: every input
// produces an Expr. Text that fits no production is kept verbatim in
// Hole nodes, so typing continues to heal the tree rather than fail.
//
// Grammar (left-associative):
//   expr   := term (('+' | '-') term)*
//   term   := factor (('*' | '/') factor)*
//   factor := INT | FLOAT | ref:N | '?' | '(' expr ')' | junk-hole
//
// Recovery rules:
//   - operand expected but an op, ')' or EOF found -> empty hole
//   - junk, or a stray sign not followed by a number -> hole covering
//     the maximal run of non-structural tokens
//   - missing ')' -> the group closes implicitly at EOF
//   - tokens left after a complete expr (adjacency, stray ')') -> the
//     whole level merges into one hole covering its text
//   - a paren group whose contents degenerated to a hole -> the hole's
//     text covers the parens too ("(1 2)" stays "(1 2)", not "1 2")

case class ExprSpan(from: Int, to: Int)

// The parsed expression plus a span map: paths (child indexes from the
// expression root) to source ranges. Enables mapping a text edit onto
// the affected node later.
case class ExprParseResult(expr: Expr, spans: Map[List[Int], ExprSpan])

def parseExpr(input: String): ExprParseResult = {
  val p = new P(input, lex(input))
  val (e, spans) = p.exprLevel(inGroup = false)
  ExprParseResult(e, spans.toMap)
}

// Spans are collected bottom-up with paths relative to the subtree
// root; each subtree's own span is the entry at path Nil.
private type Spans = List[(List[Int], ExprSpan)]

private class P(input: String, toks: Vector[Tok]) {
  private var pos = 0

  private def peek: Option[Tok] = toks.lift(pos)
  private def peek2: Option[Tok] = toks.lift(pos + 1)
  private def atEnd: Boolean = pos >= toks.length
  private def atRParen: Boolean = peek.exists(_.isInstanceOf[Tok.RParen])
  private def next(): Tok = { val t = toks(pos); pos += 1; t }
  private def leaf(e: Expr, from: Int, to: Int): (Expr, Spans) =
    (e, List(Nil -> ExprSpan(from, to)))

  private def consumeJunk(from: Int): (Expr, Spans) = {
    var to = next().to
    var more = true
    while (more)
      peek match {
        case Some(Tok.IntTok(_, _)) | Some(Tok.FloatTok(_, _)) |
             Some(Tok.RefTok(_, _)) | Some(Tok.JunkTok(_, _)) |
             Some(Tok.HoleTok(_, _)) =>
          to = next().to
        case _ => more = false
      }
    leaf(Hole(input.substring(from, to)), from, to)
  }

  private def factor(): (Expr, Spans) = peek match {
    case Some(t: Tok.IntTok) =>
      next(); leaf(Lit(input.substring(t.from, t.to).toInt), t.from, t.to)
    case Some(t: Tok.FloatTok) =>
      next(); leaf(FLit(input.substring(t.from, t.to).toDouble), t.from, t.to)
    case Some(t: Tok.RefTok) =>
      next(); leaf(Ref(input.substring(t.from + 4, t.to).toInt), t.from, t.to)
    case Some(t: Tok.HoleTok) =>
      next(); leaf(Hole(""), t.from, t.to)
    case Some(t: Tok.LParen) =>
      next()
      val (inner, ispans) = exprLevel(inGroup = true)
      val end = if (atRParen) next().to else input.length
      inner match {
        case _: Hole => leaf(Hole(input.substring(t.from, end)), t.from, end)
        case _       => (inner, (Nil -> ExprSpan(t.from, end)) :: ispans.tail)
      }
    case Some(t: Tok.OpTok) =>
      (t.op, peek2) match {
        case ('-' | '+', Some(u))
            if u.isInstanceOf[Tok.IntTok] || u.isInstanceOf[Tok.FloatTok] =>
          next(); next()
          leaf(signed(t.op, input.substring(u.from, u.to), u), t.from, u.to)
        case _ => consumeJunk(t.from)
      }
    case Some(_: Tok.JunkTok) => consumeJunk(peek.get.from)
    case _ => // ')' or EOF where an operand was expected
      val p = peek.map(_.from).getOrElse(input.length)
      leaf(Hole(""), p, p)
  }

  // A sign applied to a numeric literal.
  private def signed(op: Char, digits: String, t: Tok): Expr = {
    val neg = op == '-'
    t match {
      case _: Tok.FloatTok => FLit(if (neg) -digits.toDouble else digits.toDouble)
      case _               => Lit(if (neg) -digits.toInt else digits.toInt)
    }
  }

  // The shared shape of one precedence level: a left-associative fold of
  // operands separated by ops in `ops`, spans re-pathed under each new
  // root.
  private def level(
      ops: Set[Char], operand: () => (Expr, Spans)): (Expr, Spans) = {
    var (acc, aspans) = operand()
    while (peek.exists(t =>
        t.isInstanceOf[Tok.OpTok] && ops(t.asInstanceOf[Tok.OpTok].op))) {
      val op = next().asInstanceOf[Tok.OpTok].op
      val (rhs, rspans) = operand()
      val span = ExprSpan(aspans.head._2.from, rspans.head._2.to)
      aspans = (Nil -> span) ::
        (aspans.map { case (p, s) => (0 :: p, s) } ++
         rspans.map { case (p, s) => (1 :: p, s) })
      acc = op match {
        case '+' => Add(acc, rhs)
        case '-' => Sub(acc, rhs)
        case '*' => Mul(acc, rhs)
        case '/' => Div(acc, rhs)
      }
    }
    (acc, aspans)
  }

  private def term(): (Expr, Spans) = level(Set('*', '/'), () => factor())

  def exprLevel(inGroup: Boolean): (Expr, Spans) = {
    val (acc, aspans) = level(Set('+', '-'), () => term())
    if (atEnd || (inGroup && atRParen)) (acc, aspans)
    else {
      // Tokens remain that no rule could place: merge the whole level.
      val from = aspans.head._2.from
      var to = aspans.head._2.to
      while (!atEnd && !(inGroup && atRParen)) to = next().to
      leaf(Hole(input.substring(from, to)), from, to)
    }
  }
}
