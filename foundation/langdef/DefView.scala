package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.view.ViewTree
import opelan.foundation.view.ViewTree._

// The "def" view of a language-definition document: decode the doc and
// lay the definition out readably — title, description, schema kinds,
// the grammar as indented combinator text, view bindings, and the cell
// grammar's target. The document is still the source of truth; this is
// the skim-friendly rendering of it.
def defView(doc: Document[NodeData], contentRootId: Int)
    : DetachedNode[NodeData] = {
  val d = langDefFromDocument("", doc)
  block(List(
    Some(heading(txt(d.title))),
    Some(txt(d.description)),
    Some(section("schema", d.schema.map(k =>
      txt(s"${k.name} — ${shapeName(k.shape)}")))),
    d.syntax.map(s =>
      section("syntax", grammarLines(s.grammar).map(txt))),
    Some(section("views", d.views.map(v =>
      txt(s"${v.name} → ${v.builtin}${settingsText(v.settings)}")))),
    d.cellParser.map(cp =>
      section("cells", List(txt(cellText(cp)))))).flatten*)
}

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

private def cellText(cp: CellParserDef): String = cp match {
  case CellParserDef.Builtin(name) => s"builtin $name"
  case CellParserDef.Language(ref, fb) =>
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
