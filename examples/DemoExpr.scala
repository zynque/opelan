package opelan.examples

import opelan.examples.expr.{
  decodedExprDef, exprDoc, exprReading, exprTypeRef}
import opelan.foundation.document._
import opelan.foundation.document.typed.makeTyped
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc.{
  docImportsRef, docTitleRef, langDefFromDocument, metaConsOf}
import opelan.foundation.means.text.printPattern

// The sidebar's expression sample: (1 + 2) - (3 - 4)  ==  4, parsed
// by the seeded def.
lazy val exprSampleDoc: Document[NodeData] =
  makeTyped(
    exprTypeRef,
    NodeData.ExternalNodeRef(docTitleRef),
    NodeData.ExternalNodeRef(docImportsRef),
    NodeData.StringData("Expression sample"),
    languageFromDef(decodedExprDef)
      .parse("1 + 2 - (3 - 4)")
      .getOrElse(throw new IllegalStateException(
        "expression sample failed to parse")))

// The demo's seeded document — exprDoc minus the `a / b` rule's
// subtree and its binding-tier entry, every other node id intact, so
// the remaining declarations (`+`, `*`, `-`) keep exprDoc's anchors:
// one anchor space across both encodings of the language (a `/` node
// lands only once the user commits the rule, anchored at its new
// piece in the updated doc).
lazy val demoExprDoc: Document[NodeData] = {
  val doc = exprDoc
  val slashPattern = exprReading.rules.tiers.flatMap(_.patterns)
    .find(_.constructForm.exists(_._1 == "/"))
    .map(printPattern)
    .getOrElse(throw new IllegalStateException(
      "decodedExprDef has no / pattern"))
  val ruleId = definitionWithLiteral(doc, "/")
    .getOrElse(throw new IllegalStateException(
      "decodedExprDef encodes no / definition"))
  (for {
    defsId <- doc.getNode(ruleId).flatMap(_.parentId)
      .toRight("decodedExprDef / definition is detached")
    withoutRule <- updateNodeChildIds(defsId,
      doc.childrenOf(defsId).filterNot(_ == ruleId), doc)
    patternId <- bindingPatternId(withoutRule, defsId, slashPattern)
      .toRight("decodedExprDef encodes no / binding")
    tierId <- withoutRule.getNode(patternId).flatMap(_.parentId)
      .toRight("decodedExprDef / binding pattern is detached")
    staged <- updateNodeChildIds(tierId,
      withoutRule.childrenOf(tierId).filterNot(_ == patternId), withoutRule)
  } yield staged).fold(
    msg => throw new IllegalStateException(s"could not stage demoExprDoc: $msg"),
    identity)
}

// The staged def — the demo document's decode, so the budget's
// per-person formula sits as a gap until the audience adds `a / b`
// to the definition document.
lazy val demoExprDef: LanguageDef =
  langDefFromDocument(exprTypeRef.documentUrl, demoExprDoc)

// The `definition` node whose `pattern` declares a `literal` piece
// carrying `literal` — found by structure, so a stray `/` string
// anywhere else in a definition can't be mistaken for the rule.
private def definitionWithLiteral(
    doc: Document[NodeData], literal: String): Option[Int] =
  doc.nodes.indices.find { id =>
    metaConsOf(doc, id).contains("definition") &&
    doc.childrenOf(id).exists { pid =>
      metaConsOf(doc, pid).contains("pattern") &&
      doc.childrenOf(pid).exists(k =>
        metaConsOf(doc, k).contains("literal") &&
        doc.childrenOf(k).exists(cid =>
          doc.getNode(cid).exists(
            _.data == NodeData.StringData(literal))))
    }
  }

// The `binding`/`tier` string child carrying `patternText` — the
// binding entry matching the staged-out rule, removed along with its
// definition so the staged document stays self-consistent.
private def bindingPatternId(
    doc: Document[NodeData], defsId: Int,
    patternText: String): Option[Int] =
  doc.childrenOf(defsId)
    .find(id => metaConsOf(doc, id).contains("binding"))
    .flatMap(bid => doc.childrenOf(bid)
      .find(tid => metaConsOf(doc, tid).contains("tier") &&
        doc.childrenOf(tid).exists(cid =>
          doc.getNode(cid).map(_.data).contains(
            NodeData.StringData(patternText)))))
    .flatMap(tid => doc.childrenOf(tid)
      .find(cid => doc.getNode(cid).map(_.data).contains(
        NodeData.StringData(patternText))))
