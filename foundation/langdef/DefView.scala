package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.view.ViewTree
import opelan.foundation.view.ViewTree._

// The "def" view of a language-definition document: decode the doc and
// lay the definition out readably — title, description, the vocabulary
// of terms, the grammar as indented combinator text, the means rules as
// editable lines, view bindings, and the input grammar's target. The
// document is still the source of truth; this is the skim-friendly
// rendering of it.
// A rules section renders each line as a projected input bound to its
// text leaf, so editing a rule commits through the doc language's
// input parser and the next decode re-reads it; and `ctx.interpret`
// runs each worked example against the live definition, a failing one
// showing as a hole. `ctx.docUrl` supplies the document's stored
// identity — a def doc's URL is the language it defines, and the
// decode needs it so interpretation binds the right implementations.
def defView(doc: Document[NodeData], contentRootId: Int,
    ctx: ViewContext = ViewContext()): DetachedNode[NodeData] = {
  val d = langDefFromDocument(ctx.docUrl.getOrElse(""), doc)
  block(List(
    Some(heading(txt(d.title))),
    Some(txt(d.description)),
    Some(section("vocabulary", d.vocabulary.map(t =>
      txt(s"${t.name} — ${shapeName(t.shape)}")))),
    d.syntax.map(s =>
      section("syntax", grammarLines(s.grammar).map(txt) ++
        s.rules.toList.flatMap(rs =>
          docRuleLines(doc, contentRootId, exampleChecker(d, ctx))
            .getOrElse(ruleLines(rs))))),
    Some(section("views", d.views.map(v =>
      txt(s"${v.name} → ${v.builtin}${settingsText(v.settings)}")))),
    d.inputParser.map(cp =>
      section("inputs", List(txt(inputParserText(cp)))))).flatten*)
}

// The meta construct a node applies, by identity — the reader's `cons`.
private[langdef] def docCons(
    doc: Document[NodeData], id: Int): Option[String] =
  metaConsOf(doc, id)

private[langdef] def leafText(doc: Document[NodeData], id: Int): String =
  doc.getNode(id).map(_.data).collect { case NodeData.StringData(s) => s }
    .getOrElse("")

// One item per line: each entry is its own block.
private def section(
    name: String, items: List[DetachedNode[NodeData]])
    : DetachedNode[NodeData] =
  block(heading(txt(name)) +: items.map(block(_))*)

private def settingsText(settings: Map[String, String]): String =
  if (settings.isEmpty) ""
  else settings.toList.sorted.map((k, v) => s"$k: $v").mkString(" (", ", ", ")")

private def shapeName(s: NodeShape): String = s match {
  case NodeShape.Leaf    => "leaf"
  case NodeShape.Any     => "any"
  case NodeShape.Fixed(n) => s"fixed $n"
}

private def inputParserText(cp: InputParserDef): String = cp match {
  case InputParserDef.Builtin(name) => s"builtin $name"
  case InputParserDef.Language(ref, fb) =>
    s"language ${ref.documentUrl}#${ref.nodeId} (fallback: $fb)"
}

private def grammarLines(g: GrammarDef, indent: String = ""): List[String] =
  g match {
    case GrammarDef.Tok(kind, leaf) =>
      List(s"${indent}tok $kind → ${leafName(leaf)}")
    case GrammarDef.Choice(alts) =>
      s"${indent}choice" :: alts.flatMap(grammarLines(_, indent + "  "))
    case GrammarDef.Parens(inner) =>
      s"${indent}parens" :: grammarLines(inner, indent + "  ")
    case GrammarDef.Signum => List(s"${indent}signum")
    case GrammarDef.Junk(start, run) =>
      List(s"${indent}junk starts {${start.mkString(" ")}} runs {${run.mkString(" ")}}")
    case GrammarDef.ChainLeft(ops, atom) =>
      s"${indent}chain ${opsText(ops)}" :: grammarLines(atom, indent + "  ")
    case GrammarDef.Precedence(atom, levels) =>
      s"${indent}precedence" ::
        levels.map(l => s"$indent  level ${opsText(l)}") ++
        grammarLines(atom, indent + "  ")
    case GrammarDef.Phrase(inner, stops) =>
      s"${indent}phrase stops {${stops.mkString(" ")}}" ::
        grammarLines(inner, indent + "  ")
    case GrammarDef.Named(name, inner) =>
      s"${indent}$name =" :: grammarLines(inner, indent + "  ")
    case GrammarDef.Ref(name) => List(s"${indent}ref $name")
    case GrammarDef.Hole      => List(s"${indent}?")
  }

private def opsText(ops: Map[String, String]): String =
  ops.toList.sortBy(_._1).map { case (sym, tag) => s"$sym → $tag" }
    .mkString("{", " ", "}")

private def leafName(l: LeafKind): String = l match {
  case LeafKind.IntLit        => "int"
  case LeafKind.FloatLit      => "float"
  case LeafKind.RefLit(p)     => s"ref ($p)"
  case LeafKind.HoleLeaf      => "hole"
  case LeafKind.Text          => "text"
}
