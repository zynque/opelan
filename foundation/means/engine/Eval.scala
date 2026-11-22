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
//   evalNode(semanticsOf(set, terms), terms, doc, id, Set(id))

// A term's dispatch identity: its anchor's node address — (document
// url, node id), version-insensitive like `sameNode` — or, when
// nothing anchors it, its authored name (a code-side rule over a
// construct no vocabulary declares). Construct uses dispatch by
// reference; a name only ever selects a rule that has no anchor.
type TermKey = Either[String, (String, Int)]

private def refKey(r: ExternalNodeReference): TermKey =
  Right((r.documentUrl, r.nodeId))

// The key a named term dispatches under: its anchor's ref through the
// terms table, else the bare name.
private def nameKey(
    terms: Map[String, ExternalNodeReference], name: String): TermKey =
  terms.get(name).map(refKey).getOrElse(Left(name))

// The key a meaning's construct dispatches under: its own anchored
// `reference` first (a rule read from a document), then the name's
// anchor in the terms table, else the bare tag.
private def meaningKey(
    terms: Map[String, ExternalNodeReference], m: Meaning): TermKey =
  m.reference.orElse(terms.get(m.tag)).map(refKey)
    .getOrElse(Left(m.tag))

// The eval half of a set: its semantics rules keyed by the construct
// they evaluate, the derived layers, and the rules whose evaluation
// would revisit themselves — the closing edges.
case class Semantics(
    rules: Map[TermKey, List[Rule]],
    layers: Map[TermKey, Int],
    cyclic: Set[TermKey])

def semanticsOf(
    set: RuleSet,
    terms: Map[String, ExternalNodeReference]): Semantics = {
  val rules = set.rules.flatMap(r =>
    constructForm(r.pattern).map(f => nameKey(terms, f._1) -> r))
    .groupBy(_._1).map { case (t, rs) => t -> rs.map(_._2) }
  val deps = rules.view.mapValues(rs =>
    rs.flatMap(r => meaningKeys(terms, r.meaning)).toSet).toMap
  val layers = scala.collection.mutable.Map.empty[TermKey, Int]
  val cyclic = scala.collection.mutable.Set.empty[TermKey]
  def layer(key: TermKey, path: List[TermKey]): Int =
    if (path.contains(key)) { cyclic += key; 0 }
    else layers.getOrElseUpdate(key,
      deps.getOrElse(key, Set.empty)
        .map(k => layer(k, key :: path)).foldLeft(0)(_ max _) + 1)
  rules.keys.foreach(layer(_, Nil))
  Semantics(rules, layers.toMap, cyclic.toSet)
}

// `constructForm` — the construct-application pattern of a semantics
// rule — lives on Pattern (Rule.scala), shared with vocabulary
// derivation.
private def constructForm(p: Pattern): Option[(String, List[String])] =
  p.constructForm

// The dispatch keys a meaning's evaluation may consult — every
// non-builtin construct, transitively. A `quote` holds concrete
// content, no bindings.
private def meaningKeys(
    terms: Map[String, ExternalNodeReference], m: Meaning): Set[TermKey] =
  (if (Meaning.isBuiltin(m.tag)) Set.empty[TermKey]
   else Set(meaningKey(terms, m))) ++
    m.arguments.flatMap(meaningKeys(terms, _)).toSet

// The keys a node's construct may dispatch under, and the name it
// displays with: its ref first — version-insensitive, `sameNode`'s
// notion — then the name the ref resolves to through the terms table
// or the host's resolver (`ViewContext.termName`), so a rule whose
// construct declares no anchor still matches by name.
private def nodeKeys(
    terms: Map[String, ExternalNodeReference],
    nameOf: ExternalNodeReference => Option[String],
    doc: Document[NodeData],
    id: Int): (List[TermKey], Option[String]) =
  externalRefOf(doc, id) match {
    case Some(r) =>
      val name =
        terms.collectFirst { case (t, tr) if sameNode(tr, r) => t }
          .orElse(nameOf(r))
      (refKey(r) :: name.map(n => Left(n): TermKey).toList, name)
    case None => (Nil, None)
  }

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
    visiting: Set[Int],
    nameOf: ExternalNodeReference => Option[String] =
      (_ => None)): Either[(String, Int), NodeData] =
  doc.getNode(id) match {
    case None => Left(("missing", id))
    case Some(node) =>
      if (node.childIds.isEmpty) node.data match {
        case d @ (NodeData.IntData(_) | NodeData.FloatData(_)) =>
          Right(d)
        case NodeData.InternalNodeRef(target) =>
          if (visiting(target)) Left(("cycle", target))
          else evalNode(semantics, terms, doc, target, visiting + target, nameOf)
        case NodeData.GapData(text)   => Left((text, id))
        case NodeData.StringData(s)   => Left((s, id))
        case other                    => Left((NodeDataText.show(other), id))
      }
      else {
        val (keys, name) = nodeKeys(terms, nameOf, doc, id)
        val label = name.getOrElse(NodeDataText.show(node.data))
        keys.find(k =>
          semantics.cyclic(k) || semantics.rules.contains(k)) match {
          case Some(key) if semantics.cyclic(key) => Left(("cycle", id))
          case Some(key) =>
            // A name may declare several arities (`+n` beside
            // `a + b`): the node's child count selects the rule.
            semantics.rules(key).find(r => constructForm(r.pattern)
              .exists(_._2.length == node.childIds.length)) match {
              case Some(rule) => evalRule(rule, label, semantics,
                terms, doc, id, visiting, nameOf)
              case None       => Left((label, id))
            }
          case None =>
            // A construct whose ref IS a foundational entry's —
            // documents authored against a def that imports the
            // floor — else the floor by name (the bridge until vocab
            // entries can import the foundational entries directly).
            val primitive = externalRefOf(doc, id)
              .flatMap(foundationalOf)
              .orElse(keys.collectFirst { case Left(n) => n }
                .flatMap(foundationRefs.get).flatMap(foundationalOf))
            primitive match {
              case Some(d) =>
                evalChildren(semantics, terms, doc, node.childIds,
                  visiting, nameOf)
                  .flatMap(vs => d.eval(vs).left.map(_ -> id))
              case None => Left((label, id))
            }
        }
      }
  }

// An authored semantics rule: its placeholders bind the node's
// children positionally, each evaluated first — the meaning then
// evaluates over the bound values.
private def evalRule(
    rule: Rule, label: String,
    semantics: Semantics,
    terms: Map[String, ExternalNodeReference],
    doc: Document[NodeData], id: Int,
    visiting: Set[Int],
    nameOf: ExternalNodeReference => Option[String])
    : Either[(String, Int), NodeData] =
  doc.getNode(id) match {
    case Some(node) =>
      constructForm(rule.pattern) match {
        case Some((_, names)) if names.length == node.childIds.length =>
          evalChildren(semantics, terms, doc, node.childIds, visiting, nameOf)
            .flatMap(vs => evalTerm(semantics, terms,
              rule.meaning, names.zip(vs).toMap, doc, id, visiting, nameOf))
        case _ => Left((label, id))
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
    visiting: Set[Int],
    nameOf: ExternalNodeReference => Option[String])
    : Either[(String, Int), NodeData] =
  m.tag match {
    case "use" | "source" =>
      m.arguments.headOption.flatMap(Meaning.nameOf).flatMap(env.get)
        .toRight(("unbound", at))
    case "literal" =>
      m.arguments.headOption
        .toRight(("literal", at))
        .flatMap(evalTerm(semantics, terms, _, env, doc, at, visiting, nameOf))
    case "builtin" =>
      m.arguments.headOption.flatMap(Meaning.nameOf)
        .flatMap(foundationRefs.get)
        .flatMap(foundationalOf)
        .toRight(("builtin", at))
        .flatMap(d => evalArgs(semantics, terms,
          m.arguments.tail, env, doc, at, visiting, nameOf)
          .flatMap(vs => d.eval(vs).left.map(_ -> at)))
    case "quote" =>
      m.quoted.map(_.data).filter(d =>
          d.isInstanceOf[NodeData.IntData] ||
          d.isInstanceOf[NodeData.FloatData])
        .toRight(("quote", at))
    case tag if Meaning.isBuiltin(tag) => Left((tag, at))
    case tag =>
      val key = meaningKey(terms, m)
      if (semantics.cyclic(key)) Left(("cycle", at))
      else semantics.rules.getOrElse(key, Nil).find(r =>
        constructForm(r.pattern)
          .exists(_._2.length == m.arguments.length)) match {
        case Some(rule) =>
          evalArgs(semantics, terms, m.arguments, env, doc, at,
            visiting, nameOf)
            .flatMap(vs => constructForm(rule.pattern) match {
              case Some((_, names)) =>
                evalTerm(semantics, terms,
                  rule.meaning, names.zip(vs).toMap, doc, at, visiting,
                  nameOf)
              case _ => Left((tag, at))
            })
        case None =>
          val primitive = m.reference.orElse(terms.get(tag))
            .flatMap(foundationalOf)
            .orElse(foundationRefs.get(tag).flatMap(foundationalOf))
          primitive match {
            case Some(d) =>
              evalArgs(semantics, terms, m.arguments, env, doc, at,
                visiting, nameOf).flatMap(vs =>
                  d.eval(vs).left.map(_ -> at))
            case None => Left((tag, at))
          }
      }
  }

private def evalChildren(
    semantics: Semantics,
    terms: Map[String, ExternalNodeReference],
    doc: Document[NodeData], childIds: List[Int],
    visiting: Set[Int],
    nameOf: ExternalNodeReference => Option[String])
    : Either[(String, Int), List[NodeData]] =
  childIds.foldRight[Either[(String, Int), List[NodeData]]](Right(Nil)) {
    (cid, acc) => for {
      v <- evalNode(semantics, terms, doc, cid, visiting, nameOf)
      vs <- acc
    } yield v :: vs
  }

private def evalArgs(
    semantics: Semantics,
    terms: Map[String, ExternalNodeReference],
    args: List[Meaning], env: Map[String, NodeData],
    doc: Document[NodeData], at: Int,
    visiting: Set[Int],
    nameOf: ExternalNodeReference => Option[String])
    : Either[(String, Int), List[NodeData]] =
  args.foldRight[Either[(String, Int), List[NodeData]]](Right(Nil)) {
    (m, acc) => for {
      v <- evalTerm(semantics, terms, m, env, doc, at, visiting, nameOf)
      vs <- acc
    } yield v :: vs
  }

// The "eval" view derived: the value as text, or the gap evaluation is
// waiting on tagged with the blocking node's id.
def evalView(
    semantics: Semantics,
    terms: Map[String, ExternalNodeReference],
    nameOf: ExternalNodeReference => Option[String] = (_ => None))(
    doc: Document[NodeData], id: Int): DetachedNode[NodeData] =
  evalNode(semantics, terms, doc, id, Set(id), nameOf) match {
    case Right(v)         => from(id, txt(valueText(v)))
    case Left((msg, gid)) => from(gid, gap(msg))
  }
