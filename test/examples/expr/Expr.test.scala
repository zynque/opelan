package opelan.examples.expr

import opelan.examples.registryForDoc
import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.document.Detached._
import opelan.foundation.language.applyDocText
import opelan.foundation.means.engine.{evalNode, semanticsOf}
import opelan.foundation.view.ViewTree
import Expr._

class ExprSuite extends munit.FunSuite {

  // (1 + 2) - (3 - 4)  ==  4
  val sample = Sub(Add(Lit(1), Lit(2)), Sub(Lit(3), Lit(4)))

  // The derived evaluator's contract stated against Expr trees:
  // evalNode over the document the AST encodes to — a ref names a real
  // node id in the same document.
  private def eval(e: Expr): Either[(String, Int), NodeData] = {
    val doc = buildDocument(exprToDetached(e))
    evalNode(semanticsOf(exprRules), exprTerms,
      doc, doc.rootId, Set(doc.rootId))
  }

  test("eval computes integer results") {
    assertEquals(eval(sample), Right(NodeData.IntData(4)))
    assertEquals(eval(Lit(-7)), Right(NodeData.IntData(-7)))
    assertEquals(
      eval(Sub(Sub(Lit(1), Lit(2)), Lit(3))), Right(NodeData.IntData(-4)))
  }

  test("eval keeps integers integral and floats decimal") {
    assertEquals(eval(Mul(Lit(4), Lit(5))), Right(NodeData.IntData(20)))
    assertEquals(eval(Mul(Lit(4), FLit(1.2))), Right(NodeData.FloatData(4.8)))
    assertEquals(eval(Add(Lit(1), FLit(0.5))), Right(NodeData.FloatData(1.5)))
  }

  test("division produces a float and fails at zero") {
    assertEquals(eval(Div(Lit(8), Lit(2))), Right(NodeData.FloatData(4.0)))
    assert(eval(Div(Lit(1), Lit(0))).isLeft)
  }

  test("refs evaluate through the document") {
    // add(ref, 1) — post-order ids: the ref leaf is id 0, the literal
    // sibling id 1, the add root id 2. The ref evaluates the literal.
    assertEquals(eval(Add(Ref(1), Lit(1))), Right(NodeData.IntData(2)))
    assert(eval(Ref(7)).isLeft)
    // A ref to itself reports a cycle rather than diverging.
    assert(eval(Ref(0)).isLeft)
  }

  test("eval is blocked by gaps") {
    assert(eval(Add(Lit(1), Gap("x"))).isLeft)
  }

  test("print uses minimal parentheses") {
    assertEquals(print(sample), "1 + 2 - (3 - 4)")
    assertEquals(print(Sub(Sub(Lit(1), Lit(2)), Lit(3))), "1 - 2 - 3")
    assertEquals(print(Add(Lit(1), Add(Lit(2), Lit(3)))), "1 + (2 + 3)")
    assertEquals(print(Lit(42)), "42")
    assertEquals(print(Gap("")), "?")
    assertEquals(print(Add(Lit(1), Gap("foo"))), "1 + foo")
  }

  test("print respects * and / precedence") {
    assertEquals(
      print(Add(Lit(2), Mul(Lit(3), Lit(4)))), "2 + 3 * 4")
    assertEquals(
      print(Mul(Add(Lit(2), Lit(3)), Lit(4))), "(2 + 3) * 4")
    assertEquals(
      print(Div(Mul(Lit(8), Lit(2)), Mul(Lit(4), Lit(1)))),
      "8 * 2 / (4 * 1)")
    assertEquals(print(Mul(FLit(1.5), Lit(2))), "1.5 * 2")
    assertEquals(print(Ref(9)), "ref:9")
  }

  test("fromDocument reads back what toDetached wrote") {
    val doc = buildDocument(exprToDetached(sample))
    assertEquals(exprFromDocument(doc, doc.rootId), Right(sample))
  }

  test("fromDocument reads non-expression nodes as gaps") {
    val doc = buildDocument(sl("hello"))
    assertEquals(exprFromDocument(doc, doc.rootId), Right(Gap("hello")))
  }

  test("fromDocument reads a wrong-arity operator as a gap") {
    val doc = buildDocument(n(s(Expr.AddTag), il(1)))
    assertEquals(exprFromDocument(doc, doc.rootId), Right(Gap(Expr.AddTag)))
  }

  test("toDetached/fromDocument round-trip refs and floats") {
    val e = Mul(Add(FLit(1.5), Ref(3)), Div(Lit(8), Lit(2)))
    val doc = buildDocument(exprToDetached(e))
    assertEquals(exprFromDocument(doc, doc.rootId), Right(e))
    assert(doc.nodes.exists(_.data.isInstanceOf[NodeData.InternalNodeRef]))
    assert(doc.nodes.exists(_.data.isInstanceOf[NodeData.FloatData]))
  }

  test("ExprLanguage evaluates a typed document's content") {
    val doc = ExprLanguage.sampleDoc
    assertEquals(registryForDoc(doc), Some(ExprLanguage))
    assertEquals(
      ExprLanguage.render("eval", doc).map(ViewTree.flatten),
      Right("4"))
    assertEquals(
      ExprLanguage.render("print", doc).map(ViewTree.flatten),
      Right("1 + 2 - (3 - 4)"))
  }

  test("rendered views keep gaps as gap nodes") {
    val doc = applyDocText(ExprLanguage.sampleDoc, registryForDoc, "1 + abc").toOption.get
    assertEquals(
      gaps(ExprLanguage.render("eval", doc).toOption.get), List("abc"))
    assertEquals(
      gaps(ExprLanguage.render("print", doc).toOption.get), List("abc"))
  }

  test("view trees tag emitted pieces with their source nodes") {
    val doc = ExprLanguage.sampleDoc
    val cid = contentId(doc).get
    assertEquals(
      ExprLanguage.render("eval", doc),
      Right(ViewTree.from(cid, ViewTree.txt("4"))))
    // A gap blocking eval points at the node holding it.
    val gappy = applyDocText(ExprLanguage.sampleDoc, registryForDoc, "1 + abc").toOption.get
    val gapId = gappy.nodes.indexWhere(_.data == NodeData.GapData("abc"))
    assertEquals(
      ExprLanguage.render("eval", gappy),
      Right(ViewTree.from(gapId, ViewTree.gap("abc"))))
  }

  test("the derived print view prints text the rules read back") {
    // printViewWith derives minimal-parentheses printing from the rule
    // set; each input's flattened view is its own respelling.
    val expected = List(
      "1 + 2 - 3"   -> "1 + 2 - 3",
      "(1 + 2) * 3" -> "(1 + 2) * 3",
      "1.5 * 2"     -> "1.5 * 2",
      "ref:3 + 1"   -> "ref:3 + 1",
      "-4 + 5"      -> "-4 + 5",
      "1 - 2 - 3"   -> "1 - 2 - 3",
      "1 + (2 - 3)" -> "1 + (2 - 3)",
      "1 + abc"     -> "1 + abc",
      "1 +"         -> "1 + ?",
      "(1 2)"       -> "(1 2)",
      "abc"         -> "abc",
      "8 / 4 / 2"   -> "8 / 4 / 2")
    expected.foreach { case (t, printed) =>
      val doc =
        applyDocText(ExprLanguage.sampleDoc, registryForDoc, t).toOption.get
      assertEquals(
        ExprLanguage.render("print", doc).map(ViewTree.flatten),
        Right(printed), s"print: $t")
    }
  }

  private def gaps(t: DetachedNode[NodeData]): List[String] =
    (t.data match {
      case NodeData.GapData(text) => List(text)
      case _                    => Nil
    }) ++ t.children.flatMap(gaps)

  test("ExprLanguage reports documents without expression content") {
    val raw = beginDocument(s("root"))
    assert(ExprLanguage.render("eval", raw).isLeft)
  }
}
