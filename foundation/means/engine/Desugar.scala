package opelan.foundation.means.engine

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means._

// The decided meaning desugar (DESIGN.md §4, *Phrases desugar at
// entry*): a `reparse` term's template parses through the language's
// own rules *when the rule is read*, and the parsed tree becomes
// meaning terms — placeholders as `use`, the language's constructs as
// construct terms by reference, concrete content as `quote`. At apply
// a desugared meaning is only ever term evaluation, so the `5` in
// `5 %` → div(5, 100) is the bound node itself, not a reparsed copy.
//
// The phrase parses against the *whole* set — `compileRules` over a
// placeholder-accepting operand — so a phrase may use syntax the set
// defines after the rule that means it. The ordering tolerance the
// apply-time form needed comes from compiling rather than deferring.
// A phrase the set can't yet read lands as a quoted hole — Never
// Broken, and in the document form (C.6) the meaning input retries
// when the missing rule arrives.

// Every `reparse` meaning in the set desugars; other terms pass
// through unchanged.
def desugarRuleSet(
    set: RuleSet,
    atom: Parser,
    tag: String => NodeData,
    name: String,
    lexicon: Lexicon,
    lexicals: Map[String, LexicalTerm] = Map.empty): RuleSet =
  set.copy(rules = set.rules.map { r =>
    if (r.meaning.tag == "reparse")
      r.copy(meaning = desugarPhrase(
        r.meaning.phrase.getOrElse(Pattern(Nil)),
        r.meaning.phrase.toList.flatMap(_.placeholders).toSet,
        set, atom, tag, name, lexicon, lexicals))
    else r
  })

// A `reparse` template as a term tree: placeholder names lex as
// operand atoms marking themselves (each becomes `use`), and the rest
// of the phrase parses with the language's own rules — construct
// nodes become construct terms, leaves and holes become `quote`s.
def desugarPhrase(
    template: Pattern,
    placeholders: Set[String],
    set: RuleSet,
    atom: Parser,
    tag: String => NodeData,
    name: String,
    lexicon: Lexicon,
    lexicals: Map[String, LexicalTerm] = Map.empty): Meaning = {
  val expression = phrase(
    compileRules(set, choice(placeholderAtom(placeholders), atom),
      tag, name, lexicals),
    Set.empty)
  val text = template.pieces.map {
    case Piece.Placeholder(n, _) => n
    case Piece.Literal(l, _)     => l
  }.mkString(" ")
  toTerm(expression(In(text, lexicon), 0).tree, namesByData(set, tag))
}

// The node data a placeholder atom emits — a mark the desugar's
// translation turns into `use`. The mark survives `use`-meaning
// growth (`(a)` keeps the bound mark), so a phrase like `(a)` still
// lands as `use(a)`.
private[means] val PlaceholderMark = NodeData.StringData("placeholder")

private def placeholderAtom(names: Set[String]): Parser =
  (in, pos) => {
    val scanned = scanAt(in, pos)
    scanned.token match {
      case Some(t) if names(t.text) =>
        POut(t.to, PTree(PlaceholderMark, t.text, t.from, t.text.length),
          ok = true, scanned.examined)
      case _ =>
        // The local `emptyHole` (the grammar package's is private).
        val at = skipSpace(in.input, pos)
        POut(pos, PTree(NodeData.GapData(""), "", at, 0), ok = false,
          scanned.examined.max(at + 1))
    }
  }

// The parsed phrase's tree as a meaning term: marks become `use`,
// nodes carrying a construct's data become construct terms (keeping
// the ref a `lang:` doc's nodes would hold), and everything else —
// leaves, gaps, nodes no term names — is quoted verbatim.
private def toTerm(t: PTree, names: Map[NodeData, String]): Meaning =
  if (t.data == PlaceholderMark) Meaning.use(t.text)
  else names.get(t.data) match {
    case Some(name) =>
      Meaning(name, t.kids.map(k => toTerm(k, names)), refOf(t.data))
    case None => Meaning.quote(scrubbed(t))
  }

// A quoted subtree holds no marks — a placeholder inside content that
// didn't resolve to a term reads as a hole rather than leaking the
// desugar's mark into the output.
private def scrubbed(t: PTree): PTree =
  if (t.data == PlaceholderMark)
    t.copy(data = NodeData.GapData(t.text))
  else t.copy(kids = t.kids.map(scrubbed))

private def refOf(d: NodeData): Option[ExternalNodeReference] = d match {
  case NodeData.ExternalNodeRef(r) => Some(r)
  case _ => None
}

// Node data → construct name, for every construct term in the set —
// so a parsed node names the meaning that would have built it.
private def namesByData(
    set: RuleSet, tag: String => NodeData): Map[NodeData, String] =
  set.rules.flatMap(r => constructs(r.meaning)).map { m =>
    m.reference.map(NodeData.ExternalNodeRef(_)).getOrElse(tag(m.tag)) ->
      m.tag
  }.toMap

private def constructs(m: Meaning): List[Meaning] =
  if (Meaning.isConstruct(m))
    m +: m.arguments.flatMap(constructs)
  else Nil
