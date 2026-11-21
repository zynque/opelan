package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means.engine.LexicalTerm
import opelan.foundation.means.text._

// The `lang:` document's text surface as a production — what
// defDocParseSpanned built by hand, as a grammar. A document is its
// first non-blank line's node; each line absorbs the non-blank lines
// indented deeper than itself; a string line under `definitions`
// parses as a rule through ruleGrammar, its encoding stamped with the
// rule tree's spans. Named parsers memoize at `document`, line, and
// means-line granularity, so a session reparses in proportion to the
// edit. The syntax's `surface` names `document` — the meta-language's
// compiled grammar roots here, and spans, sessions, and provenance
// come through the shared machinery.
val documentTerm: LexicalTerm =
  LexicalTerm(Some(rule("document")((in, pos) =>
    lineAt(in, pos) match {
      case None => ParseResult(pos,
        ParseTree(externalMeta("dialect"), "", pos, 0),
        applied = true, in.input.length + 1)
      case Some(e) =>
        // The root absorbs every following line — a shallower line is
        // still its child, not a sibling (`phrase` would otherwise
        // collapse the whole document into one gap).
        val (children, end, seen) =
          outlineChildren(in, e.to, -1, e.from, e.data)
        ParseResult(end,
          ParseTree(e.data, in.input.substring(e.from, e.to),
            e.from, e.to - e.from, children),
          applied = true, seen)
    })), _ => true)

// One outline line: indent measured in leading spaces and tabs, its
// trimmed text decoded (meta constructs by ref, payloads by
// NodeDataText), and where the trimmed content starts.
private[doc] case class LineEntry(
    indent: Int, data: NodeData, text: String,
    from: Int, to: Int, contentFrom: Int)

// The next non-blank line at or after `pos` — blank lines are not
// entries, as in the outline format.
private[doc] def lineAt(in: ParseInput, pos: Int): Option[LineEntry] = {
  var i = pos
  var found: Option[LineEntry] = None
  while (found.isEmpty && i < in.input.length) {
    val eol = in.input.indexOf('\n', i) match {
      case -1 => in.input.length
      case n  => n
    }
    val line = in.input.substring(i, eol)
    val indent = line.takeWhile(c => c == ' ' || c == '\t').length
    if (line.trim.nonEmpty)
      found = Some(LineEntry(
        indent, dataOf(line.trim), line.trim, i, eol, i + indent))
    i = eol + 1
  }
  found
}

// A node's children: following non-blank lines indented deeper than it,
// each parsed by the production the node's data calls for — under
// `definitions`, a string line is a rule, and a means line's own
// outline children are dropped as `structured` drops them. Returns the
// trees (their `from`s relative to the parent), the position after the
// block, and how far was read — including the line that ended it.
private def outlineChildren(
    in: ParseInput, pos: Int, indent: Int, parentFrom: Int,
    parentData: NodeData): (List[ParseTree], Int, Int) = {
  val children = List.newBuilder[ParseTree]
  var cur = pos
  var seen = pos + 1
  var more = true
  while (more) lineAt(in, cur) match {
    case Some(e) if e.indent > indent =>
      val lineParser = e.data match {
        case _: NodeData.StringData
            if isMeta(parentData, "definitions") => meansLine
        case _ => outlineLine
      }
      val r = lineParser(in, e.from)
      children += r.tree.copy(from = r.tree.from - parentFrom)
      cur = r.position
      seen = seen.max(r.examined)
    case Some(e) => seen = seen.max(e.to + 1); more = false
    case None    => seen = in.input.length + 1; more = false
  }
  (children.result(), cur, seen)
}

private lazy val outlineLine: Parser = rule("outline-line")((in, pos) =>
  lineAt(in, pos) match {
    case Some(e) =>
      val (children, end, seen) =
        outlineChildren(in, e.to, e.indent, e.from, e.data)
      ParseResult(end,
        ParseTree(e.data, in.input.substring(e.from, e.to),
          e.from, e.to - e.from, children),
        applied = true, seen)
    case None => ParseResult(pos,
      ParseTree(NodeData.GapData(""), "", pos, 0), applied = false, pos + 1)
  })
