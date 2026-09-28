package opelan.foundation.language

import opelan.foundation.document._

// The integer-expression language, defined in Scala. `typeRef` names the
// document that will eventually hold this definition (bootstrapping);
// until then the registry resolves it to this implementation.
object ExprLanguage extends Language {

  val typeRef = ExternalNodeReference("opelan:languages/int-expression", 0, 0)

  def name = "Integer Expressions"

  def views = List("print", "eval")

  def render(view: String, doc: Document[NodeData]): Either[String, String] =
    view match {
      case "print" => contentExpr(doc).map(Expr.print)
      case "eval"  => contentExpr(doc).map(e => Expr.eval(e).toString)
      case v       => Left(s"$name has no '$v' view")
    }

  private def contentExpr(doc: Document[NodeData]): Either[String, Expr] =
    Typed.contentId(doc)
      .toRight(s"$name: typed document has no content")
      .flatMap(Expr.fromDocument(doc, _))

  // A typed sample document: (1 + 2) - (3 - 4)  ==  4
  def sampleDoc: Document[NodeData] =
    Typed.make(
      typeRef,
      NodeData.StringData("expr sample"),
      Expr.toDetached(
        Expr.Sub(
          Expr.Add(Expr.Lit(1), Expr.Lit(2)),
          Expr.Sub(Expr.Lit(3), Expr.Lit(4)))))
}
