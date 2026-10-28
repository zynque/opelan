package opelan.ui.typeddoc

import org.scalajs.dom
import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.typed._
import opelan.foundation.langdef.autoUpdateEnabled
import opelan.examples.languageForDoc
import opelan.ui.fp._
import opelan.ui.fp.Dsl._
import opelan.ui.text.CodeMirror

// Pure view: a toolbar choosing the editing surface, a split body — the
// structural editor / text surface on the left, the language's live derived
// views in the sidebar on the right — and a status bar. The editor mount
// and text surface stay mounted in every mode; hiding with display:none
// keeps the child's DOM — and its focus/edit state — intact. The text
// surface remounts only when textEpoch changes (external doc edits).
def typedDocView(m: TypedDocModel): View[TypedDocInput] =
  el("div", style("height: 100%; display: flex; flex-direction: column;"))(
    toolbar(m),
    el("div", style("flex: 1; display: flex; min-height: 0;"))(
      editorPane(m),
      textPane(m),
      derivedPane(m),
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

// A derived view fills the pane — the app surface alone, no editor.
// The bar carries the two navigations out: Raw shows this document's
// own representation (the structural editor); Definition (F12) follows
// the type ref to the language document. The pane is focusable so F12
// works anywhere; a click that didn't land inside an editor focuses it.
private def derivedPane(m: TypedDocModel): View[TypedDocInput] = {
  // The view actually shown: if the doc's language doesn't offer the
  // requested view — e.g. its definition was edited — fall back to the
  // language's default perspective rather than rendering an error.
  val lang = languageForDoc(m.doc, m.store)
  val active: Option[String] = m.view match {
    case DocView.Derived(n) => lang match {
      case Some(l) if l.views.contains(n) => Some(n)
      case Some(l)                        => l.defaultView.orElse(Some(n))
      case None                           => Some(n)
    }
    case _ => None
  }
  val keydown: dom.Event => Option[TypedDocInput] = e =>
    if (e.asInstanceOf[dom.KeyboardEvent].key == "F12") {
      e.preventDefault()
      Some(TypedDocInput.GoToDefinition)
    } else None
  val click: dom.Event => Option[TypedDocInput] = e => {
    val t = e.target.asInstanceOf[dom.Element]
    if (!t.isInstanceOf[dom.HTMLInputElement] && t.querySelector("input") == null)
      e.currentTarget.asInstanceOf[dom.HTMLElement].focus()
    None
  }
  els("div",
    attrs = Map(
      "tabindex" -> "0",
      "style" -> ("flex: 1; display: flex; flex-direction: column; " +
        "min-height: 0; outline: none;" + hidden(active.isEmpty))),
    events = Map("keydown" -> keydown, "click" -> click))(
    active.toList.flatMap(name => Vector(derivedBar(name), derivedBody(name, m))))
}

private def derivedBar(name: String): View[TypedDocInput] =
  els("div",
    style("padding: 6px 10px; border-bottom: 1px solid #eee; background: #fafafa; " +
      "font-family: Arial, sans-serif; font-size: 12px;"))(
    Vector(
      el("span", style("color: #666; margin-right: 12px;"))(text(s"view: $name")),
      barButton("Definition (F12)", TypedDocInput.GoToDefinition),
      barButton("Raw", TypedDocInput.SwitchView(DocView.Editor))))

private def barButton(label: String, in: TypedDocInput): View[TypedDocInput] =
  el("button",
    style("margin-right: 6px; padding: 3px 8px;"),
    events = on("click", in))(text(label))

private def derivedBody(name: String, m: TypedDocModel): View[TypedDocInput] = {
  val rendered = languageForDoc(m.doc, m.store)
    .map(_.renderWith(name, m.doc, m.holeRetry, m.docUrl))
    .getOrElse(Left("no language for this document"))
  el("div", style("flex: 1; overflow: auto; padding: 12px;"))(
    body(rendered))
}

private def toolbar(m: TypedDocModel): View[TypedDocInput] =
  els("div",
    style("padding: 6px 10px; border-bottom: 1px solid #ddd; background: #f9f9f9; " +
      "font-family: Arial, sans-serif; font-size: 12px;"))(
    Vector(
      viewButton("editor", DocView.Editor, m),
      viewButton("text", DocView.Text, m)) ++
      languageForDoc(m.doc, m.store).toList.flatMap(l =>
        l.views.map(v => viewButton(v, DocView.Derived(v), m))) ++
      Vector(
        el("span", style("color: #666; margin: 0 10px;"))(text(s"type: ${typeLabel(m)}"))) ++
      pendingControls(m) ++
      Vector(
        el("span", style("float: right;"))(
          el("button",
            style("padding: 3px 8px; margin-left: 6px;"),
            events = on("click", TypedDocInput.LoadExprSample))(text("Expr sample")),
          el("button",
            style("padding: 3px 8px; margin-left: 6px;"),
            events = on("click", TypedDocInput.LoadSheetSample))(text("Sheet sample")))))

// Pending pin updates + the auto-update opt-in: the accept button
// appears only when a ref pins behind a head; the toggle writes the
// flag into the document itself. A ref whose node is gone at the head
// can't be bumped — it surfaces as a broken count, not an update.
private def pendingControls(m: TypedDocModel): Vector[View[TypedDocInput]] = {
  val n = pendingUpdates(m.doc, m.store).length
  val broken = brokenRefs(m.doc, m.store)
  Vector(
    el("button",
      style("padding: 3px 8px; margin-left: 6px;" +
        (if (n == 0) " display: none;" else " font-weight: bold;")),
      events = on("click", TypedDocInput.AcceptUpdates))(
      text(s"Update ($n)")),
    el("button",
      style("padding: 3px 8px; margin-left: 6px;"),
      events = on("click", TypedDocInput.ToggleAutoUpdate))(
      text(s"auto-update: ${if (autoUpdateEnabled(m.doc)) "on" else "off"}"))) ++
    brokenChip(broken)
}

private def brokenChip(
    broken: List[(Int, ExternalNodeReference, Int)])
    : Vector[View[TypedDocInput]] =
  if (broken.isEmpty) Vector.empty
  else Vector(els[TypedDocInput]("span",
    attrs = Map(
      "style" -> "color: #a33; margin-left: 6px;",
      "title" -> broken.map { case (_, r, v) =>
        s"${r.documentUrl}@${r.documentVersionId}#${r.nodeId} " +
          s"pins a node gone at v$v"
      }.mkString("\n")))(
    Vector(text(s"broken: ${broken.length}"))))


private def viewButton(label: String, v: DocView, m: TypedDocModel): View[TypedDocInput] = {
  val active = m.view == v
  el("button",
    style("margin-right: 6px; padding: 3px 8px;" +
      (if (active) " font-weight: bold; background: #dde9f5;" else "")),
    events = on("click", TypedDocInput.SwitchView(v)))(
    text(label))
}

private def typeLabel(m: TypedDocModel): String =
  languageForDoc(m.doc, m.store).map(_.name).getOrElse {
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
