package opelan.ui.typeddoc

import opelan.examples.{implsFor, languageForDoc}
import opelan.foundation.document._
import opelan.foundation.langdef.{
  commitMetaInput, langDefFromDocument, linkRuleUses, metaConsOf, Impls}
import opelan.foundation.language.docText
import opelan.ui.editor.DocPush
import opelan.ui.fp.Update
import TypedDocOutput._

// A projected input edit: parse through the doc's language — via the
// pane's per-input session, so re-editing an input reparses in proportion
// to the edit — and commit the content in place. The node's id
// survives, and so do the ids of every child whose content didn't
// change (commitContent). An unchanged commit — e.g. a blur with no
// edit — is a no-op, so projections can't churn versions. Parsed
// constructs arrive as inline refs; the commit imports them
// (internalizeRefs).
private[typeddoc] def commitInputText(
    m: TypedDocModel, id: Int,
    text: String): Update[TypedDocModel, TypedDocOutput, TypedDocInput] =
  (commitMetaInput(m.doc, m.docUrl.getOrElse(""), id, text, metaImpls(m, id))
      .map((_, m.inputSessions))
    .orElse(languageForDoc(m.doc, m.store)
      .flatMap(_.commitInput(id, text, m.inputSessions)))) match {
    case Some((content, _)) if unchangedInput(m.doc, id, content) =>
      Update(m)
    case Some((content, sessions)) =>
      commitContent(id, content, m.doc)
          .flatMap(internalizeRefs(_, id))
          .map(linkRuleUses(_, id)) match {
        case Right(d) =>
          val msg = "Input updated"
          val live = reachableIds(d)
          Update(
            m.copy(
              doc = d, pushedDoc = d, pushedSelect = None,
              pushMode = DocPush.Merge("Input edit"),
              text = docText(d, langFor(m)), textEpoch = m.textEpoch + 1,
              status = msg, inputSessions = sessions.retain(live)),
            Vector(DocChanged(d), Status(msg)))
        case Left(err) =>
          Update(m.copy(status = err), Vector(Status(err)))
      }
    case None =>
      Update(m.copy(status = "No input grammar"),
        Vector(Status("No input grammar")))
  }

// The impls a `rule` commit needs to desugar phrase meanings through
// the def's own syntax — the doc being edited is the def, so its
// lexicon comes from the foundational bundle the def names.
private def metaImpls(m: TypedDocModel, id: Int): Impls =
  if (metaConsOf(m.doc, id).contains("rule"))
    m.docUrl.map(u => implsFor(langDefFromDocument(u, m.doc)))
      .getOrElse(Impls())
  else Impls()

// An input commit that reproduces the node's current content (compared
// with its imports inlined, as the parser writes constructs).
private def unchangedInput(
    d: Document[NodeData], id: Int, content: DetachedNode[NodeData]): Boolean =
  extractExternalized(d, id).contains(content)
