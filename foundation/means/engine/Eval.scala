package opelan.foundation.means.engine

import opelan.foundation.document._
import opelan.foundation.document.typed.{externalRefOf, sameNode}
import opelan.foundation.means._
import opelan.foundation.view.ViewTree._

// Evaluation derived from a rule set (DESIGN.md, *A meaning is a term,
// evaluated*): a node's construct resolves to an authored semantics
// rule — one whose pattern is a construct application, `square(a)
// means mul(a, a)` — else to a foundational definition by reference,
// else a gap. Semantics rules form a dependency DAG over the
// constructs their meanings use: a rule's layer is one above the
// deepest rule it consults (foundational definitions are layer 0),
// and a cycle renders as a gap at the rule that closes it (*Layers
// form a DAG*, Never Broken). Refs resolve through the document; a
// ref cycle is a gap.
//
//   evalNode(semanticsOf(set), terms, doc, id, Set(id))

// The eval half of a set: its semantics rules keyed by the construct
// they evaluate, the derived layers, and the rules whose evaluation
// would revisit themselves — the closing edges.
case class Semantics(
    rules: Map[String, Rule],
    layers: Map[String, Int],
    cyclic: Set[String])

def semanticsOf(set: RuleSet): Semantics = {
  val rules = set.rules.flatMap(r =>
    constructForm(r.pattern).map(_._1 -> r)).toMap
  val deps = rules.view.mapValues(r => meaningConstructs(r.meaning)).toMap
  val layers = scala.collection.mutable.Map.empty[String, Int]
  val cyclic = scala.collection.mutable.Set.empty[String]
  def layer(tag: String, path: List[String]): Int =
    if (path.contains(tag)) { cyclic += tag; 0 }
    else layers.getOrElseUpdate(tag,
      deps.getOrElse(tag, Set.empty)
        .map(t => layer(t, tag :: path)).foldLeft(0)(_ max _) + 1)
  rules.keys.foreach(layer(_, Nil))
  Semantics(rules, layers.toMap, cyclic.toSet)
}

// `constructForm` — the construct-application pattern of a semantics
// rule — lives on Pattern (Rule.scala), shared with vocabulary
// derivation.
private def constructForm(p: Pattern): Option[(String, List[String])] =
  p.constructForm

// The construct tags a meaning's evaluation may consult — every
// non-builtin tag, transitively. A `quote` holds concrete content,
// no bindings.
private def meaningConstructs(m: Meaning): Set[String] =
  (if (Meaning.isBuiltin(m.tag)) Set.empty[String] else Set(m.tag)) ++
    m.arguments.flatMap(meaningConstructs).toSet

// The tag a node applies — its construct's term name through the
// import entry (examples/Terms.termOf, kept local so the engine stays
// in foundation).
private def termOf(
    terms: Map[String, ExternalNodeReference],
    doc: Document[NodeData], id: Int): Option[String] =
  externalRefOf(doc, id).flatMap(r =>
    terms.collectFirst { case (t, tr) if sameNode(tr, r) => t })

// A node's value: a numeric leaf is itself; an `InternalNodeRef` leaf
// evaluates the target's subtree (visiting catches ref cycles); a gap
// blocks it, carrying its text and source id; a construct's ref names
// its semantics — an authored rule wins (like authored operand rules
// over builtin operands, Compile.scala), then a foundational
// definition by reference. Left carries (display text, node id).
def evalNode(
    semantics: Semantics,
    terms: Map[String, ExternalNodeReference],
    doc: Document[NodeData], id: Int,
    visiting: Set[Int]): Either[(String, Int), NodeData] =
  doc.getNode(id) match {
    case None => Left(("missing", id))
    case Some(node) =>
      if (node.childIds.isEmpty) node.data match {
        case d @ (NodeData.IntData(_) | NodeData.FloatData(_)) =>
          Right(d)
        case NodeData.InternalNodeRef(target) =>
          if (visiting(target)) Left(("cycle", target))
          else evalNode(semantics, terms, doc, target, visiting + target)
        case NodeData.GapData(text)   => Left((text, id))
        case NodeData.StringData(s)   => Left((s, id))
        case other                    => Left((NodeDataText.show(other), id))
      }
      else termOf(terms, doc, id) match {
        case Some(tag) if semantics.cyclic(tag) => Left(("cycle", id))
        case Some(tag) if semantics.rules.contains(tag) =>
          semantics.rules.get(tag) match {
            case Some(rule) => evalRule(rule, tag, semantics,
              terms, doc, id, visiting)
            case None       => Left((tag, id))
          }
        case Some(tag) =>
          // The floor by name: `add`'s term resolves to the
          // foundational definition its ref names (bridge until vocab
          // entries can import the foundational entries directly).
          foundationRefs.get(tag).flatMap(foundationalOf) match {
            case Some(d) =>
              evalChildren(semantics, terms, doc, node.childIds, visiting)
                .flatMap(vs => d.eval(vs).left.map(_ -> id))
            case None => Left((tag, id))
          }
        case None =>
          // A construct whose ref IS a foundational entry's —
          // documents authored against a def that imports the floor.
          externalRefOf(doc, id).flatMap(foundationalOf) match {
            case Some(d) =>
              evalChildren(semantics, terms, doc, node.childIds, visiting)
                .flatMap(vs => d.eval(vs).left.map(_ -> id))
            case None => Left((NodeDataText.show(node.data), id))
          }
      }
  }

// An authored semantics rule: its placeholders bind the node's
// children positionally, each evaluated first — the meaning then
// evaluates over the bound values.
private def evalRule(
    rule: Rule, tag: String,
    semantics: Semantics,
    terms: Map[String, ExternalNodeReference],
    doc: Document[NodeData], id: Int,
    visiting: Set[Int]): Either[(String, Int), NodeData] =
  doc.getNode(id) match {
    case Some(node) =>
      constructForm(rule.pattern) match {
        case Some((_, names)) if names.length == node.childIds.length =>
          evalChildren(semantics, terms, doc, node.childIds, visiting)
            .flatMap(vs => evalTerm(semantics, terms,
              rule.meaning, names.zip(vs).toMap, doc, id, visiting))
        case _ => Left((tag, id))
      }
    case None => Left(("missing", id))
  }

// A semantics meaning evaluated over bound values — the same term
// evaluation applyMeaning performs at parse time, one layer deeper:
// `use` is the bound value, a construct's arguments evaluate then it
// applies (authored rule first, then a foundational def by reference —
// `reference` wins over the terms-table lookup), `builtin` names a
// foundational definition directly, `literal` is a leaf term.
private def evalTerm(
    semantics: Semantics,
    terms: Map[String, ExternalNodeReference],
    m: Meaning, env: Map[String, NodeData],
    doc: Document[NodeData], at: Int,
    visiting: Set[Int]): Either[(String, Int), NodeData] =
  m.tag match {
    case "use" | "source" =>
      m.arguments.headOption.flatMap(Meaning.nameOf).flatMap(env.get)
        .toRight(("unbound", at))
    case "literal" =>
      m.arguments.headOption
        .toRight(("literal", at))
        .flatMap(evalTerm(semantics, terms, _, env, doc, at, visiting))
    case "builtin" =>
      m.arguments.headOption.flatMap(Meaning.nameOf)
        .flatMap(foundationRefs.get)
        .flatMap(foundationalOf)
        .toRight(("builtin", at))
        .flatMap(d => evalArgs(semantics, terms,
          m.arguments.tail, env, doc, at, visiting)
          .flatMap(vs => d.eval(vs).left.map(_ -> at)))
    case "quote" =>
      m.quoted.map(_.data).filter(d =>
          d.isInstanceOf[NodeData.IntData] ||
          d.isInstanceOf[NodeData.FloatData])
        .toRight(("quote", at))
    case tag if Meaning.isBuiltin(tag) => Left((tag, at))
    case tag if semantics.cyclic(tag) => Left(("cycle", at))
    case tag => semantics.rules.get(tag) match {
      case Some(rule) =>
        evalArgs(semantics, terms, m.arguments, env, doc, at, visiting)
          .flatMap(vs => constructForm(rule.pattern) match {
            case Some((_, names)) if names.length == vs.length =>
              evalTerm(semantics, terms,
                rule.meaning, names.zip(vs).toMap, doc, at, visiting)
            case _ => Left((tag, at))
          })
      case None =>
        val primitive = m.reference.orElse(terms.get(tag))
          .flatMap(foundationalOf)
          .orElse(foundationRefs.get(tag).flatMap(foundationalOf))
        primitive match {
          case Some(d) =>
            evalArgs(semantics, terms, m.arguments, env, doc, at,
              visiting).flatMap(vs => d.eval(vs).left.map(_ -> at))
          case None => Left((tag, at))
        }
    }
  }

private def evalChildren(
    semantics: Semantics,
    terms: Map[String, ExternalNodeReference],
    doc: Document[NodeData], childIds: List[Int],
    visiting: Set[Int]): Either[(String, Int), List[NodeData]] =
  childIds.foldRight[Either[(String, Int), List[NodeData]]](Right(Nil)) {
    (cid, acc) => for {
      v <- evalNode(semantics, terms, doc, cid, visiting)
      vs <- acc
    } yield v :: vs
  }

private def evalArgs(
    semantics: Semantics,
    terms: Map[String, ExternalNodeReference],
    args: List[Meaning], env: Map[String, NodeData],
    doc: Document[NodeData], at: Int,
    visiting: Set[Int]): Either[(String, Int), List[NodeData]] =
  args.foldRight[Either[(String, Int), List[NodeData]]](Right(Nil)) {
    (m, acc) => for {
      v <- evalTerm(semantics, terms, m, env, doc, at, visiting)
      vs <- acc
    } yield v :: vs
  }

// The "eval" view derived: the value as text, or the gap evaluation is
// waiting on tagged with the blocking node's id.
def evalView(
    semantics: Semantics,
    terms: Map[String, ExternalNodeReference])(
    doc: Document[NodeData], id: Int): DetachedNode[NodeData] =
  evalNode(semantics, terms, doc, id, Set(id)) match {
    case Right(v)         => from(id, txt(valueText(v)))
    case Left((msg, gid)) => from(gid, gap(msg))
  }
