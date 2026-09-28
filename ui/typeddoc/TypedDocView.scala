package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.foundation.language.Languages
import opelan.ui.fp._
import opelan.ui.fp.Dsl._

// Pure view: a view-switching toolbar, the editor child (kept mounted in
// every mode so it survives view switches), the derived-view panel, and a
// status bar. The mount sits in an always-present wrapper; hiding it with
// display:none keeps the child's DOM — and its focus/edit state — intact.
object TypedDocView {

  def view(m: TypedDocModel): View[TypedDocInput] =
    el("div", style("height: 100%; display: flex; flex-direction: column;"))(
      toolbar(m),
      editorPane(m),
      derivedPane(m),
      statusBar(m))

  private def editorPane(m: TypedDocModel): View[TypedDocInput] =
    el("div", style("flex: 1; overflow: auto;" + hidden(m.view != DocView.Editor)))(
      mount)

  private def derivedPane(m: TypedDocModel): View[TypedDocInput] = {
    val body = m.view match {
      case DocView.Editor => Vector.empty[View[TypedDocInput]]
      case DocView.Derived(name) =>
        Vector(el("pre",
          style("font-family: Consolas, monospace; font-size: 16px; margin: 0;"))(
          text(derivedText(m, name))))
    }
    els("div",
      style("flex: 1; overflow: auto; padding: 16px;" + hidden(m.view == DocView.Editor)))(
      body)
  }

  private def derivedText(m: TypedDocModel, viewName: String): String =
    Languages.forDoc(m.doc) match {
      case Some(lang) =>
        lang.render(viewName, m.doc).fold(err => s"error: $err", identity)
      case None =>
        if (Typed.isTyped(m.doc)) "Unknown document type — no views available"
        else "Raw document — no derived views"
    }

  private def toolbar(m: TypedDocModel): View[TypedDocInput] = {
    val viewButtons = viewButton("editor", DocView.Editor, m) +:
      Languages.forDoc(m.doc).toList.flatMap(lang =>
        lang.views.map(v => viewButton(v, DocView.Derived(v), m)))
    els("div",
      style("padding: 6px 10px; border-bottom: 1px solid #ddd; background: #f9f9f9; " +
        "font-family: Arial, sans-serif; font-size: 12px;"))(
      viewButtons ++ Vector(
        el("span", style("color: #666; margin: 0 10px;"))(text(s"type: ${typeLabel(m)}")),
        el("button",
          style("padding: 3px 8px; float: right;"),
          events = on("click", TypedDocInput.LoadExprSample))(text("Expr sample"))))
  }

  private def viewButton(label: String, v: DocView, m: TypedDocModel): View[TypedDocInput] = {
    val active = m.view == v
    el("button",
      style("margin-right: 6px; padding: 3px 8px;" +
        (if (active) " font-weight: bold; background: #dde9f5;" else "")),
      events = on("click", TypedDocInput.SwitchView(v)))(
      text(label))
  }

  private def typeLabel(m: TypedDocModel): String =
    Languages.forDoc(m.doc).map(_.name).getOrElse {
      Typed.typeRefOf(m.doc)
        .map(r => s"unknown: ${r.documentUrl}#${r.nodeId}")
        .getOrElse("raw document")
    }

  private def statusBar(m: TypedDocModel): View[TypedDocInput] =
    el("div",
      style("padding: 4px 10px; border-top: 1px solid #ddd; background: #f9f9f9; " +
        "font-family: Arial, sans-serif; font-size: 12px; color: #555;"))(
      text(s"${m.status} — root #${m.doc.rootId}"))

  private def hidden(condition: Boolean): String =
    if (condition) " display: none;" else ""
}
