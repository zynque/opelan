package opelan.examples.markdown

import opelan.foundation.language.LineMark

// The markdown surface painted for editing (the `edit` view's
// decorations): each line's kind is the block it opens — `cm-h1`/
// `cm-h2` for headings, `cm-list` for items — and inline `*italic*`/
// `**bold**` runs paint so successful markup stands out. Kinds are
// CSS classes the host's editor binds.
def markdownLineMarks(text: String): List[LineMark] =
  text.split("\n", -1).toList.map { line =>
    if (line.startsWith("## "))
      LineMark("cm-h2", (0, 2, "cm-marker") :: inlineSpans(line, 3))
    else if (line.startsWith("# "))
      LineMark("cm-h1", (0, 1, "cm-marker") :: inlineSpans(line, 2))
    else if (line.startsWith("* "))
      LineMark("cm-list", (0, 1, "cm-marker") :: inlineSpans(line, 2))
    else LineMark("", inlineSpans(line, 0))
  }

// `**bold**` and `*italic*` runs, painted whole — markers included —
// the same pairing the parser applies (`**` binds first; a lone `*`
// is literal text and paints nothing).
private def inlineSpans(
    line: String, from: Int): List[(Int, Int, String)] = {
  val spans = List.newBuilder[(Int, Int, String)]
  var i = from
  while (i < line.length) {
    if (line.startsWith("**", i)) {
      val end = line.indexOf("**", i + 2)
      if (end > i + 2) { spans += ((i, end + 2, "cm-bold")); i = end + 2 }
      else i += 1
    } else if (line(i) == '*') {
      val end = line.indexOf('*', i + 1)
      if (end > i + 1) { spans += ((i, end + 1, "cm-italic")); i = end + 1 }
      else i += 1
    } else i += 1
  }
  spans.result()
}
