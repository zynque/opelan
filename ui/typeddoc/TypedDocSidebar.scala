package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.foundation.language.Languages
import opelan.ui.fp._
import opelan.ui.fp.Dsl._

// The live right-hand column: every derived view the document's language
// provides, stacked and re-rendered on each document change — print on
// top, eval below for the expression language. Hidden for raw/untyped
// documents, which have no language and thus no derived views.
object TypedDocSidebar {

  def apply(m: TypedDocModel): View[TypedDocInput] = {
    val sections = Languages.forDoc(m.doc).toList.flatMap(lang =>
      lang.views.map(v => section(m, v)))
    els("div",
      style("width: 220px; flex-shrink: 0; border-left: 1px solid #ddd; " +
        "overflow: auto; background: #fafafa;" +
        TypedDocView.hidden(sections.isEmpty)))(
      sections)
  }

  private def section(m: TypedDocModel, viewName: String): View[TypedDocInput] =
    els("div", style("padding: 10px 12px; border-bottom: 1px solid #eee;"))(
      Vector(
        el("div",
          style("font-family: Arial, sans-serif; font-size: 11px; color: #999; " +
            "text-transform: uppercase; letter-spacing: 0.6px; margin-bottom: 6px;"))(
          text(viewName)),
        el("pre",
          style("font-family: Consolas, monospace; font-size: 16px; margin: 0; " +
            "white-space: pre-wrap;"))(
          text(rendered(m, viewName)))))

  private def rendered(m: TypedDocModel, viewName: String): String =
    Languages.forDoc(m.doc) match {
      case Some(lang) =>
        lang.render(viewName, m.doc).fold(err => s"error: $err", identity)
      case None =>
        if (Typed.isTyped(m.doc)) "Unknown document type"
        else "No derived views"
    }
}
