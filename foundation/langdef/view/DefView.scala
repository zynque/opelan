package opelan.foundation.langdef.view

import opelan.foundation.document._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc._
import opelan.foundation.view.ViewTree
import opelan.foundation.view.ViewTree._

// The "def" view of a language-definition document: decode the doc and
// lay the definition out readably — title, description, the means
// rules as editable lines, view bindings, and the input grammar's
// target. The
// document is still the source of truth; this is the skim-friendly
// rendering of it.
// A rules section renders each line as a projected input bound to its
// text leaf, so editing a rule commits through the doc language's
// input parser and the next decode re-reads it; and `ctx.interpret`
// runs each worked example against the live definition, a failing one
// showing as a gap. `ctx.docUrl` supplies the document's stored
// identity — a def doc's URL is the language it defines, and the
// decode needs it so interpretation binds the right implementations.
def defView(doc: Document[NodeData], contentRootId: Int,
    ctx: ViewContext = ViewContext()): DetachedNode[NodeData] = {
  val d = langDefFromDocument(ctx.docUrl.getOrElse(""), doc)
  block(List(
    Some(heading(txt(d.title))),
    Some(txt(d.description)),
    d.syntax.map(s =>
      section("syntax",
        s.rules.toList.flatMap(rs =>
          docRuleLines(doc, contentRootId, d, exampleChecker(d, ctx))
            .getOrElse(ruleLines(rs))))),
    Some(section("views", d.views.map(v =>
      txt(s"${v.name} → ${v.builtin}${settingsText(v.settings)}")))),
    d.inputParser.map(cp =>
      section("inputs", List(txt(inputParserText(cp)))))).flatten*)
}

// The meta construct a node applies, by identity — the reader's `cons`.
private[langdef] def docCons(
    doc: Document[NodeData], id: Int): Option[String] =
  metaConsOf(doc, id)

private[langdef] def leafText(doc: Document[NodeData], id: Int): String =
  doc.getNode(id).map(_.data).collect { case NodeData.StringData(s) => s }
    .getOrElse("")

// One item per line: each entry is its own block.
private def section(
    name: String, items: List[DetachedNode[NodeData]])
    : DetachedNode[NodeData] =
  block(heading(txt(name)) +: items.map(block(_))*)

private def settingsText(settings: Map[String, String]): String =
  if (settings.isEmpty) ""
  else settings.toList.sorted.map((k, v) => s"$k: $v").mkString(" (", ", ", ")")

private def inputParserText(cp: InputParserDef): String = cp match {
  case InputParserDef.Builtin(name) => s"builtin $name"
  case InputParserDef.Language(ref, fb) =>
    s"language ${ref.documentUrl}#${ref.nodeId} (fallback: $fb)"
}
