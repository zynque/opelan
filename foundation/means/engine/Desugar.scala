package opelan.foundation.means.engine

import opelan.foundation.document._
import opelan.foundation.grammar._
import opelan.foundation.means._

// The decided meaning desugar (DESIGN.md §4, *Phrases desugar at
// entry*): a phrase spelling parses through the language's own rules
// *when it is read*, and the parsed tree becomes meaning terms —
// placeholders as `use`, the language's constructs as construct terms
// by reference, concrete content as `quote`. At apply a desugared
// meaning is only ever term evaluation, so the `5` in `5 %` →
// div(5, 100) is the bound node itself, not a reparsed copy.
//
// The phrase parses against the *whole* set — `compileRules` over a
// placeholder-accepting operand — so a phrase may use syntax the set
// defines after the rule that means it. The ordering tolerance the
// apply-time form needed comes from compiling rather than deferring.
// A phrase the set can't yet read lands as a quoted hole — Never
// Broken — and `rephraseRuleSet` retries it as rules arrive: the
// meaning input's healing.

// Phrase text as a meaning term: placeholder names lex as operand
// atoms marking themselves (each becomes `use`), and the rest parses
// with the language's own rules — construct nodes become construct
// terms, leaves and holes become `quote`s.
def phraseMeaning(
    text: String,
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
  toTerm(expression(In(text, lexicon), 0).tree, namesByData(set, tag))
}

// Retry a meaning that is one quoted hole — the stored form of a
// phrase the set couldn't yet read. A re-read that reaches real terms
// replaces it; one that lands as a hole again keeps the authored text.
def rephraseMeaning(
    m: Meaning,
    placeholders: Set[String],
    set: RuleSet,
    atom: Parser,
    tag: String => NodeData,
    name: String,
    lexicon: Lexicon,
    lexicals: Map[String, LexicalTerm] = Map.empty): Meaning =
  holeQuoteText(m).fold(m) { text =>
    val read = phraseMeaning(text, placeholders, set, atom, tag, name,
      lexicon, lexicals)
    if (holeQuoteText(read).isDefined) m else read
  }

// Every quoted-hole meaning in the set retries; other terms pass
// through unchanged. Interpretation runs this, so a stored hole heals
// the moment the rule it needs arrives — even in a doc never
// re-committed.
def rephraseRuleSet(
    set: RuleSet,
    atom: Parser,
    tag: String => NodeData,
    name: String,
    lexicon: Lexicon,
    lexicals: Map[String, LexicalTerm] = Map.empty): RuleSet =
  set.copy(rules = set.rules.map(r =>
    r.copy(meaning = rephraseMeaning(r.meaning,
      r.pattern.placeholders.toSet, set, atom, tag, name, lexicon,
      lexicals))))

// The text a quoted hole keeps — Some only when the meaning is exactly
// a hole, the "phrase the set can't yet read" marker. The text lives
// in the gap's data: decoded trees carry no source span.
def holeQuoteText(m: Meaning): Option[String] =
  if (m.tag == "quote")
    m.quoted.collect {
      case PTree(NodeData.GapData(text), _, _, _, Nil, _) => text }
  else None

// A desugared meaning that still holds a hole — the phrase didn't
// fully read.
def hasGap(m: Meaning): Boolean =
  m.arguments.exists(hasGap) || m.quoted.exists(gapTree)

private def gapTree(t: PTree): Boolean =
  t.data.isInstanceOf[NodeData.GapData] || t.kids.exists(gapTree)

// The node data a placeholder atom emits — a mark the desugar's
// translation turns into `use`. The mark survives `use`-meaning
// growth (`(a)` keeps the bound mark), so a phrase like `(a)` still
// lands as `use(a)`.
private[means] val PlaceholderMark = NodeData.StringData("placeholder")

def placeholderAtom(names: Set[String]): Parser =
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
def toTerm(t: PTree, names: Map[NodeData, String]): Meaning =
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
def namesByData(
    set: RuleSet, tag: String => NodeData): Map[NodeData, String] =
  set.rules.flatMap(r => constructs(r.meaning)).map { m =>
    m.reference.map(NodeData.ExternalNodeRef(_)).getOrElse(tag(m.tag)) ->
      m.tag
  }.toMap

private def constructs(m: Meaning): List[Meaning] =
  if (Meaning.isConstruct(m))
    m +: m.arguments.flatMap(constructs)
  else Nil
