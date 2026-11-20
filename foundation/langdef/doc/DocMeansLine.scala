package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means.text._

// A `means` line under `definitions`: the rule grammar parses the
// line's trimmed text — bounded to the line so nothing beyond its end
// can attach — and the encoding's nodes are stamped with the rule
// tree's spans (ruleSpannedChildren). A quoted line reads as a rule
// too (its StringData's value), but positions shift, so its nodes
// inherit the line's span. A means line's own outline children are
// dropped, as `structured` drops them — the parse skips their lines.
private[doc] lazy val meansLine: Parser = rule("means-line")((in, pos) =>
  lineAt(in, pos) match {
    case Some(e @ LineEntry(_, NodeData.StringData(line), _, _, _, _)) =>
      val self = Span(e.from, e.to)
      val bare = line == e.text
      val content = if (bare)
        in.input.substring(e.contentFrom, e.contentFrom + e.text.length)
        else line
      val parsed = ruleGrammar(ParseInput(content, in.lexicon), 0)
      val definition = ruleNode(
        readRuleTree(parsed.tree).getOrElse(gapRule), "", Map.empty,
        externalMeta)
      val spanned =
        if (bare)
          ruleSpannedChildren(definition, parsed.tree, e.contentFrom, self)
        else definition.children.map(unpositioned(_, self))
      val (end, seen) = pastDeeper(in, e.to, e.indent,
        if (bare) e.contentFrom + parsed.examined else e.to + 1)
      ParseResult(end,
        ParseTree(definition.data, in.input.substring(e.from, e.to),
          e.from, e.to - e.from,
          spanned.map(parseTreeOf(_, in, e.from))),
        applied = true, seen)
    case _ => ParseResult(pos,
      ParseTree(NodeData.GapData(""), "", pos, 0), applied = false, pos + 1)
  })

// The position after the non-blank lines indented deeper than
// `indent` (skipped, not parsed) plus how far was read — the line
// that ended the run counts, as in outlineChildren.
private def pastDeeper(
    in: ParseInput, pos: Int, indent: Int, seen: Int): (Int, Int) = {
  var cur = pos
  var out = seen.max(pos + 1)
  var more = true
  while (more) lineAt(in, cur) match {
    case Some(e) if e.indent > indent =>
      cur = e.to; out = out.max(e.to + 1)
    case Some(e) => out = out.max(e.to + 1); more = false
    case None    => out = in.input.length + 1; more = false
  }
  (cur, out)
}

// A span-stamped subtree as a parse node — `from` rebased to the
// parent's start, the source slice as its text.
private def parseTreeOf(
    t: SpannedTree[NodeData], in: ParseInput, base: Int): ParseTree = {
  val s = t.data.span
  ParseTree(t.data.data, in.input.substring(s.from, s.to),
    s.from - base, s.to - s.from,
    t.children.map(parseTreeOf(_, in, s.from)))
}
