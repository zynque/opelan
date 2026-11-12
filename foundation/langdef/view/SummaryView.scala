package opelan.foundation.langdef.view

import opelan.foundation.document._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc._
import opelan.foundation.view.ViewTree._

// The "summary" view of a language-definition document: the language
// at a glance — its description, worked examples, and view bindings.
// Read-only where the "edit" view projects inputs; it carries the same
// role styling (keywords, literals) for a skim of what the language
// is. Examples still check live, a failure trailing as a gap.
def defSummary(doc: Document[NodeData], contentRootId: Int,
    ctx: ViewContext = ViewContext()): DetachedNode[NodeData] = {
  val d = langDefFromDocument(ctx.docUrl.getOrElse(""), doc)
  val check = exampleChecker(d, ctx)
  block(List(
    Some(heading(txt(d.title))),
    Some(txt(d.description)),
    d.syntax.flatMap(_.rules).map(_.examples).filter(_.nonEmpty).map(
      examples => section("examples", examples.map(e =>
        flow((List(
          styled("marker", txt("e.g. ")),
          styled("literal", txt(e.input)),
          styled("keyword", txt(" means ")),
          styled("literal", txt(e.expected))) ++
          check(e.input, e.expected).toList
            .flatMap(t => List(txt(" "), gap(t))))*)))),
    Some(section("views", d.views.map(v =>
      flow(
        styled("tag", txt(v.name)),
        styled("marker", txt(" → ")),
        styled("literal", txt(v.builtin + settingsText(v.settings)))))))
  ).flatten*)
}
