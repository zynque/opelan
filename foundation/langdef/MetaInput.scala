package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.grammar.PTree
import opelan.foundation.langdef.doc._
import opelan.foundation.langdef.syntax._
import opelan.foundation.means.{
  desugarPhrase, readRuleOrHole, Meaning, Pattern, Rule}

// Text committed against a meta construct produces the structured
// subtree its decode reads back, never a bare string: a `rule` line
// becomes pattern + meaning nodes, its constructs bound to the doc's
// own vocabulary entries. `None` for a node that isn't meta content —
// an ordinary document input goes through the doc's own input grammar.
// `impls` supplies the defined language's lexicon so a phrase meaning
// desugars at entry (DESIGN §4, *Phrases desugar at entry*).
def commitMetaInput(
    doc: Document[NodeData], docUrl: String,
    id: Int, text: String, impls: Impls = Impls()): Option[DetachedNode[NodeData]] =
  metaConsOf(doc, id) match {
    case Some("rule") =>
      val read = readRuleOrHole(text)
      Some(ruleNode(desugarCommitted(doc, docUrl, id, read, impls),
        vocabularyTermIds(doc, docUrl), pieceIdsFor(doc, id), externalMeta))
    case _ => None
  }

// A committed `reparse` meaning desugars at commit — through the doc's
// own rules with the new rule swapped into the set — so the stored
// meaning is terms. A phrase the set can't yet read keeps its
// `reparse` form: the interpretation-time desugar retries it as rules
// arrive — the meaning's hole-retry healing.
private def desugarCommitted(
    doc: Document[NodeData], docUrl: String,
    id: Int, read: Rule, impls: Impls): Rule = {
  val defined = langDefFromDocument(docUrl, doc)
  (for {
    syn   <- defined.syntax
    set   <- syn.rules
    lex   <- impls.lexicons.get(syn.lexicon)
    index <- ruleIndex(doc, id)
    if read.meaning.tag == "reparse"
    ph    <- read.meaning.phrase
  } yield {
    val tag = termTagOf(defined)
    val meaning = desugarPhrase(ph, ph.placeholders.toSet,
      set.copy(rules = set.rules.updated(index, read)),
      interpret(syn.grammar, tag), tag, syn.lexicon, lex,
      lexicalTerms(impls, leafTermNames(defined)))
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

// A desugared meaning that still holds a hole — the phrase didn't
// fully read, so the authored `reparse` form keeps healing rights.
private def hasGap(m: Meaning): Boolean =
  m.arguments.exists(hasGap) || m.quoted.exists(gapTree)

private def gapTree(t: PTree): Boolean =
  t.data.isInstanceOf[NodeData.GapData] || t.kids.exists(gapTree)

// The doc's own vocabulary as the term name → entry-id map a structured
// meaning encodes against — empty for a doc that isn't a def.
private[langdef] def vocabularyTermIds(
    doc: Document[NodeData], docUrl: String): Map[String, Int] =
  langDefFromDocument(docUrl, doc).vocabulary
    .flatMap(k => k.ref.map(k.name -> _.nodeId)).toMap
