package opelan.foundation.language

// A language's `edit` view free-text surface: `marks` classifies each
// line's text for the editor's decorations — what the line read as
// (the lang: surface paints constructs and gaps, the markdown surface
// its headings and emphasis) — and `preview` names a second view
// rendered beside the editor, live. A language whose `edit` view
// renders a view tree rather than an editor leaves this None.
case class EditSurface(
    marks: String => List[LineMark],
    preview: Option[String] = None)

// One line's presentation: a whole-line class ("" for none) and
// character-offset spans within the line's text — the same shape the
// host's decorated editor consumes.
case class LineMark(kind: String, spans: List[(Int, Int, String)] = Nil)
