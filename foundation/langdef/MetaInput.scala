package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.grammar.PTree
import opelan.foundation.langdef.doc._
import opelan.foundation.langdef.syntax._
import opelan.foundation.language.InputSessions
import opelan.foundation.means.{Meaning, Pattern, Rule}
import opelan.foundation.means.engine.{hasGap, holeQuoteText, phraseMeaning}
import opelan.foundation.means.text.{
  readConstraintsText, readPattern, readRuleOrHole}

// Text committed against a meta construct produces the structured
// subtree its decode reads back, never a bare string: a `rule` line
// becomes pattern + meaning nodes, its constructs bound to the doc's
// own vocabulary entries; `pattern`, `meaning`, and `where` fields
// commit their own sections — the meaning through the defined
// language's rules (DESIGN §4, *Phrases desugar at entry*). `None`
// for a node that isn't meta content — an ordinary document input
// goes through the doc's own input grammar. `inputs` carries the
// meaning field's incremental session between commits.
def commitMetaInput(
    doc: Document[NodeData], docUrl: String,
    id: Int, text: String,
    inputs: InputSessions = InputSessions(),
    impls: Impls = Impls())
    : Option[(DetachedNode[NodeData], InputSessions)] =
  metaConsOf(doc, id) match {
    case Some("rule") =>
      Some((ruleNode(desugarCommitted(doc, docUrl, id,
          readRuleOrHole(text), impls),
        docUrl, pieceIdsFor(doc, id), externalMeta), inputs))
    case Some("pattern") =>
      Some((patternNode(readPattern(text), externalMeta), inputs))
    case Some("meaning") =>
      Some(commitMeaningInput(doc, docUrl, id, text, inputs, impls))
    case Some("where") =>
      Some((whereNode(readConstraintsText(text), externalMeta), inputs))
    case _ => None
  }

// The `rule` node a meta commit belongs to — the node itself or its
// parent — where `linkRuleUses` re-points `use`/`source` operands at
// the pattern's piece nodes after the commit lands.
def enclosingRuleId(
    doc: Document[NodeData], id: Int): Int =
  if (metaConsOf(doc, id).contains("rule")) id
  else doc.getNode(id).flatMap(_.parentId)
    .filter(metaConsOf(doc, _).contains("rule")).getOrElse(id)

// A committed meaning that landed as one quoted hole re-reads through
// the doc's own rules — with the new rule swapped into the set — the
// whole-line commit's share of *Phrases desugar at entry*. A phrase
// the set can't yet read keeps its quoted-hole form: the
// interpretation-time retry (`rephraseRuleSet`) heals it as rules
// arrive.
private def desugarCommitted(
    doc: Document[NodeData], docUrl: String,
    id: Int, read: Rule, impls: Impls): Rule = {
  val defined = langDefFromDocument(docUrl, doc)
  (for {
    syn   <- defined.syntax
    set   <- syn.rules
    lex   <- impls.lexicons.get(syn.lexicon)
    index <- ruleIndex(doc, id)
    text  <- holeQuoteText(read.meaning)
  } yield {
    val tag = termTagOf(defined)
    val meaning = phraseMeaning(text, read.pattern.placeholders.toSet,
      set.copy(rules = set.rules.updated(index, read)),
      interpret(syn.grammar, tag), tag, syn.lexicon, lex,
      lexicalTerms(impls))
    if (hasGap(meaning)) read else read.copy(meaning = meaning)
  }).getOrElse(read)
}

// The committed rule's index in the decoded set: its position among
// the `rule` children of its `rules` parent.
private def ruleIndex(doc: Document[NodeData], id: Int): Option[Int] =
  for {
    parent <- doc.getNode(id).flatMap(_.parentId)
    if metaConsOf(doc, parent).contains("rules")
    rules = doc.childrenOf(parent).filter(metaConsOf(doc, _).contains("rule"))
    i = rules.indexOf(id) if i >= 0
  } yield i

// Term name → the decoded construct's ref (or a hole — Never Broken).
private[langdef] def termTagOf(d: LanguageDef): String => NodeData = {
  val refs = termRefs(d)
  name => refs.get(name).map(NodeData.ExternalNodeRef(_))
    .getOrElse(NodeData.GapData(name))
}
