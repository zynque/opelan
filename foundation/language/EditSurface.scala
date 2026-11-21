package opelan.foundation.language

import opelan.foundation.document._
import opelan.foundation.grammar._

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

// Marks derived from a compiled grammar — the rule-level subset of
// derived edit-surface marks: every leaf span paints what the parse
// made of it — a literal its number class, a ref, a string — a gap
// span the gap class, an unclaimed leaf unmatched. Construct spans
// paint nothing; their leaves carry the meaning. Dialects needing
// richer marks (lang:'s rule lines, markdown's blocks) name their own
// surface until authored marks land.
def grammarMarks(g: Grammar)(text: String): List[LineMark] = {
  val spans = List.newBuilder[(Int, Int, String)]
  def walk(t: SpannedTree[NodeData]): Unit = t.data.data match {
    case NodeData.GapData(_) =>
      spans += ((t.data.span.from, t.data.span.to, "cm-gap"))
    case _ if t.children.isEmpty =>
      spans += ((t.data.span.from, t.data.span.to, leafMark(t.data.data)))
    case _ => t.children.foreach(walk)
  }
  walk(spanned(g(text).tree))
  lineSpans(text, spans.result())
}

private def leafMark(d: NodeData): String = d match {
  case NodeData.IntData(_) | NodeData.FloatData(_) => "cm-number"
  case NodeData.StringData(_) => "cm-string"
  case NodeData.InternalNodeRef(_) | NodeData.ExternalNodeRef(_) |
       NodeData.SourceRef(_) => "cm-ref"
  case _ => "cm-unmatched"
}

// Absolute spans as per-line marks, clipped at line edges.
private def lineSpans(
    text: String, spans: List[(Int, Int, String)]): List[LineMark] = {
  var offset = 0
  text.split("\n", -1).toList.map { line =>
    val mark = LineMark("", spans.collect {
      case (f, t, c) if f < offset + line.length && t > offset =>
        ((f max offset) - offset, (t min (offset + line.length)) - offset,
          c)
    })
    offset += line.length + 1
    mark
  }
}
