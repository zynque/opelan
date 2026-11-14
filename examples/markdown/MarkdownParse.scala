package opelan.examples.markdown

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.langdef._
import opelan.foundation.langdef.LeafKind
import opelan.foundation.langdef.syntax.{LeafDef, leafAccepts, leafParser}
import opelan.foundation.means._
import opelan.foundation.means.engine.{LexicalTerm, ruleTree, sequenceRule}
import opelan.foundation.means.text.readPattern

// The named productions the def's `where` terms bind — `Impls.
// productions` — combinator parsers over the rule set's own compiled
// rules, so the `lang:` document's `definitions` section is the grammar
// this surface runs (the same shapes compileRules derives, composed to
// document level). `plain` is an ordinary leaf: one text token, the
// emphasis operand (`*t*`, `**t**` — emphasis doesn't nest). `runs` is
// a line's inline run — a non-empty `some` of spans, all on one line:
// `withinLine` rejects an element whose skipped whitespace crossed the
// line's end, so a heading's `t` can't swallow the next block. `blocks`
// is the document: `list`s of `*` items, `#`/`##` headings, `para`s —
// the `markdown` node a text commit stores.

private val textLeaf: Parser =
  token("text")(t => NodeData.StringData(t.text))

// Whitespace between spans is text too — `scanAt` would skip it, so a
// `*bold* tail` run keeps its ` ` as a verbatim leaf.
private val spaceLeaf: Parser = (in, pos) => {
  var end = pos
  while (end < in.input.length &&
         (in.input(end) == ' ' || in.input(end) == '\t')) end += 1
  if (end > pos)
    ParseResult(end, ParseTree(NodeData.StringData(
      in.input.substring(pos, end)),
      in.input.substring(pos, end), pos, end - pos),
      applied = true, end)
  else failed(pos)
}

// A stray marker is literal text rather than a failure — `* 3` keeps
// its `*` (Never Broken). Last of the run's alternatives.
private val strayLeaf: Parser = (in, pos) => {
  val scanned = scanAt(in, pos)
  scanned.token match {
    case Some(t) => ParseResult(t.to, ParseTree(
      NodeData.StringData(t.text), t.text, t.from, t.text.length),
      applied = true, scanned.examined)
    case None => failed(pos)
  }
}

private def failed(pos: Int): ParseResult =
  ParseResult(pos, ParseTree(NodeData.GapData(""), "", pos, 0),
    applied = false, pos + 1)

private def ruleOf(pattern: String): Rule =
  markdownRules.rules.find(_.pattern.key == readPattern(pattern).key)
    .getOrElse(sys.error(s"markdown rules have no '$pattern'"))

// `p` must apply on the current line — its tree may not start across a
// newline from `pos`. How a `runs` element stays on its line: marker
// tokens skip whitespace freely, so without this a `t` would pick up
// the next block's text.
private def withinLine(p: Parser): Parser = (in, pos) => {
  val r = p(in, pos)
  if (r.applied && in.input.substring(pos, r.tree.from).contains('\n'))
    ParseResult(pos, ParseTree(NodeData.GapData(""), "", pos, 0),
      applied = false, r.examined)
  else r
}

val plainLeaf = LeafDef(List("text" -> LeafKind.Text))

val plainTerm: LexicalTerm =
  LexicalTerm(Some(leafParser(plainLeaf)), leafAccepts(plainLeaf))

// One inline span: a strict emphasis rule (no closed-rule recovery —
// an unmatched `*` falls through to `strayLeaf` instead of gap-eating
// forward), an intra-line space, a plain text token, or any token
// kept verbatim.
private lazy val runsCore: Parser = choice(
  sequenceRule(ruleOf("**t**"), gapHere, markdownTag,
    Map("t" -> plainTerm), strict = true),
  sequenceRule(ruleOf("*t*"), gapHere, markdownTag,
    Map("t" -> plainTerm), strict = true),
  spaceLeaf, textLeaf, strayLeaf)

// A flat `runs` node: `first` then `rest` repeated, all elements as
// children. `first` differs from `rest` where the run's start may sit
// on a later line than `pos` (a paragraph starting after blank
// lines).
private def flatRuns(first: Parser, rest: Parser): Parser = (in, pos) => {
  val f = first(in, pos)
  if (!f.applied) f
  else {
    var cur = f.position
    var seen = f.examined
    val children = List.newBuilder[ParseTree]
    children += f.tree
    var more = true
    while (more) {
      val r = rest(in, cur)
      seen = seen.max(r.examined)
      if (r.applied && r.position > cur) { children += r.tree; cur = r.position }
      else more = false
    }
    ParseResult(cur, ParseTree(markdownTag(RunsTag),
      in.input.substring(f.tree.from, cur), f.tree.from,
      cur - f.tree.from, children.result()), applied = true, seen)
  }
}

// Spaces and tabs only — the gap after a `#`/`*` marker is syntax,
// not content; a newline ends the line instead.
private def inlineSkip(input: String, pos: Int): Int = {
  var i = pos
  while (i < input.length && (input(i) == ' ' || input(i) == '\t')) i += 1
  i
}

// The `runs` production bound in `# t`, `## t`, `* t` — the marker's
// own whitespace skipped, every element on the marker's line.
lazy val runsTerm: LexicalTerm = LexicalTerm(
  Some((in, pos) =>
    flatRuns(withinLine(runsCore), withinLine(runsCore))(
      in, inlineSkip(in.input, pos))),
  _ => true)

private def blockSeq(pattern: String): Parser =
  sequenceRule(ruleOf(pattern), gapHere, markdownTag,
    Map("t" -> runsTerm))

// A paragraph: the same flat runs, but its first element may start
// after the blank lines that precede it (`withinLine` would refuse
// the newline it had to cross).
private lazy val paraParser: Parser = (in, pos) => {
  val r = flatRuns(runsCore, withinLine(runsCore))(in, pos)
  if (!r.applied) r
  else ParseResult(r.position,
    ruleTree(ruleOf("t"), Map("t" -> r.tree), in,
      r.tree.from, r.position, markdownTag),
    applied = true, r.examined)
}

// The `blocks` production: consecutive `* t` items group under `list`,
// then headings, then a paragraph of whatever's left — the
// `markdown` node.
lazy val blocksTerm: LexicalTerm = LexicalTerm(
  Some(many(markdownTag(MarkdownTag))(choice(
    some(markdownTag(ListTag))(blockSeq("* t")),
    blockSeq("# t"), blockSeq("## t"), paraParser))),
  _ => true)

// The document's text surface — the `blocks` production's tree, the
// content a text commit stores.
def markdownParse(text: String): DetachedNode[NodeData] =
  toDetached(
    blocksTerm.parser.get(ParseInput(text, markdownLexicon), 0).tree)
