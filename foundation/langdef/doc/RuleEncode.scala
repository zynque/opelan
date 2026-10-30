package opelan.foundation.langdef.doc

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.grammar.PTree
import opelan.foundation.means._
import opelan.foundation.means.text._

// A rule set as document nodes (DESIGN §4, *Everything by reference*):
// each `rule` holds `pattern` + `meaning` (+ `where`) sections; pattern
// pieces are `placeholder`/`literal` nodes with a `glued` marker when
// the source wrote them unspaced; the meaning is the term subtree —
// `use`/`source`/`builtin` operands name their bound placeholder (by
// name until a link pass points them at the piece node), `quote`
// keeps its desugared subtree (a quoted hole is a phrase the set
// couldn't yet read), and
// a construct's data is its name — the first occurrence in doc order
// is the term's declaration, so a document of the defined language
// references that node by id. The binding list stays
// tiers of pattern texts and examples stay input/expected pairs.
//
// `meta` supplies meta-construct data: `pending` inside a def-doc
// build, `externalMeta` in detached content (a committed input has no
// resolvePending pass). The reader also accepts the older text form —
// a `rule` whose first child is a string — so docs written before the
// structured encoding and lines edited mid-migration still decode.
private[langdef] def rulesNode(
    set: RuleSet,
    url: String,
    meta: String => NodeData): DetachedNode[NodeData] =
  n(meta("rules"),
    (set.rules.map(ruleNode(_, url, Map.empty, meta)) ++
      bindingNode(set.tiers, meta) ++
      set.examples.map(e =>
        n(meta("example"), sl(e.input), sl(e.expected))))*)

private[langdef] def ruleNode(
    r: Rule, url: String, pieceIds: Map[String, Int],
    meta: String => NodeData): DetachedNode[NodeData] =
  n(meta("rule"),
    (List(
      patternNode(r.pattern, meta),
      n(meta("meaning"), meaningTermNode(r.meaning, url, pieceIds, meta))) ++
      (if (r.where.isEmpty) Nil else List(whereNode(r.where, meta))))*)

private def bindingNode(
    tiers: List[Tier], meta: String => NodeData): List[DetachedNode[NodeData]] =
  if (tiers.isEmpty) Nil
  else List(n(meta("binding"),
    tiers.map(t => n(meta("tier"),
      t.patterns.map(p => sl(printPattern(p)))*))*))

private[langdef] def whereNode(
    cs: List[Constraint], meta: String => NodeData): DetachedNode[NodeData] =
  n(meta("where"), cs.map(c =>
    n(meta("constraint"), (c.placeholders.map(sl) :+ sl(c.term))*))*)

private[langdef] def patternNode(
    p: Pattern, meta: String => NodeData): DetachedNode[NodeData] =
  n(meta("pattern"), p.pieces.map(pieceNode(_, meta))*)

private def pieceNode(
    p: Piece, meta: String => NodeData): DetachedNode[NodeData] = {
  val (tag, text) = p match {
    case Piece.Placeholder(name, _) => ("placeholder", name)
    case Piece.Literal(literal, _)  => ("literal", literal)
  }
  n(meta(tag), (sl(text) +:
    (if (p.glued) List(DetachedNode.leaf(meta("glued"))) else Nil))*)
}

private[langdef] def meaningTermNode(
    m: Meaning, url: String, pieceIds: Map[String, Int],
    meta: String => NodeData): DetachedNode[NodeData] = m.tag match {
  case "use" | "source" | "builtin" =>
    n(meta(m.tag), m.arguments.flatMap(Meaning.nameOf).map(name =>
      pieceIds.get(name)
        .map(t => DetachedNode.leaf(NodeData.InternalNodeRef(t)))
        .getOrElse(sl(name)))*)
  // A literal's operands are value terms: a bare name binds a pattern
  // piece like a `use` operand does, anything else is a term subtree.
  case "literal" =>
    n(meta("literal"), m.arguments.map(a => Meaning.nameOf(a) match {
      case Some(name) => pieceIds.get(name)
          .map(t => DetachedNode.leaf(NodeData.InternalNodeRef(t)))
          .getOrElse(sl(name))
      case None => meaningTermNode(a, url, pieceIds, meta)
    })*)
  case "quote" =>
    n(meta("quote"), m.quoted.toList.map(detachedOf)*)
  case tag =>
    DetachedNode(constructData(m, tag, url),
      m.arguments.map(meaningTermNode(_, url, pieceIds, meta)))
}

// A construct meaning's node data: its external ref when the term
// belongs to another document, its bare name otherwise — a local
// construct carries its name in every occurrence, and the first
// occurrence in doc order IS the term's declaration (there is no
// vocabulary section; identity derives positionally at read).
private def constructData(
    m: Meaning, tag: String, url: String): NodeData =
  m.reference match {
    case Some(r) if r.documentUrl != url => NodeData.ExternalNodeRef(r)
    case _                             => NodeData.StringData(tag)
  }

private def detachedOf(t: PTree): DetachedNode[NodeData] =
  DetachedNode(t.data, t.kids.map(detachedOf))

// Meta-construct data for detached rule content — a real import ref,
// not a pending marker, so a subtree produced outside a def-doc build
// (an input commit) still names its constructs.
private[langdef] def externalMeta(name: String): NodeData =
  metaRef(name).map(NodeData.ExternalNodeRef(_))
    .getOrElse(NodeData.GapData(name))
