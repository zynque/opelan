package opelan.ui.typeddoc

import opelan.examples.languageForDoc
import opelan.foundation.document._
import opelan.foundation.language.docText
import opelan.ui.editor.DocPush
import opelan.ui.fp.Update
import TypedDocOutput._

// A projected cell edit: parse through the doc's language — via the
// pane's per-cell session, so re-editing a cell reparses in proportion
// to the edit — and commit the content in place. The node's id
// survives, and so do the ids of every child whose content didn't
// change (commitContent). An unchanged commit — e.g. a blur with no
// edit — is a no-op, so projections can't churn versions. Parsed
// constructs arrive as inline refs; the commit imports them
// (internalizeRefs).
private[typeddoc] def commitCellText(
    m: TypedDocModel, id: Int,
    text: String): Update[TypedDocModel, TypedDocOutput, TypedDocInput] =
  languageForDoc(m.doc, m.store)
    .flatMap(_.commitCell(id, text, m.cellSessions)) match {
    case Some((content, _)) if unchangedCell(m.doc, id, content) =>
      Update(m)
    case Some((content, sessions)) =>
      commitContent(id, content, m.doc)
          .flatMap(internalizeRefs(_, id)) match {
        case Right(d) =>
          val msg = "Cell updated"
          val live = reachableIds(d)
          Update(
            m.copy(
              doc = d, pushedDoc = d, pushedSelect = None,
              pushMode = DocPush.Merge("Cell edit"),
              text = docText(d, langFor(m)), textEpoch = m.textEpoch + 1,
              status = msg, cellSessions = sessions.retain(live)),
            Vector(DocChanged(d), Status(msg)))
        case Left(err) =>
          Update(m.copy(status = err), Vector(Status(err)))
      }
    case None =>
      Update(m.copy(status = "No cell grammar"),
        Vector(Status("No cell grammar")))
  }

// A cell commit that reproduces the node's current content (compared
// with its imports inlined, as the parser writes constructs).
private def unchangedCell(
    d: Document[NodeData], id: Int, content: DetachedNode[NodeData]): Boolean =
  extractExternalized(d, id).contains(content)
