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
  // so only a compound right operand ever needs parens. A hole prints its
  // verbatim text (or '?' when empty), so printed holes reparse as holes.
  def print(e: Expr): String = e match {
    case Lit(v)    => v.toString
    case Add(l, r) => s"${print(l)} + ${printRight(r)}"
    case Sub(l, r) => s"${print(l)} - ${printRight(r)}"
    case Hole(t)   => if (t.isEmpty) "?" else t
  }

  private def printRight(e: Expr): String = e match {
    case Add(_, _) | Sub(_, _) => s"(${print(e)})"
    case other                 => print(other)
  }

  def toDetached(e: Expr): DetachedNode[NodeData] = e match {
    case Lit(v)   => DetachedNode.leaf(NodeData.IntData(v))
    case Hole(t)  => DetachedNode.leaf(NodeData.GapData(t))
    case Add(l, r) =>
      DetachedNode(NodeData.StringData(AddTag), List(toDetached(l), toDetached(r)))
    case Sub(l, r) =>
      DetachedNode(NodeData.StringData(SubTag), List(toDetached(l), toDetached(r)))
  }

  // Interpret the subtree rooted at nodeId as an expression.
  def fromDocument(doc: Document[NodeData], nodeId: Int): Either[String, Expr] =
    doc.getNode(nodeId) match {
      case None => Left(s"Expr: node id $nodeId does not exist in document")
      case Some(node) =>
        node.data match {
          case NodeData.IntData(v) if node.childIds.isEmpty => Right(Lit(v))
          case NodeData.IntData(_) =>
            Left(s"Expr: literal node $nodeId has children")
          case NodeData.GapData(t) => Right(Hole(t))
          case NodeData.StringData(AddTag) => binOp(nodeId, node.childIds, doc, Add.apply)
          case NodeData.StringData(SubTag) => binOp(nodeId, node.childIds, doc, Sub.apply)
          case other =>
            Left(s"Expr: node $nodeId is not an expression (${Show.showNodeData(other)})")
        }
    }

  private def binOp(
      nodeId: Int,
      childIds: List[Int],
      doc: Document[NodeData],
      op: (Expr, Expr) => Expr): Either[String, Expr] =
    childIds match {
      case List(l, r) =>
        for {
          left <- fromDocument(doc, l)
          right <- fromDocument(doc, r)
        } yield op(left, right)
      case _ =>
        Left(s"Expr: operator node $nodeId must have exactly 2 children, has ${childIds.length}")
    }
}
