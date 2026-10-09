package opelan.foundation.language

import opelan.foundation.document._

// The abstract syntax of the integer-expression language: literals plus
// addition and subtraction. Grouping is structural — the document tree
// itself is the parenthesization — so the AST needs no paren node.
//
// Hole is a typed gap: text that did not parse, kept verbatim so the cell
// can heal on later edits (see ExprParse). An empty hole means "operand
// expected here".
enum Expr {
  case Lit(value: Int)
  case Add(left: Expr, right: Expr)
  case Sub(left: Expr, right: Expr)
  case Hole(text: String)
}

object Expr {
  // Content encoding: an IntData leaf is a literal; a "add"/"sub" node
  // with exactly two children is a binary operation; a GapData node is a
  // hole.
  val AddTag = "add"
  val SubTag = "sub"

  // Evaluation is partial: a hole anywhere in the expression blocks it.
  def eval(e: Expr): Either[String, Int] = e match {
    case Lit(v)    => Right(v)
    case Add(l, r) => for { a <- eval(l); b <- eval(r) } yield a + b
    case Sub(l, r) => for { a <- eval(l); b <- eval(r) } yield a - b
    case Hole(t)   => Left(s"expression contains a hole${if (t.isEmpty) "" else s": $t"}")
  }

  // Infix text with minimal parentheses: operations are left-associative,
  // so only a compound right operand ever needs parens.
  def print(e: Expr): String = Frag.flatten(printFrags(e))

  // The printed form as fragments: holes stay distinct so views can style
  // them inline. Flattened, a hole prints its verbatim text (or '?' when
  // empty), so printed holes reparse as holes.
  def printFrags(e: Expr): List[Frag] = e match {
    case Lit(v)    => List(Frag.Text(v.toString))
    case Add(l, r) => printFrags(l) ++ (Frag.Text(" + ") +: printRightFrags(r))
    case Sub(l, r) => printFrags(l) ++ (Frag.Text(" - ") +: printRightFrags(r))
    case Hole(t)   => List(Frag.Hole(t))
  }

  private def printRightFrags(e: Expr): List[Frag] = e match {
    case Add(_, _) | Sub(_, _) => Frag.Text("(") +: printFrags(e) :+ Frag.Text(")")
    case other                 => printFrags(e)
  }

  // The verbatim text of the first hole blocking evaluation — the spot a
  // view can surface as "waiting on". None when eval succeeds.
  def blockingHole(e: Expr): Option[String] = e match {
    case Lit(_)    => None
    case Add(l, r) => blockingHole(l).orElse(blockingHole(r))
    case Sub(l, r) => blockingHole(l).orElse(blockingHole(r))
    case Hole(t)   => Some(t)
  }

  def toDetached(e: Expr): DetachedNode[NodeData] = e match {
    case Lit(v)   => DetachedNode.leaf(NodeData.IntData(v))
    case Hole(t)  => DetachedNode.leaf(NodeData.GapData(t))
    case Add(l, r) =>
      DetachedNode(NodeData.StringData(AddTag), List(toDetached(l), toDetached(r)))
    case Sub(l, r) =>
      DetachedNode(NodeData.StringData(SubTag), List(toDetached(l), toDetached(r)))
  }

  // Interpret the subtree rooted at nodeId as an expression. The read is
  // recovering: nodes that are not valid expression syntax (junk data,
  // wrong arity, literals with children) read as holes, so malformed
  // subtrees surface as gaps rather than errors — the same totality the
  // text parser gives typed input.
  def fromDocument(doc: Document[NodeData], nodeId: Int): Either[String, Expr] =
    doc.getNode(nodeId) match {
      case None => Left(s"Expr: node id $nodeId does not exist in document")
      case Some(node) =>
        node.data match {
          case NodeData.IntData(v) if node.childIds.isEmpty => Right(Lit(v))
          case NodeData.GapData(t) => Right(Hole(t))
          case NodeData.StringData(AddTag) if node.childIds.length == 2 =>
            binOp(node.childIds, doc, Add.apply)
          case NodeData.StringData(SubTag) if node.childIds.length == 2 =>
            binOp(node.childIds, doc, Sub.apply)
          case other => Right(Hole(holeText(other)))
        }
    }

  // What a malformed node contributes to a hole: its surface text —
  // strings unquoted, so hole text reparses as the same junk.
  private def holeText(data: NodeData): String = data match {
    case NodeData.StringData(s) => s
    case other                  => NodeDataText.show(other)
  }

  // Arity is guaranteed by the caller's case guard.
  private def binOp(
      childIds: List[Int],
      doc: Document[NodeData],
      op: (Expr, Expr) => Expr): Either[String, Expr] =
    for {
      left <- fromDocument(doc, childIds(0))
      right <- fromDocument(doc, childIds(1))
    } yield op(left, right)
}
