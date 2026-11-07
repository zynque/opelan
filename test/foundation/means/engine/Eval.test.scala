package opelan.foundation.means.engine

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.means._
import opelan.foundation.means.text.readRuleOrGap

// The derived evaluator (Eval.scala): foundational definitions by
// reference, authored semantics rules first, refs through the
// document, cycles as gaps.
class EvalSuite extends munit.FunSuite {

  private val terms = foundationRefs ++
    List("square", "quad", "loop", "ring", "mystery")
      .zipWithIndex.map { case (n, i) =>
        n -> ExternalNodeReference("opelan:langs/t", 0, i + 1) }.toMap

  private def app(tag: String, args: DetachedNode[NodeData]*) =
    DetachedNode(NodeData.ExternalNodeRef(terms(tag)), args.toList)

  private def set(rules: String*): RuleSet =
    RuleSet(rules.map(readRuleOrGap).toList)

  private def eval(rules: RuleSet, doc: Document[NodeData], id: Int) =
    evalNode(semanticsOf(rules), terms, doc, id, Set(id))

  private val numOps = set("a + b means add(a, b)", "a * b means mul(a, b)",
    "a - b means sub(a, b)", "a / b means div(a, b)")

  private def doc(nodes: Node[NodeData]*): Document[NodeData] =
    Document(nodes.indexWhere(_.parentId.isEmpty), nodes.toVector)

  private def node(data: NodeData, children: List[Int], parent: Option[Int]) =
    Node(0, data, children, parent)

  test("numeric leaves evaluate to themselves; arithmetic is int-preserving") {
    val d = buildDocument(app("add", il(1), il(2)))
    assertEquals(eval(numOps, d, d.rootId), Right(NodeData.IntData(3)))
    val d2 = buildDocument(app("mul",
      il(4), DetachedNode.leaf(NodeData.FloatData(1.2))))
    assertEquals(eval(numOps, d2, d2.rootId),
      Right(NodeData.FloatData(4.8)))
  }

  test("division always floats and fails at zero") {
    val d = buildDocument(app("div", il(8), il(2)))
    assertEquals(eval(numOps, d, d.rootId), Right(NodeData.FloatData(4.0)))
    val z = buildDocument(app("div", il(1), il(0)))
    assertEquals(eval(numOps, z, z.rootId),
      Left(("division by zero", z.rootId)))
  }

  test("a gap blocks evaluation, carrying its text") {
    val d = buildDocument(app("add", il(1),
      DetachedNode.leaf(NodeData.GapData("abc"))))
    eval(numOps, d, d.rootId) match {
      case Left((msg, _)) => assertEquals(msg, "abc")
      case _              => fail("expected a gap")
    }
  }

  test("an unknown construct is a gap, not an error") {
    val d = buildDocument(app("mystery", il(1)))
    assert(eval(numOps, d, d.rootId).isLeft)
  }

  test("refs evaluate through the document; a ref cycle is a gap") {
    // 0: 1   1: add(0, 2)   2: 2  — root is the add = 3
    val d = doc(
      node(NodeData.IntData(1), Nil, Some(1)),
      node(NodeData.ExternalNodeRef(terms("add")), List(0, 2), None),
      node(NodeData.IntData(2), Nil, Some(1)))
    assertEquals(eval(numOps, d, d.rootId), Right(NodeData.IntData(3)))
    // 0: ref:0 — a self reference is a cycle
    val cyc = doc(node(NodeData.InternalNodeRef(0), Nil, None))
    assertEquals(eval(numOps, cyc, 0), Left(("cycle", 0)))
  }

  test("an authored semantics rule evaluates its construct") {
    val rules = set(
      "a * b means mul(a, b)",
      "square(a) means mul(a, a)")
    val d = buildDocument(app("square", il(3)))
    assertEquals(eval(rules, d, d.rootId), Right(NodeData.IntData(9)))
  }

  test("semantics rules layer over each other") {
    val rules = set(
      "a * b means mul(a, b)",
      "square(a) means mul(a, a)",
      "quad(a) means square(square(a))")
    val d = buildDocument(app("quad", il(3)))
    assertEquals(eval(rules, d, d.rootId), Right(NodeData.IntData(81)))
  }

  test("a semantics cycle renders as a gap") {
    val rules = set("loop(a) means ring(a)", "ring(a) means loop(a)")
    assert(semanticsOf(rules).cyclic.nonEmpty)
    val d = buildDocument(app("ring", il(1)))
    assert(eval(rules, d, d.rootId).isLeft)
  }

  test("a semantics rule of the wrong arity is a gap") {
    val rules = set("square(a) means mul(a, a)")
    val d = buildDocument(app("square", il(3), il(4)))
    assert(eval(rules, d, d.rootId).isLeft)
  }

  test("a semantics rule shadows a foundational definition") {
    val rules = set("add(a, b) means mul(a, b)")
    val d = buildDocument(app("add", il(2), il(3)))
    assertEquals(eval(rules, d, d.rootId), Right(NodeData.IntData(6)))
  }
}
