package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.foundation.language.Language
import opelan.examples.languageForDoc
import opelan.ui.fp._
import opelan.ui.fp.Dsl._
import opelan.ui.view._

// The live right-hand column: every derived view the document's language
// provides, stacked and re-rendered on each document change — print on
// top, eval below for the expression language. Views render through the
// single view-tree interpreter: holes show as chips, and fragments tagged
// with SourceRef provenance select their producing node when clicked.
def typedDocSidebar(m: TypedDocModel): View[TypedDocInput] = {
  val sections = languageForDoc(m.doc, m.store).toList.flatMap(lang =>
    lang.views.map(v => section(lang, v, m)))
  // Hidden when a derived view fills the pane — the app is alone — and
  // for raw/untyped documents, which have no language and thus no views.
  val solo = m.view.isInstanceOf[DocView.Derived]
  els("div",
    style("width: 220px; flex-shrink: 0; border-left: 1px solid #ddd; " +
      "overflow: auto; background: #fafafa;" +
      hidden(sections.isEmpty || solo)))(
    sections)
}

private def section(
    lang: Language, viewName: String, m: TypedDocModel): View[TypedDocInput] =
  els("div", style("padding: 10px 12px; border-bottom: 1px solid #eee;"))(
    Vector(
      el("div",
        style("font-family: Arial, sans-serif; font-size: 11px; color: #999; " +
          "text-transform: uppercase; letter-spacing: 0.6px; margin-bottom: 6px;"))(
        text(viewName)),
      el("div",
        style("font-family: Consolas, monospace; font-size: 16px; " +
          "white-space: pre-wrap;"))(
        body(lang.renderWith(viewName, m.doc, m.holeRetry)))))

private[typeddoc] def body(
    r: Either[String, DetachedNode[NodeData]]): View[TypedDocInput] =
  r match {
    case Right(tree) =>
      viewTree(tree,
        id => TypedDocInput.SelectSource(id),
        (id, t) => TypedDocInput.CommitCellText(id, t))
    case Left(err)   => el("span", style("color: #b00;"))(text(s"error: $err"))
  }
