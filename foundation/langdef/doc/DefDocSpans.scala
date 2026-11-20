package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means.text._

// `defDocParse` reporting, for every node it builds, the span of text
// the node covers — the `lang:` surface's provenance
// (foundation/language/TextProvenance). An outline node covers its
// whole line; under `definitions`, a `definition`'s sub-structure —
// pattern pieces, meaning arguments, where-clauses — covers the
// tokens RuleGrammar positioned (ruleSpannedChildren).
def defDocParseSpanned(text: String): SpannedTree[NodeData] = {
  var offset = 0
  val entries = text.split("\n", -1).toList.flatMap { line =>
    val start = offset
    offset += line.length + 1
    Option.unless(line.trim.isEmpty) {
      val indent = line.takeWhile(c => c == ' ' || c == '\t').length
      (indent, dataOf(line.trim), line.trim, Span(start, start + line.length))
    }
  }
  entries match {
    case Nil =>
      DetachedNode.leaf(SpannedNode(externalMeta("dialect"), Span(0, 0)))
    case (_, data, _, span) :: rest =>
      DetachedNode(SpannedNode(data, span), spanBlock(rest, data))
  }
}

private type LineEntry = (Int, NodeData, String, Span)

// Each entry absorbs the entries indented deeper than itself —
// Outline.block's shape. A string child of a `definitions` node is a
// means line: it reads into the `definition` encoding, its outline
// children dropped as `structured` drops them, its parts stamped with
// the grammar tree's positions.
private def spanBlock(
    entries: List[LineEntry], parentData: NodeData)
    : List[SpannedTree[NodeData]] = entries match {
  case Nil => Nil
  case (indent, data, text, span) :: tail =>
    val (deeper, rest) = tail.span(_._1 > indent)
    val (nodeData, children) = data match {
      case NodeData.StringData(line) if isMeta(parentData, "definitions") =>
        val tree = ruleGrammar(ParseInput(line, ruleLexicon), 0).tree
        val definition = ruleNode(
          readRuleTree(tree).getOrElse(gapRule), "", Map.empty, externalMeta)
        // A bare line's offsets locate its tokens; a quoted one reads
        // as a rule too (the StringData's value), but positions shift.
        (definition.data,
          if (line == text)
            ruleSpannedChildren(definition, tree, span.from + indent, span)
          else definition.children.map(unpositioned(_, span)))
      case _ => (data, spanBlock(deeper, data))
    }
    DetachedNode(SpannedNode(nodeData, span), children) ::
      spanBlock(rest, parentData)
}
