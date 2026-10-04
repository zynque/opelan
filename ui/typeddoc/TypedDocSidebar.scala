package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.foundation.language.{Frag, Language, Languages}
import opelan.ui.fp._
import opelan.ui.fp.Dsl._

// The live right-hand column: every derived view the document's language
// provides, stacked and re-rendered on each document change — print on
// top, eval below for the expression language. Holes render as inline
// chips so incomplete input reads as "waiting", not "error". Hidden for
// raw/untyped documents, which have no language and thus no views.
object TypedDocSidebar {

  def apply(m: TypedDocModel): View[TypedDocInput] = {
    val sections = Languages.forDoc(m.doc).toList.flatMap(lang =>
      lang.views.map(v => section(lang, v, m)))
    els("div",
      style("width: 220px; flex-shrink: 0; border-left: 1px solid #ddd; " +
        "overflow: auto; background: #fafafa;" +
        TypedDocView.hidden(sections.isEmpty)))(
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
        els("div",
          style("font-family: Consolas, monospace; font-size: 16px; " +
            "white-space: pre-wrap;"))(
          body(lang.render(viewName, m.doc)))))

  private def body(r: Either[String, List[Frag]]): Vector[View[TypedDocInput]] =
    r match {
      case Right(frags) => frags.toVector.map(fragView)
      case Left(err)    => Vector(el("span", style("color: #b00;"))(text(s"error: $err")))
    }

  private def fragView(f: Frag): View[TypedDocInput] = f match {
    case Frag.Text(t) => text(t)
    case Frag.Hole(t) =>
      el("span",
        style("background: #fdf3d7; color: #8a6d1a; border: 1px solid #e8d48a; " +
          "border-radius: 3px; padding: 0 4px;"))(
        text(if (t.isEmpty) "?" else t))
  }
}
