package opelan.foundation.document

import Detached._

class StoreSuite extends munit.FunSuite {

  val docA: Document[NodeData] =
    buildDocument(n(s("doc A"), sl("x"), il(1)))
  val docB: Document[NodeData] =
    buildDocument(n(s("doc B"), sl("y")))

  test("put appends a version and advances the head") {
    val (s1, v0) = Store.empty.put("opelan:docs/a", docA)
    assertEquals(v0, 0)
    val (s2, v1) = s1.put("opelan:docs/a", docB)
    assertEquals(v1, 1)
    assertEquals(s2.head("opelan:docs/a"), Some((1, docB)))
  }

  test("pinned versions survive later puts") {
    val (s1, _) = Store.empty.put("opelan:docs/a", docA)
    val (s2, _) = s1.put("opelan:docs/a", docB)
    assertEquals(s2.get("opelan:docs/a", 0), Some(docA))
    assertEquals(s2.get("opelan:docs/a", 1), Some(docB))
    assertEquals(s2.get("opelan:docs/a", 2), None)
  }

  test("resolve dereferences url@version#node") {
    val (store, v) = Store.empty.put("opelan:docs/a", docA)
    val target = docA.childrenOf(docA.rootId).head
    val ref = ExternalNodeReference("opelan:docs/a", v, target)
    assertEquals(store.resolve(ref).map(_._2.data), Some(s("x")))
  }

  test("resolve fails for unknown url, version, or node") {
    val (s, v) = Store.empty.put("opelan:docs/a", docA)
    assertEquals(s.resolve(ExternalNodeReference("opelan:docs/missing", 0, 0)), None)
    assertEquals(s.resolve(ExternalNodeReference("opelan:docs/a", 9, 0)), None)
    assertEquals(s.resolve(ExternalNodeReference("opelan:docs/a", v, 999)), None)
  }

  test("withVersion rebuilds pinned versions and tracks the head") {
    val s = Store.empty.withVersion("u", 3, docA).withVersion("u", 1, docB)
    assertEquals(s.head("u"), Some((3, docA)))
    assertEquals(s.get("u", 1), Some(docB))
  }

  test("labelOf reads the root label and falls back to the url") {
    val (s, _) = Store.empty.put("u", docA)
    assertEquals(s.labelOf("u"), "doc A")
    assertEquals(s.labelOf("missing"), "missing")
  }

  test("urlForName slugifies and freshUrl disambiguates taken names") {
    assertEquals(Store.urlForName("My Doc!"), "opelan:docs/my-doc")
    val (s, _) = Store.empty.put("opelan:docs/my-doc", docA)
    assertEquals(s.freshUrl("My Doc!"), "opelan:docs/my-doc-2")
    assertEquals(s.freshUrl("other"), "opelan:docs/other")
  }
}
