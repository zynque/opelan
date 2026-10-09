package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import Expr._

class ExprSuite extends munit.FunSuite {

  // (1 + 2) - (3 - 4)  ==  4
  val sample = Sub(Add(Lit(1), Lit(2)), Sub(Lit(3), Lit(4)))

  test("eval computes integer results") {
    assertEquals(eval(sample), Right(4))
    assertEquals(eval(Lit(-7)), Right(-7))
    assertEquals(eval(Sub(Sub(Lit(1), Lit(2)), Lit(3))), Right(-4))
  }

  test("eval is blocked by holes") {
    assert(eval(Add(Lit(1), Hole("x"))).isLeft)
  }

  test("print uses minimal parentheses") {
    assertEquals(print(sample), "1 + 2 - (3 - 4)")
    assertEquals(print(Sub(Sub(Lit(1), Lit(2)), Lit(3))), "1 - 2 - 3")
    assertEquals(print(Add(Lit(1), Add(Lit(2), Lit(3)))), "1 + (2 + 3)")
    assertEquals(print(Lit(42)), "42")
    assertEquals(print(Hole("")), "?")
    assertEquals(print(Add(Lit(1), Hole("foo"))), "1 + foo")
  }

  test("fromDocument reads back what toDetached wrote") {
    val doc = buildDocument(toDetached(sample))
    assertEquals(fromDocument(doc, doc.rootId), Right(sample))
  }

  test("fromDocument reads non-expression nodes as holes") {
    val doc = buildDocument(sl("hello"))
    assertEquals(fromDocument(doc, doc.rootId), Right(Hole("hello")))
  }

  test("fromDocument reads a wrong-arity operator as a hole") {
    val doc = buildDocument(n(s(Expr.AddTag), il(1)))
    assertEquals(fromDocument(doc, doc.rootId), Right(Hole(Expr.AddTag)))
  }

  test("ExprLanguage evaluates a typed document's content") {
    val doc = ExprLanguage.sampleDoc
    assertEquals(languageForDoc(doc), Some(ExprLanguage))
    assertEquals(ExprLanguage.render("eval", doc), Right(List(Frag.Text("4"))))
    assertEquals(
      ExprLanguage.render("print", doc).map(Frag.flatten),
      Right("1 + 2 - (3 - 4)"))
  }

  test("rendered views keep holes as fragments") {
    val doc = applyDocText(ExprLanguage.sampleDoc, "1 + abc").toOption.get
    assertEquals(
      ExprLanguage.render("eval", doc),
      Right(List(Frag.Hole("abc"))))
    assert(ExprLanguage.render("print", doc).exists(_.contains(Frag.Hole("abc"))))
  }

  test("ExprLanguage reports documents without expression content") {
    val raw = beginDocument(s("root"))
    assert(ExprLanguage.render("eval", raw).isLeft)
  }
}
