package opelan.foundation.document.typed

import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.Detached._

class TypedSuite extends munit.FunSuite {

  val ref = ExternalNodeReference("test:type", 0, 7)
  val plus = ExternalNodeReference("test:lang", 0, 3)
  val titleRef = ExternalNodeReference("test:core", 0, 0)
  val importsRef = ExternalNodeReference("test:core", 0, 1)
  val doc = makeTyped(ref, er(titleRef), er(importsRef), s("doc"),
    n(er(plus), il(1), n(er(plus), il(2), il(3))))

  test("make lays out imports, title, then content under a typed root") {
    assertEquals(doc.getNode(doc.rootId).map(_.data), Some(NodeData.ExternalNodeRef(ref)))
    val children = doc.childrenOf(doc.rootId)
    assertEquals(children.length, 3)
    assertEquals(importsId(doc), Some(children(0)))
    // The imports node's data is the `imports` declaration as a direct
    // external ref (not imported into itself); its children are the
    // ext-ref entries.
    assertEquals(doc.getNode(children(0)).map(_.data),
      Some(NodeData.ExternalNodeRef(importsRef)))
    assertEquals(titleId(doc), Some(children(1)))
    // The title node carries the `title` construct ref (imported); the
    // raw text is its child.
    assertEquals(externalRefOf(doc, children(1)), Some(titleRef))
    assertEquals(titleTextOf(doc).flatMap(doc.getNode).map(_.data), Some(s("doc")))
    assertEquals(contentId(doc), Some(children(2)))
  }

  test("make imports inline external refs once and uses them by internal ref") {
    val entries = importEntries(doc)
    assertEquals(entries.map(_._2), List(titleRef, plus))
    val entry = entries.find(_._2 == plus).get._1
    val cid = contentId(doc).get
    assertEquals(doc.getNode(cid).map(_.data), Some(NodeData.InternalNodeRef(entry)))
    assertEquals(externalRefOf(doc, cid), Some(plus))
    assert(isImportEntry(doc, entry))
    assert(!isImportEntry(doc, cid))
  }

  test("extractExternalized inlines imports again") {
    assertEquals(extractExternalized(doc, contentId(doc).get),
      Some(n(er(plus), il(1), n(er(plus), il(2), il(3)))))
  }

  test("typeRefOf reads the type reference") {
    assertEquals(typeRefOf(doc), Some(ref))
  }

  test("a raw document has no type and no imports") {
    val raw = beginDocument(s("root"))
    assertEquals(typeRefOf(raw), None)
    assert(!isTyped(raw))
    assertEquals(importsId(raw), None)
    assertEquals(internalizeRefs(raw, raw.rootId), Right(raw))
  }

  test("a root without an external-ref data is not typed") {
    val raw = buildDocument(n(s("a"), sl("b"), sl("c")))
    assertEquals(typeRefOf(raw), None)
    assert(!isTyped(raw))
  }

  test("a simple typed document has data children and no standard layout") {
    val simple = buildDocument(n(er(ref), il(1), il(2)))
    assert(isTyped(simple))
    assertEquals(typeRefOf(simple), Some(ref))
    assert(!hasStandardLayout(simple))
    assertEquals(importsId(simple), None)
    assertEquals(contentId(simple), None)
  }

  test("refName resolves an import through the store, at the head") {
    val lang0 = buildDocument(n(s("lang"), sl("x"), sl("y"),
      n(er(ExternalNodeReference("meta:kind", 0, 9)), sl("plus"))))
    val kindId = lang0.rootId - 1
    val lang1 = updateNodeData(lang0.childrenOf(kindId).head, s("add"), lang0).toOption.get
    val target = ExternalNodeReference("test:lang", 0, kindId)
    val d = makeTyped(ref, er(titleRef), er(importsRef), s("doc"),
      n(er(target), il(1)))
    val cid = contentId(d).get
    val store0 = Store.empty.put("test:lang", lang0)._1
    assertEquals(refName(store0, d, cid), Some("plus"))
    // A rename in a later version shows through the pinned import.
    assertEquals(refName(store0.put("test:lang", lang1)._1, d, cid), Some("add"))
  }
}
