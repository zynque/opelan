package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.language.{HoleRetry, TextSyntax}

// Remember a parse attempt for every hole cell in `doc` under the cell
// grammar's current syntax. Attempts the last language change left
// valid are reused (HoleRetry.parse); attempts for texts no longer in
// the document are dropped.
def rememberCellHoles(
    doc: Document[NodeData], syntax: Option[TextSyntax],
    retry: HoleRetry): HoleRetry =
  syntax.fold(retry) { s =>
    val texts = holeTexts(doc)
    val remembered = texts.foldLeft(retry)((r, t) => r.parse(s, t)._2)
    HoleRetry(remembered.attempts.filter((t, _) => texts.contains(t)))
  }

private def holeTexts(doc: Document[NodeData]): Set[String] =
  doc.preorder(doc.rootId).iterator.flatMap((id, _) => doc.getNode(id)).collect {
    case n if n.childIds.isEmpty => n.data
  }.collect { case NodeData.GapData(t) if t.trim.nonEmpty => t }.toSet
