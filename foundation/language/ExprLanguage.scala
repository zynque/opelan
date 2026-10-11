package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.document.Detached._

// The arithmetic-expression language, defined in Scala. `typeRef` names the
// document that will eventually hold this definition (bootstrapping);
// until then the registry resolves it to this implementation.
object ExprLanguage extends Language {

  val typeRef = ExternalNodeReference("opelan:languages/int-expression", 0, 0)

  def name = "Expressions"

  def views = List("print", "eval")

  def render(view: String, doc: Document[NodeData]): Either[String, DetachedNode[NodeData]] =
    view match {
      case "print" => content(doc).map(id => printTree(readLocExpr(doc, id)))
      case "eval"  => content(doc).map(id => evalTree(doc, readLocExpr(doc, id)))
      case v       => Left(s"$name has no '$v' view")
    }

  // The text syntax is total: any input parses, producing holes where the
  // text does not fit the grammar.
  override def parse(text: String): Option[DetachedNode[NodeData]] =
    Some(exprToDetached(parseExpr(text).expr))

  private def content(doc: Document[NodeData]): Either[String, Int] =
    contentId(doc).toRight(s"$name: typed document has no content")

  // A typed sample document: (1 + 2) - (3 - 4)  ==  4
  def sampleDoc: Document[NodeData] =
    makeTyped(
      typeRef,
      NodeData.StringData("expr sample"),
      exprToDetached(
        Expr.Sub(
          Expr.Add(Expr.Lit(1), Expr.Lit(2)),
          Expr.Sub(Expr.Lit(3), Expr.Lit(4)))))

  def langDoc: Document[NodeData] =
    buildDocument(n(s("lang: expression"),
      sl("the arithmetic-expression language"),
      n(s("nodes"),
        sl("literal — an integer or decimal number"),
        sl("add, sub, mul, div — a binary operation on two children"),
        sl("ref — an internal reference to a node in this document"),
        sl("gap — text the parser could not place, kept as a hole")),
      n(s("views"),
        sl("print — the expression as text, each span traced to its source"),
        sl("eval — the numeric value; division is always decimal"))))
}
