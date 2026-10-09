package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.foundation.language.languageForDoc
import opelan.ui.fp._
import opelan.ui.fp.Dsl._
import opelan.ui.text.CodeMirror

// Pure view: a toolbar choosing the editing surface, a split body — the
// structural editor / text cell on the left, the language's live derived
// views in the sidebar on the right — and a status bar. The editor mount
// and text cell stay mounted in every mode; hiding with display:none
// keeps the child's DOM — and its focus/edit state — intact. The text
// cell remounts only when textEpoch changes (external doc edits).
def typedDocView(m: TypedDocModel): View[TypedDocInput] =
  el("div", style("height: 100%; display: flex; flex-direction: column;"))(
    toolbar(m),
    el("div", style("flex: 1; display: flex; min-height: 0;"))(
      editorPane(m),
      textPane(m),
      typedDocSidebar(m)),
    statusBar(m))

private def editorPane(m: TypedDocModel): View[TypedDocInput] =
  el("div", style("flex: 1; overflow: auto;" + hidden(m.view != DocView.Editor)))(
    mount)

private def textPane(m: TypedDocModel): View[TypedDocInput] =
  el("div", style("flex: 1; overflow: auto;" + hidden(m.view != DocView.Text)))(
    managed(s"cm-${m.textEpoch}") { (el, emit) =>
      CodeMirror.mount(el, m.text, t => emit(TypedDocInput.TextEdited(t)))
      ()
    })

private def toolbar(m: TypedDocModel): View[TypedDocInput] =
  els("div",
    style("padding: 6px 10px; border-bottom: 1px solid #ddd; background: #f9f9f9; " +
      "font-family: Arial, sans-serif; font-size: 12px;"))(
    Vector(
      viewButton("editor", DocView.Editor, m),
      viewButton("text", DocView.Text, m),
      el("span", style("color: #666; margin: 0 10px;"))(text(s"type: ${typeLabel(m)}")),
      el("button",
        style("padding: 3px 8px; float: right;"),
        events = on("click", TypedDocInput.LoadExprSample))(text("Expr sample"))))

private def viewButton(label: String, v: DocView, m: TypedDocModel): View[TypedDocInput] = {
  val active = m.view == v
  el("button",
    style("margin-right: 6px; padding: 3px 8px;" +
      (if (active) " font-weight: bold; background: #dde9f5;" else "")),
    events = on("click", TypedDocInput.SwitchView(v)))(
    text(label))
}

private def typeLabel(m: TypedDocModel): String =
  languageForDoc(m.doc).map(_.name).getOrElse {
    typeRefOf(m.doc)
      .map(r => s"unknown: ${r.documentUrl}#${r.nodeId}")
      .getOrElse("raw document")
  }

private def statusBar(m: TypedDocModel): View[TypedDocInput] =
  el("div",
    style("padding: 4px 10px; border-top: 1px solid #ddd; background: #f9f9f9; " +
      "font-family: Arial, sans-serif; font-size: 12px; color: #555;"))(
    text(s"${m.status} — root #${m.doc.rootId}"))

private[typeddoc] def hidden(condition: Boolean): String =
  if (condition) " display: none;" else ""
