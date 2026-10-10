package opelan.foundation.language

import opelan.foundation.document._

// The integer-expression language, defined in Scala. `typeRef` names the
// document that will eventually hold this definition (bootstrapping);
// until then the registry resolves it to this implementation.
object ExprLanguage extends Language {

  val typeRef = ExternalNodeReference("opelan:languages/int-expression", 0, 0)

  def name = "Integer Expressions"

  def views = List("print", "eval")

  def render(view: String, doc: Document[NodeData]): Either[String, DetachedNode[NodeData]] =
    view match {
      case "print" => content(doc).map(id => printTree(readLocExpr(doc, id)))
      case "eval"  => content(doc).map(id => evalTree(readLocExpr(doc, id)))
      case v       => Left(s"$name has no '$v' view")
    }

  // The text syntax is total: any input parses, producing holes where the
  // text does not fit the grammar.
  override def parse(text: String): Option[DetachedNode[NodeData]] =
    Some(Expr.toDetached(parseExpr(text).expr))

  private def content(doc: Document[NodeData]): Either[String, Int] =
    contentId(doc).toRight(s"$name: typed document has no content")

  // A typed sample document: (1 + 2) - (3 - 4)  ==  4
  def sampleDoc: Document[NodeData] =
    makeTyped(
      typeRef,
      NodeData.StringData("expr sample"),
      Expr.toDetached(
        Expr.Sub(
          Expr.Add(Expr.Lit(1), Expr.Lit(2)),
          Expr.Sub(Expr.Lit(3), Expr.Lit(4)))))
}
