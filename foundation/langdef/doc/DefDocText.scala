package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.document.typed._
import opelan.foundation.langdef._
import opelan.foundation.langdef.view.{meaningFieldText, whereFieldText}
import opelan.foundation.means.{lexicalTermOf, Rule}
import opelan.foundation.means.text.printPattern

// The text surface of a `lang:` document (foundation/language,
// DocText): the def's content subtree as an outline in which meta
// constructs print by name and each `definition` subtree flattens to
// its `means` line — the surface reads and writes rules, never the
// encoding's leaf strings:
//
//   dialect
//     description "the arithmetic-expression language"
//     name "Expressions"
//     syntax
//       "standard"
//       definitions
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
// the outline payloads. Under `definitions`, a string line is a
// definition — it
// reads through readRuleOrGap into the structured
// pattern/meaning/where encoding — while `binding`,
// `tier`, `example`, and `definition` meta nodes keep their structured
// encoding, so hand-written structure still lands.

// The content subtree as lines: meta constructs by name, a `definition`
// as
// its canonical `pattern means meaning [where]` text (phrase meanings
// project back through the defined language's own printer, as the def
// view's meaning field does), everything else as its outline payload —
// import-entry refs resolved to their external display so the id they
// hold doesn't matter.
def defDocLines(
    doc: Document[NodeData], url: String, rootId: Int,
    resolve: ExternalNodeReference => Option[LanguageDef] = _ => None
    ): List[String] = {
  val d = langDefFromDocument(url, doc, 0, resolve)
  val termIds = deriveTermIds(doc, rootId)
  def lines(id: Int, depth: Int): List[String] =
    metaConsOf(doc, id) match {
      case Some("definition") =>
        List("  " * depth + ruleLineText(
          d, ruleFromDoc(doc, url, 0, termIds, resolve, id)))
      case Some(name) =>
        ("  " * depth + name) :: doc.childrenOf(id).flatMap(lines(_, depth + 1))
      // A lexical ref in a payload position (the `surface` entry)
      // prints its registered name — the decode's `lexicalRefs`
      // fallback resolves it back.
      case None =>
        ("  " * depth + externalRefOf(doc, id)
          .map(r => lexicalTermOf(r)
            .map(n => NodeDataText.show(NodeData.StringData(n)))
            .getOrElse(NodeDataText.show(NodeData.ExternalNodeRef(r))))
          .getOrElse(doc.getNode(id).map(n => NodeDataText.show(n.data))
            .getOrElse("?"))) ::
          doc.childrenOf(id).flatMap(lines(_, depth + 1))
    }
  lines(rootId, 0)
}

// Reading the text back into a detached subtree is the language's
// compiled grammar's job — `document` (DocGrammar) is the surface
// production DocLanguage.syntax roots at.

private[langdef] def dataOf(text: String): NodeData =
  metaRef(text).map(NodeData.ExternalNodeRef(_))
    .getOrElse(NodeDataText.parse(text))

private[langdef] def isMeta(data: NodeData, name: String): Boolean = data match {
  case NodeData.ExternalNodeRef(r) => metaNameOf(r).contains(name)
  case _                           => false
}

// The rule's `means` line — the edit view's field composition as one
// text (`where` carries its keyword here).
private def ruleLineText(d: LanguageDef, r: Rule): String =
  printPattern(r.pattern) + " means " + meaningFieldText(d, r) +
    (if (r.where.isEmpty) "" else " where " + whereFieldText(r))
