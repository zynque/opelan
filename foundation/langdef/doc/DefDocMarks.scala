package opelan.foundation.langdef.doc

import opelan.foundation.document.{NodeData, NodeDataText}
import opelan.foundation.language.LineMark
import opelan.foundation.means.Piece
import opelan.foundation.means.text.readRule

// The def document's text surface (DefDocText) painted for editing:
// one mark per line — what the line read as — plus sub-line spans for
// the pieces a rule line carried. Kind names are CSS classes a host
// binds: `cm-meta` for constructs, `cm-string`/`cm-number`/`cm-ref`
// for payloads, `cm-rule` for a line under `definitions` that reads,
// `cm-gap`
// for `?`-text and rules that didn't, and `cm-unmatched` for a bare
// word the meta vocabulary doesn't declare — it lands as a string,
// so the painting signals it isn't a recognized pattern.

// Indentation tracks the outline's nesting the way defDocParse's
// `block` does: a line is a rule when its nearest shallower ancestor
// is the `definitions` construct.
def defDocLineMarks(text: String): List[LineMark] = {
  var stack: List[(Int, String)] = Nil
  text.split("\n", -1).toList.map { line =>
    val indent = line.takeWhile(c => c == ' ' || c == '\t').length
    val trimmed = line.trim
    while (stack.headOption.exists(_._1 >= indent)) stack = stack.tail
    val inRules = stack.headOption.exists(_._2 == "definitions")
    val mark =
      if (trimmed.isEmpty) LineMark("")
      else if (metaRef(trimmed).isDefined) LineMark("cm-meta")
      else if (inRules) ruleMark(trimmed, indent)
      else plainMark(trimmed)
    if (trimmed.nonEmpty)
      stack = (indent,
        metaRef(trimmed).flatMap(metaNameOf).getOrElse("")) :: stack
    mark
  }
}

// A rule line reads fully or not at all — the gap rule it would
// commit as is painted so the failing text stands out. `means` and
// `where` mark as keywords, bound placeholder names as placeholders.
private def ruleMark(line: String, indent: Int): LineMark =
  readRule(line) match {
    case Left(_) => LineMark("cm-gap")
    case Right(rule) =>
      val names = rule.pattern.pieces
        .collect { case Piece.Placeholder(name, _) => name }.distinct
      val placeholders = names.flatMap(name =>
        ("\\b" + java.util.regex.Pattern.quote(name) + "\\b").r
          .findAllMatchIn(line)
          .map(m => (m.start, m.end, "cm-placeholder")).toList)
      val keywords =
        keywordSpan(line, "means") ++ keywordSpan(line, "where") ++
          keywordSpan(line, "=>")
      val spans = (keywords ++ placeholders).sortBy(_._1)
        .foldLeft((List.empty[(Int, Int, String)], -1)) {
          case ((acc, end), s @ (f, t, _)) =>
            if (f < end) (acc, end) else (s :: acc, t)
        }._1.reverse.map { case (f, t, c) => (f + indent, t + indent, c) }
      LineMark("cm-rule", spans)
  }

private def keywordSpan(line: String, word: String)
    : List[(Int, Int, String)] = {
  val i = line.indexOf(s" $word ")
  if (i < 0) Nil else List((i + 1, i + 1 + word.length, "cm-keyword"))
}

private def plainMark(line: String): LineMark =
  NodeDataText.parse(line) match {
    case NodeData.StringData(_) if line.startsWith("\"") =>
      LineMark("cm-string")
    case NodeData.IntData(_) | NodeData.FloatData(_) =>
      LineMark("cm-number")
    case NodeData.InternalNodeRef(_) | NodeData.ExternalNodeRef(_) |
        NodeData.SourceRef(_) => LineMark("cm-ref")
    case NodeData.GapData(_) => LineMark("cm-gap")
    case NodeData.StringData(_) => LineMark("cm-unmatched")
  }
