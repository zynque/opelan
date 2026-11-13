package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.typed.sameNode
import NodeShape._
import opelan.foundation.means.engine.{foundationalOf, foundationRefs}

// Placeholder definitions — terms the workbench needs before the
// foundation is rich enough to define them as ordinary (non-foundation)
// definitions. They sit beside the foundation primitives and resolve
// through the same ambient name↔ref mechanism, but are a separate
// category, marked `placeholder`: intended to move up out of the
// foundation layer once the definition language can express them.
//
//   meta        — lang: language's own constructs (`term`, `vocabulary`,
//                 `definition`, `meaning`, …): the bootstrap anchor,
// until
//                 def documents can declare their constructs
//   structural  — the sheet language's grid terms (`ref`, `column`,
//                 `row`, …) and the markdown language's block/inline
//                 constructs: structural vocabulary no definition
//                 document authors yet
//
// Every placeholder anchors in `placeholderDoc` — an outline document
// seeded at `placeholderUrl` that lists the registry's names, so a
// reference's displayed name resolves through the ordinary store path.
case class PlaceholderTerm(
    name: String, shape: NodeShape, ref: ExternalNodeReference)

val placeholderUrl = "opelan:foundation/placeholders"

private def anchored(i: Int, t: NodeTerm): PlaceholderTerm =
  PlaceholderTerm(t.name, t.shape, ExternalNodeReference(placeholderUrl, 0, i))

// The meta constructs — lang: language's vocabulary, in `metaTerms`
// order (Defs.scala).
val metaPlaceholders: List[PlaceholderTerm] =
  metaTerms.zipWithIndex.map((t, i) => anchored(i, t))

// The sheet language's structural terms — `ref(column("B"), row(3))`
// is a cell reference: `column` a field node, `row` the field's number.
val structuralPlaceholders: List[PlaceholderTerm] = List(
  NodeTerm("sheet", Any),
  NodeTerm("hrow", Any),
  NodeTerm("row", Any),
  NodeTerm("label", Leaf),
  NodeTerm("literal", Leaf),
  NodeTerm("ref", Fixed(2)),
  NodeTerm("column", Fixed(1)),
  NodeTerm("formula", Any)).zipWithIndex.map((t, i) =>
  anchored(metaTerms.length + i, t))

// The markdown language's structural terms — the block and inline
// constructs its documents hold (`* ` items group under `list`;
// `**bold**`/`*italic*` are inline spans of a block's text runs).
val markdownPlaceholders: List[PlaceholderTerm] = List(
  NodeTerm("markdown", Any),
  NodeTerm("h1", Any),
  NodeTerm("h2", Any),
  NodeTerm("list", Any),
  NodeTerm("item", Any),
  NodeTerm("para", Any),
  NodeTerm("bold", Any),
  NodeTerm("italic", Any)).zipWithIndex.map((t, i) =>
  anchored(metaTerms.length + structuralPlaceholders.length + i, t))

val placeholderTerms: List[PlaceholderTerm] =
  metaPlaceholders ++ structuralPlaceholders ++ markdownPlaceholders

val metaPlaceholderRefs: Map[String, ExternalNodeReference] =
  metaPlaceholders.map(t => t.name -> t.ref).toMap

val structuralPlaceholderRefs: Map[String, ExternalNodeReference] =
  structuralPlaceholders.map(t => t.name -> t.ref).toMap

val markdownPlaceholderRefs: Map[String, ExternalNodeReference] =
  markdownPlaceholders.map(t => t.name -> t.ref).toMap

// Placeholder name → its ref. The two groups share names (`ref`,
// `literal`); the meta names win by convention — the def documents are
// the ambient-heavy readers, and a language whose own vocabulary
// declares the name overrides this map at every consumer.
val placeholderRefs: Map[String, ExternalNodeReference] =
  markdownPlaceholderRefs ++ structuralPlaceholderRefs ++ metaPlaceholderRefs

// The placeholder a ref names, if any.
def placeholderOf(
    ref: ExternalNodeReference): Option[PlaceholderTerm] =
  placeholderTerms.find(t => sameNode(t.ref, ref))

// Every ambient name → ref: the foundation primitives and the
// placeholders — usable in meanings with no vocabulary entry at all.
val ambientRefs: Map[String, ExternalNodeReference] =
  foundationRefs ++ placeholderRefs

// The ambient term a ref names — a foundation primitive, else a
// placeholder. The ref→name direction of ambientRefs.
def ambientTermOf(ref: ExternalNodeReference): Option[NodeTerm] =
  foundationalOf(ref)
    .map(d => NodeTerm(d.name, Fixed(d.arity), Some(d.ref)))
    .orElse(placeholderOf(ref)
      .map(t => NodeTerm(t.name, t.shape, Some(t.ref))))

// The registry as a document: the name leaf of placeholder i is node i
// (buildDocument numbers the children post-order, the title root
// last). Seeded (ui/workbench/DemoDocs) so `refName` resolves a
// placeholder ref's displayed name through the ordinary store path —
// and so the registry is browsable. Deliberately not def-typed.
lazy val placeholderDoc: Document[NodeData] =
  buildDocument(DetachedNode(
    NodeData.StringData("foundation: placeholders"),
    placeholderTerms.map(t =>
      DetachedNode.leaf(NodeData.StringData(t.name)))))
