package opelan.examples

import opelan.foundation.document._

// The abstract syntax of the expression language: int/float literals,
// the four arithmetic operations, references to other nodes in the
// enclosing document (a cell pulls the referenced node's value), and
// holes. Grouping is structural — the document tree itself is the
// parenthesization — so the AST needs no paren node.
//
// Hole is a typed gap: text that did not parse, kept verbatim so the cell
// can heal on later edits (see ExprParse). An empty hole means "operand
// expected here".
enum Expr {
  case Lit(value: Int)
  case FLit(value: Double)
  case Add(left: Expr, right: Expr)
  case Sub(left: Expr, right: Expr)
  case Mul(left: Expr, right: Expr)
  case Div(left: Expr, right: Expr)
  // Stored as InternalNodeRef in the document; evaluated via a resolver
  // since Expr itself cannot see the document.
  case Ref(nodeId: Int)
  case Hole(text: String)
}

object Expr {
  // Content encoding: an IntData/FloatData leaf is a literal; an
  // InternalNodeRef leaf (to a node that isn't an import) is a reference;
  // a node applying the add/sub/mul/div construct — its data refers to
  // the kind's schema entry in lang: expression, through an import —
  // with exactly two children is a binary operation; a GapData node is a
  // hole. The tags below are kind names, used only to look the
  // constructs up (exprKinds).
  val AddTag = "add"
  val SubTag = "sub"
  val MulTag = "mul"
  val DivTag = "div"

  // Evaluation is partial: a hole anywhere blocks it; a Ref blocks it
  // unless the resolver (which can see the document) supplies the
  // referenced node's value; division by zero fails.
  def eval(
      e: Expr,
      resolve: Int => Option[Num] = _ => None): Either[String, Num] = e match {
    case Lit(v)    => Right(Num.I(v))
    case FLit(v)   => Right(Num.F(v))
    case Ref(id)   => resolve(id).toRight(s"unresolved ref: $id")
    case Add(l, r) => bin(l, r, resolve, Num.add)
    case Sub(l, r) => bin(l, r, resolve, Num.sub)
    case Mul(l, r) => bin(l, r, resolve, Num.mul)
    case Div(l, r) =>
      for {
        a <- eval(l, resolve); b <- eval(r, resolve)
        q <- Num.div(a, b).toRight("division by zero")
      } yield q
    case Hole(t)   => Left(s"expression contains a hole${if (t.isEmpty) "" else s": $t"}")
  }

  private def bin(l: Expr, r: Expr, resolve: Int => Option[Num],
      f: (Num, Num) => Num): Either[String, Num] =
    for { a <- eval(l, resolve); b <- eval(r, resolve) } yield f(a, b)

  // Infix text with minimal parentheses: `*` and `/` bind tighter than
  // `+` and `-`, and every level is left-associative, so a compound right
  // operand at the same or lower precedence needs parens. A hole prints
  // its verbatim text (or '?' when empty), so printed holes reparse.
  def print(e: Expr): String = pp(e, 0)

  private def prec(e: Expr): Int = e match {
    case Add(_, _) | Sub(_, _) => 1
    case Mul(_, _) | Div(_, _) => 2
    case _                     => 3
  }

  // `ctx` is the precedence a nested expression must meet to stand
  // unparenthesized: own level for left operands, one tighter for right.
  private def pp(e: Expr, ctx: Int): String = {
    val body = e match {
      case Lit(v)    => v.toString
      case FLit(v)   => v.toString
      case Ref(id)   => s"ref:$id"
      case Hole(t)   => if (t.isEmpty) "?" else t
      case Add(l, r) => s"${pp(l, 1)} + ${pp(r, 2)}"
      case Sub(l, r) => s"${pp(l, 1)} - ${pp(r, 2)}"
      case Mul(l, r) => s"${pp(l, 2)} * ${pp(r, 3)}"
      case Div(l, r) => s"${pp(l, 2)} / ${pp(r, 3)}"
    }
    if (prec(e) < ctx) s"($body)" else body
  }
}
