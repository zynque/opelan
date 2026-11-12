package opelan.examples.expr

// The abstract syntax of the expression language: int/float literals,
// the four arithmetic operations, references to other nodes in the
// enclosing document (a cell pulls the referenced node's value), and
// gaps. Grouping is structural — the document tree itself is the
// parenthesization — so the AST needs no paren node.
//
// A Gap is the AST's typed hole: text that did not parse, kept verbatim
// so the cell can heal on later edits. An empty gap
// means "operand expected here".
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
  case Gap(text: String)
}

object Expr {
  // Content encoding: an IntData/FloatData leaf is a literal; an
  // InternalNodeRef leaf (to a node that isn't an import) is a reference;
  // a node applying the + - * / constructs — its data refers to the
  // literal-piece declaration in lang: expression, whose rule meaning
  // interprets it as add/sub/mul/div at eval — with exactly two children
  // is a binary operation; a GapData node is a gap. The tags below are
  // the declared construct names, used only to look the declarations up
  // (exprTerms).
  val AddTag = "+"
  val SubTag = "-"
  val MulTag = "*"
  val DivTag = "/"

  // Infix text with minimal parentheses: `*` and `/` bind tighter than
  // `+` and `-`, and every level is left-associative, so a compound right
  // operand at the same or lower precedence needs parentheses. A gap prints
  // its verbatim text (or '?' when empty), so printed gaps reparse.
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
      case Gap(t)   => if (t.isEmpty) "?" else t
      case Add(l, r) => s"${pp(l, 1)} + ${pp(r, 2)}"
      case Sub(l, r) => s"${pp(l, 1)} - ${pp(r, 2)}"
      case Mul(l, r) => s"${pp(l, 2)} * ${pp(r, 3)}"
      case Div(l, r) => s"${pp(l, 2)} / ${pp(r, 3)}"
    }
    if (prec(e) < ctx) s"($body)" else body
  }
}
