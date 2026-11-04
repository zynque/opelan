package opelan.foundation.langdef.view

import opelan.foundation.document._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc._
import opelan.foundation.means.{Meaning, Rule, RuleSet}
import opelan.foundation.means.engine.{meaningTree, printWith}
import opelan.foundation.means.text.{
  meaningView, patternView, printMeaning, readPattern, ruleView, whereView}
import opelan.foundation.view.ViewTree._

// The rules section of the "def" view, rendered as projected inputs:
// a structured rule line is its fields — `pattern`, `meaning`, and
// `where` inputs bound to their own nodes, so the meaning field
// commits through the defined language's rules (commitMetaInput) —
// while binding patterns and example fields bind to their text
// leaves. A text-form `rule` (a string first child — the transitional
// encoding) keeps a single whole-line input; a malformed line reads
// as a hole rule, never an error.
// Falls back to None when the document has no `rules` node (a def
// decoded but never written as one), so callers can print instead.
// `check` runs a worked example — input text, expected text — and
// returns a failure detail the line shows as a hole.
private[langdef] def docRuleLines(
    doc: Document[NodeData], rootId: Int, d: LanguageDef,
    check: (String, String) => Option[String])
    : Option[List[DetachedNode[NodeData]]] =
  for {
    syn <- doc.childrenOf(rootId).find(docCons(doc, _).contains("syntax"))
    rs  <- doc.childrenOf(syn).find(docCons(doc, _).contains("rules"))
  } yield doc.childrenOf(rs).flatMap(ruleLine(doc, _, d, check))

// One `rules` entry as a line: a text-form `rule` edits its leaf in
// one input bound to the rule node (a commit structures it); a
// structured rule projects `pattern`/`meaning`/`where` as inputs
// bound to their own nodes — displaying role-styled text (keywords
// emphasized, placeholders italic) that a click swaps for the edit
// box — the meaning field parses through the
// defined language per keystroke. A `binding` shows its
// tiers as comma-separated pattern inputs; an `example` is input and
// expected side by side, checked live — a mismatch trails a hole
// saying what the input read as.
private def ruleLine(
    doc: Document[NodeData], id: Int, d: LanguageDef,
    check: (String, String) => Option[String])
    : List[DetachedNode[NodeData]] =
  docCons(doc, id) match {
    case Some("rule") =>
      List(doc.childrenOf(id).headOption match {
        case Some(t)
            if doc.getNode(t).exists(
              _.data.isInstanceOf[NodeData.StringData]) =>
          input(id, txt(leafText(doc, t)))
        case Some(_) =>
          val children = doc.childrenOf(id)
          def section(name: String) =
            children.find(docCons(doc, _).contains(name))
          (section("pattern"), section("meaning")) match {
            case (Some(p), Some(m)) =>
              val r = ruleFromDoc(doc, "", 0,
                deriveTermIds(doc, doc.rootId), id)
              val fields = List(
                input(p, patternView(r.pattern)*),
                styled("keyword", txt(" means ")),
                input(m, meaningDisplay(d, r)*)) ++
                section("where").toList.flatMap(w =>
                  List(styled("keyword", txt(" where ")),
                    input(w, whereView(r.where)*)))
              flow(fields*)
            case _ => hole("rule")
          }
        case None    => hole("rule")
      })
    case Some("binding") =>
      styled("keyword", txt("binding:")) +: doc.childrenOf(id)
        .filter(docCons(doc, _).contains("tier"))
        .map(t => flow((txt("  ") +: commaInputs(doc, t))*))
    case Some("example") =>
      List(flow((doc.childrenOf(id) match {
        case a :: b :: _ =>
          val (inText, outText) = (leafText(doc, a), leafText(doc, b))
          List(styled("marker", txt("e.g. ")), input(a, txt(inText)),
            styled("keyword", txt(" means ")), input(b, txt(outText))) ++
            check(inText, outText).map(t => List(txt(" "), hole(t)))
              .getOrElse(Nil)
        case a :: _      => List(styled("marker", txt("e.g. ")),
          input(a, txt(leafText(doc, a))))
        case _           => List(hole("example"))
      })*))
    case _ => Nil
  }

// The meaning field's display children: a meaning carrying `quote`
// terms keeps the flat text its phrase projection supplies; a canonical
// spelling shows styled spans — the constructor tag and `use` names
// highlight until the field is edited.
private def meaningDisplay(
    d: LanguageDef, r: Rule): List[DetachedNode[NodeData]] =
  if (hasQuote(r.meaning)) List(txt(meaningFieldText(d, r)))
  else meaningView(r.meaning)

// A stored meaning's field text: `printMeaning`'s canonical spelling,
// except a meaning carrying `quote` terms — concrete content only a
// phrase desugars to — which projects back through the language's own
// printer, so `a / 100` reads as authored, not `div(a, 100)`. A
// quoted hole shows its `?` mark — the input wears the hole styling.
private[langdef] def meaningFieldText(d: LanguageDef, r: Rule): String =
  (for {
    set <- d.syntax.flatMap(_.rules) if hasQuote(r.meaning)
  } yield printWith(set, termTagOf(d), meaningLeaf)(
    meaningTree(r.meaning, termTagOf(d))))
    .getOrElse(printMeaning(r.meaning))

private[langdef] def hasQuote(m: Meaning): Boolean =
  m.tag == "quote" || m.arguments.exists(hasQuote)

private def meaningLeaf(data: NodeData): String = data match {
  case NodeData.StringData(s) => s
  case other                  => NodeDataText.show(other)
}

// The `where` field's text — the constraints without the keyword,
// which the line's own `where` label supplies.
private[langdef] def whereFieldText(r: Rule): String =
  r.where.map(c => c.placeholders.mkString(", ") + ": " + c.term)
    .mkString("; ")

private def commaInputs(
    doc: Document[NodeData], tierId: Int): List[DetachedNode[NodeData]] =
  doc.childrenOf(tierId).map(p =>
      input(p, patternView(readPattern(leafText(doc, p)))*))
    .flatMap(List(_, txt(", "))).dropRight(1) match {
      case Nil  => List(txt(""))
      case children => children
    }

// A rule set printed rather than edited — the fallback when the doc
// carries no `rules` node (e.g. a def decoded from an older doc) and
// for surfaces that only render text.
private[langdef] def ruleLines(set: RuleSet): List[DetachedNode[NodeData]] =
  set.rules.map(r => ruleView(r)) ++
    (if (set.tiers.isEmpty) Nil
     else styled("keyword", txt("binding:")) +: set.tiers.map(t =>
       flow((txt("  ") +: t.patterns.flatMap(p =>
         patternView(p) ++ List(txt(", ")))).dropRight(1)*)))
