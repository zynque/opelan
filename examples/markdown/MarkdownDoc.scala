package opelan.examples.markdown

import opelan.examples.termOf
import opelan.foundation.document._
import opelan.foundation.document.Detached._

// A markdown document's content: a `markdown` node whose children are
// the blocks — `h1`/`h2` headings, `list`s of `item`s, and `para`
// paragraphs. Inline markup lives inside a block's children: plain
// StringData leaves as text runs among `bold`/`italic` span nodes.
//
//   # Title        →  h1(inline…)
//   ## Section     →  h2(inline…)
//   * item         →  list(item(inline…)…) — consecutive `* ` lines
//                     group under one list
//   para text      →  para(inline…)
//
// The constructs are the language's placeholder terms (MarkdownDef);
// documents carry them by ref.

val MarkdownTag = "markdown"
val H1Tag = "h1"
val H2Tag = "h2"
val ListTag = "list"
val ItemTag = "item"
val ParaTag = "para"
val BoldTag = "bold"
val ItalicTag = "italic"

// The text surface — markdown text to the content subtree. Total:
// every non-blank line is a block and an unmatched `*` stays literal
// text, so no input fails.
def parseMarkdown(text: String): DetachedNode[NodeData] =
  n(markdownTag(MarkdownTag), blocksOf(text.split("\n", -1).toList)*)

private def blocksOf(lines: List[String]): List[DetachedNode[NodeData]] =
  lines match {
    case Nil => Nil
    case line :: rest if line.startsWith("## ") =>
      n(markdownTag(H2Tag), inlinesOf(line.drop(3))*) :: blocksOf(rest)
    case line :: rest if line.startsWith("# ") =>
      n(markdownTag(H1Tag), inlinesOf(line.drop(2))*) :: blocksOf(rest)
    case line :: rest if line.startsWith("* ") =>
      val (items, more) = rest.span(_.startsWith("* "))
      n(markdownTag(ListTag),
        (line :: items).map(l =>
          n(markdownTag(ItemTag), inlinesOf(l.drop(2))*))*) ::
        blocksOf(more)
    case line :: rest if line.trim.isEmpty => blocksOf(rest)
    case line :: rest =>
      n(markdownTag(ParaTag), inlinesOf(line)*) :: blocksOf(rest)
  }

// A line's inline runs: `**bold**` and `*italic*` spans among plain
// text. `**` binds first; a `*` with no closing match is literal.
def inlinesOf(text: String): List[DetachedNode[NodeData]] = {
  val out = List.newBuilder[DetachedNode[NodeData]]
  val plain = new StringBuilder
  def flush(): Unit =
    if (plain.nonEmpty) { out += sl(plain.toString); plain.clear() }
  var i = 0
  while (i < text.length) {
    if (text.startsWith("**", i)) {
      val end = text.indexOf("**", i + 2)
      if (end > i + 2) {
        flush()
        out += n(markdownTag(BoldTag),
          inlinesOf(text.substring(i + 2, end))*)
        i = end + 2
      } else { plain += text(i); i += 1 }
    } else if (text(i) == '*') {
      val end = text.indexOf('*', i + 1)
      if (end > i + 1) {
        flush()
        out += n(markdownTag(ItalicTag),
          inlinesOf(text.substring(i + 1, end))*)
        i = end + 1
      } else { plain += text(i); i += 1 }
    } else { plain += text(i); i += 1 }
  }
  flush()
  out.result()
}
