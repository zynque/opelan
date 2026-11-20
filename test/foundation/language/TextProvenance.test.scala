package opelan.foundation.language

import munit.FunSuite
import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.grammar.{Span, SpannedNode, SpannedTree}

class TextProvenanceSuite extends FunSuite {

  private def owners(chains: List[Int]*) = Vector(chains*)

  // Post-order ids: a-leaf=0, a=1, *-leaf=2, *=3, b-leaf=4, b=5,
  // pattern=6 (the content root).
  private val doc = buildDocument(n(s("pattern"),
    n(s("placeholder"), sl("a")),
    n(s("literal"), sl("*")),
    n(s("placeholder"), sl("b"))))

  // `a * b`: each token's chain runs leaf, piece, pattern; the spaces
  // belong to the pattern — the innermost node covering them.
  private val start = TextProvenance(doc, "a * b",
    owners(List(0, 1, 6), List(6), List(2, 3, 6), List(6), List(4, 5, 6)))

  private def piece(data: NodeData, span: Span, kids: SpannedTree[NodeData]*)
      : SpannedTree[NodeData] =
    DetachedNode(SpannedNode(data, span), kids.toList)

  private def leaf(data: NodeData, span: Span) =
    DetachedNode(SpannedNode(data, span), Nil)

  test("inserted characters carry no chain") {
    val p = TextProvenance(doc, "a*b", owners(List(1), List(2), List(3)))
    assertEquals(p.retext("a**b").owners,
      owners(List(1), List(2), Nil, List(3)))
  }

  test("deleted characters take their chains with them") {
    val p = TextProvenance(doc, "a*b", owners(List(1), List(2), List(3)))
    assertEquals(p.retext("ab").owners, owners(List(1), List(3)))
  }

  test("a span hints the node holding most of its originals") {
    // typed "x " in front: `a`'s chain still passes through the
    // a-piece, so the moved piece keeps its id.
    val next = start.retext("x a * b")
    val spanned = piece(s("pattern"), Span(0, 7),
      piece(s("placeholder"), Span(0, 1), leaf(s("x"), Span(0, 1))),
      piece(s("placeholder"), Span(2, 3), leaf(s("a"), Span(2, 3))),
      piece(s("literal"), Span(4, 5), leaf(s("*"), Span(4, 5))),
      piece(s("placeholder"), Span(6, 7), leaf(s("b"), Span(6, 7))))
    val hints = next.hints(spanned)
    assertEquals(hints.get(List(1)), Some(1))
    assertEquals(hints.get(List(2)), Some(3))
    assertEquals(hints.get(List(3)), Some(5))
    assertEquals(hints.get(List(0)), None)
    assertEquals(hints.get(Nil), Some(6))
  }

  test("a span with no originals hints nothing") {
    // `a * b` -> `a ^ b`: the caret's chain passes through none of the
    // star's owners, so the literal is a new node.
    val next = start.retext("a ^ b")
    val spanned = piece(s("pattern"), Span(0, 5),
      piece(s("placeholder"), Span(0, 1), leaf(s("a"), Span(0, 1))),
      piece(s("literal"), Span(2, 3), leaf(s("^"), Span(2, 3))),
      piece(s("placeholder"), Span(4, 5), leaf(s("b"), Span(4, 5))))
    val hints = next.hints(spanned)
    assertEquals(hints.get(List(1)), None)
    assertEquals(hints.get(List(0)), Some(1))
    assertEquals(hints.get(List(2)), Some(5))
  }

  test("a piece keeps its node while an original survives") {
    // `a * b` -> `a ** b`: the second `*` is the original — the literal
    // keeps its id, its `**` leaf (different data) doesn't.
    val next = start.retext("a ** b")
    val spanned = piece(s("pattern"), Span(0, 6),
      piece(s("placeholder"), Span(0, 1), leaf(s("a"), Span(0, 1))),
      piece(s("literal"), Span(2, 4), leaf(s("**"), Span(2, 4))),
      piece(s("placeholder"), Span(5, 6), leaf(s("b"), Span(5, 6))))
    val hints = next.hints(spanned)
    assertEquals(hints.get(List(1)), Some(3))
    assertEquals(hints.get(List(1, 0)), None)
    assertEquals(hints.get(List(0)), Some(1))
    assertEquals(hints.get(List(2)), Some(5))
  }

  test("adopt claims unowned characters with the covering chain") {
    // `b` and `c` have no chain — the deepest node covering them (the
    // `b` leaf, id 4) claims them; its ancestor 6 already owns
    // characters, so the chain stops short of it.
    val provenance = TextProvenance(doc, "a * bc",
      owners(List(0, 1, 6), List(6), List(2, 3, 6), List(6), Nil, Nil))
    val spanned = piece(s("pattern"), Span(0, 6),
      piece(s("placeholder"), Span(0, 1), leaf(s("a"), Span(0, 1))),
      piece(s("literal"), Span(2, 3), leaf(s("*"), Span(2, 3))),
      piece(s("placeholder"), Span(4, 6), leaf(s("bc"), Span(4, 6))))
    val adopted = provenance.adopt(doc, doc.rootId, spanned)
    assertEquals(adopted.owners,
      owners(List(0, 1, 6), List(6), List(2, 3, 6), List(6),
        List(4, 5), List(4, 5)))
    assert(adopted.doc eq doc)
  }
}
