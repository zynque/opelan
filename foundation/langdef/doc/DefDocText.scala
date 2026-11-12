package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._
import opelan.foundation.langdef.view.{meaningFieldText, whereFieldText}
import opelan.foundation.means.Rule
import opelan.foundation.means.text.{printPattern, readRuleOrGap}

// The text surface of a `lang:` document (foundation/language,
// DocText): the def's content subtree as an outline in which meta
// constructs print by name and each `rule` subtree flattens to its
// `means` line — the surface reads and writes rules, never the
// encoding's leaf strings:
//
//   definition
//     description "the arithmetic-expression language"
//     name "Expressions"
//     syntax
//       "expr"
//       rules
//         a + b means add(a, b)
//         n means n where n: number
//         binding
//           tier
//             "a * b"
//             "a / b"
//         example
//           "1 + 2 * 3"
//           "1 + (2 * 3)"
//     views
//       view "print" "expr-print"
//
// Writing back: a bare word naming a meta construct is that construct
// (quoting keeps a word a string); `ref:`/`src:`/`?`/number lines keep
// the outline payloads. Under `rules`, a string line is a rule — it
// reads through readRuleOrGap into the structured
// pattern/meaning/where encoding — while `binding`,
// `tier`, `example`, and `rule` meta nodes keep their structured
// encoding, so hand-written structure still lands.

// The content subtree as lines: meta constructs by name, a `rule` as
// its canonical `pattern means meaning [where]` text (phrase meanings
// project back through the defined language's own printer, as the def
// view's meaning field does), everything else as its outline payload —
// import-entry refs resolved to their external spelling so the id they
// hold doesn't matter.
def defDocLines(
    doc: Document[NodeData], url: String, rootId: Int,
    resolve: ExternalNodeReference => Option[LanguageDef] = _ => None
    ): List[String] = {
  val d = langDefFromDocument(url, doc, 0, resolve)
  val termIds = deriveTermIds(doc, rootId)
  def lines(id: Int, depth: Int): List[String] =
    metaConsOf(doc, id) match {
      case Some("rule") =>
        List("  " * depth + ruleLineText(
          d, ruleFromDoc(doc, url, 0, termIds, resolve, id)))
      case Some(name) =>
        ("  " * depth + name) :: doc.childrenOf(id).flatMap(lines(_, depth + 1))
      case None =>
        ("  " * depth + externalRefOf(doc, id)
          .map(r => NodeDataText.show(NodeData.ExternalNodeRef(r)))
          .getOrElse(doc.getNode(id).map(n => NodeDataText.show(n.data))
            .getOrElse("?"))) ::
          doc.childrenOf(id).flatMap(lines(_, depth + 1))
    }
  lines(rootId, 0)
}

// Read the text back into a detached content subtree — what
// DocLanguage.parse feeds commitContent (foundation/language/DocText).
// Total: bare meta words become construct refs, rule lines under
// `rules` become `rule` nodes, and anything else stays the outline
// payload it spelled. Phrase meanings a language-aware commit would
// desugar stay quoted gaps here — interpretation retries them
// (rephraseRuleSet).
def defDocParse(text: String): DetachedNode[NodeData] = {
  val entries = text.split("\n", -1).toList
    .filter(_.trim.nonEmpty)
    .map { line =>
      val indent = line.takeWhile(c => c == ' ' || c == '\t').length
      (indent, dataOf(line.trim))
    }
  entries match {
    case Nil          => DetachedNode.leaf(externalMeta("definition"))
    case head :: rest => structured(DetachedNode(head._2, block(rest)))
  }
}

private def dataOf(text: String): NodeData =
  metaRef(text).map(NodeData.ExternalNodeRef(_))
    .getOrElse(NodeDataText.parse(text))

// Each entry absorbs the following entries indented deeper than itself
// — Outline.block's shape, on def-doc payloads.
private def block(
    entries: List[(Int, NodeData)]): List[DetachedNode[NodeData]] =
  entries match {
    case Nil => Nil
    case (indent, data) :: tail =>
      val (children, rest) = tail.span(_._1 > indent)
      DetachedNode(data, block(children)) :: block(rest)
  }

// A string child of a `rules` node is a rule line — structure it;
// everything else keeps the form it was written in.
private def structured(t: DetachedNode[NodeData]): DetachedNode[NodeData] = {
  val children = t.children.map(structured)
  if (isMeta(t.data, "rules"))
    DetachedNode(t.data, children.map(c => c.data match {
      case NodeData.StringData(line) =>
        ruleNode(readRuleOrGap(line), "", Map.empty, externalMeta)
      case _ => c
    }))
  else DetachedNode(t.data, children)
}

private def isMeta(data: NodeData, name: String): Boolean = data match {
  case NodeData.ExternalNodeRef(r) => metaNameOf(r).contains(name)
  case _                           => false
}

// The rule's `means` line — the edit view's field composition as one
// text (`where` carries its keyword here).
private def ruleLineText(d: LanguageDef, r: Rule): String =
  printPattern(r.pattern) + " means " + meaningFieldText(d, r) +
    (if (r.where.isEmpty) "" else " where " + whereFieldText(r))
