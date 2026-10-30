package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.document.typed.contentId
import opelan.foundation.langdef.doc._
import opelan.foundation.langdef.syntax._
import opelan.foundation.language.{InputSessions, SyntaxChange, TextSyntax}
import opelan.foundation.means._
import opelan.foundation.means.engine._
import opelan.foundation.means.text._
import opelan.foundation.grammar._

// The meaning field's input grammar (DESIGN §4, *Phrases desugar at
// entry*): the text parses through the defined language's own rules
// with the rule's placeholders as operand atoms, so the committed
// meaning is term nodes — `use` placeholders, constructs by ref,
// `quote` concrete content — never a template to reparse. A
// `build`/`use` spelling (`div(a, b)`, `literal(neg(n))`) reads
// through the meta reader instead. Commits go through the pane's
// per-input session, so a re-edit reparses in proportion to the
// keystroke, and a language change carries the session like any
// text surface.
private[langdef] def commitMeaningInput(
    doc: Document[NodeData], docUrl: String,
    id: Int, text: String, inputs: InputSessions, impls: Impls)
    : (DetachedNode[NodeData], InputSessions) =
  // An explicit `?` is a hole — the input grammar's own convention.
  if (text.startsWith("?"))
    (meaningNode(Meaning.hole(text.drop(1)), doc, docUrl, id), inputs)
  else meaningSyntax(doc, docUrl, id, impls) match {
    case Some(syntax) => inputs.parse(id, syntax, text)
    // A meaning field somewhere its def has no rules: keep the text
    // as a quoted hole rather than corrupting the node with a string.
    case None => (meaningNode(Meaning.hole(text), doc, docUrl, id), inputs)
  }

// The incremental syntax of one meaning field: the def's own rules
// compiled over an operand that also accepts the rule's placeholder
// names, under the def's lexicon. The key carries everything the
// parser was built from — a pattern edit or a rules change restarts
// or reruns only the rules the diff names, like the text surface's.
private def meaningSyntax(
    doc: Document[NodeData], docUrl: String,
    meaningId: Int, impls: Impls): Option[TextSyntax] =
  for {
    ruleId <- doc.getNode(meaningId).flatMap(_.parentId)
      .filter(metaConsOf(doc, _).contains("rule"))
    defined = langDefFromDocument(docUrl, doc)
    s <- defined.syntax
    set <- s.rules
    lexicon <- impls.lexicons.get(s.lexicon)
  } yield {
    val tag = termTagOf(defined)
    val atom = interpret(s.grammar, tag)
    val lexicals = lexicalTerms(impls)
    val placeholders = ruleFromDoc(doc, docUrl, 0,
      deriveTermIds(doc, contentId(doc).getOrElse(doc.rootId)), ruleId)
      .pattern.placeholders.toSet
    val pieceIds = pieceIdsFor(doc, ruleId)
    val names = namesByData(set, tag)
    val tags = termRefs(defined).view
      .mapValues(NodeData.ExternalNodeRef(_)).toMap
    TextSyntax(
      phrase(compileRules(set,
        choice(placeholderAtom(placeholders), atom),
        tag, s.lexicon, lexicals), Set.empty),
      lexicon,
      (s.lexicon, s.grammar, set, placeholders, tags),
      changedSince = previous => previous.key match {
        case (lex: String, g: GrammarDef, before: RuleSet,
              ph: Set[String] @unchecked,
              beforeTags: Map[String, NodeData] @unchecked)
            if lex == s.lexicon && g == s.grammar && ph == placeholders =>
          Some(SyntaxChange(
            syntaxChanges(SyntaxDef(lex, g, Some(before)), s),
            retagBy(beforeTags, tag)))
        case _ => None
      },
      toContent = tree => meaningNode(
        readMeaningText(tree.text,
          arg => phraseMeaning(arg, placeholders, set, atom, tag,
            s.lexicon, lexicon, lexicals))
          .getOrElse(toTerm(tree, names)),
        doc, docUrl, meaningId))
  }

// A committed meaning's detached form: the `meaning` node holding the
// term subtree, `use` operands linked to the pattern's piece nodes.
private def meaningNode(
    m: Meaning, doc: Document[NodeData], docUrl: String, meaningId: Int)
    : DetachedNode[NodeData] = {
  val pieceIds = doc.getNode(meaningId).flatMap(_.parentId)
    .filter(metaConsOf(doc, _).contains("rule"))
    .map(pieceIdsFor(doc, _)).getOrElse(Map.empty)
  n(externalMeta("meaning"),
    meaningTermNode(m, docUrl, pieceIds, externalMeta))
}
