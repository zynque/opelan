package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.language.{GapRetry, TextSyntax}

// Remember a parse attempt for every gap in `doc` under the input
// grammar's current syntax. Attempts the last language change left
// valid are reused (GapRetry.parse); attempts for texts no longer in
// the document are dropped.
def rememberInputGaps(
    doc: Document[NodeData], syntax: Option[TextSyntax],
    retry: GapRetry): GapRetry =
  syntax.fold(retry) { s =>
    val texts = gapTexts(doc)
    val remembered = texts.foldLeft(retry)((r, t) => r.parse(s, t)._2)
    GapRetry(remembered.attempts.filter((t, _) => texts.contains(t)))
  }

private def gapTexts(doc: Document[NodeData]): Set[String] =
  doc.preorder(doc.rootId).iterator.flatMap((id, _) => doc.getNode(id)).collect {
    case n if n.childIds.isEmpty => n.data
  }.collect { case NodeData.GapData(t) if t.trim.nonEmpty => t }.toSet
