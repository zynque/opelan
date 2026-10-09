package opelan.foundation.document

// The two textual forms of node data — the single home for NodeData<->text:
//
//   outline — the persisted surface syntax of Outline.render/parse:
//     "abc"   string      42 / 1.5      int / float
//     ref:5   internal    ref:url@v#n   external ref
//     ?text   gap         bare          string
//
//   edit    — the editor's row display and edit-box syntax:
//     "abc"   string      42 / 1.5      int / float
//     &5      internal    ext:url@v#n   external ref
//     ?text   gap         bare          string
//
// The syntaxes differ only in ref spelling; scalars are shared.
object NodeDataText {

  // -- outline syntax -------------------------------------------------

  def show(data: NodeData): String = data match {
    case NodeData.StringData(s)        => s"\"$s\""
    case NodeData.IntData(i)           => i.toString
    case NodeData.FloatData(f)         => f.toString
    case NodeData.InternalNodeRef(id)  => s"ref:$id"
    case NodeData.ExternalNodeRef(ref) => s"ref:${refText(ref)}"
    case NodeData.GapData(text)        => s"?$text"
  }

  def parse(text: String): NodeData = text match {
    case s if s.length >= 2 && s.startsWith("\"") && s.endsWith("\"") =>
      NodeData.StringData(s.substring(1, s.length - 1))
    case s if s.startsWith("?") => NodeData.GapData(s.drop(1))
    case s if s.matches("ref:\\d+") => NodeData.InternalNodeRef(s.drop(4).toInt)
    case s if s.matches("ref:.+@\\d+#\\d+") =>
      NodeData.ExternalNodeRef(parseRef(s.drop(4)))
    case s => scalar(s)
  }

  // -- edit-box syntax ------------------------------------------------

  def showEdit(data: NodeData): String = data match {
    case NodeData.InternalNodeRef(id)  => s"&$id"
    case NodeData.ExternalNodeRef(ref) => s"ext:${refText(ref)}"
    case other                         => show(other)
  }

  def parseEdit(text: String): NodeData =
    if (text.matches("&\\d+")) NodeData.InternalNodeRef(text.drop(1).toInt)
    else if (text.matches("ext:.+@\\d+#\\d+"))
      NodeData.ExternalNodeRef(parseRef(text.drop(4)))
    else if (text.startsWith("?")) NodeData.GapData(text.drop(1))
    else scalar(text)

  // -- shared ----------------------------------------------------------

  // The shared tail of both parses: numbers, else a bare string.
  private def scalar(s: String): NodeData =
    if (s.matches("-?\\d+")) NodeData.IntData(s.toInt)
    else if (s.matches("-?\\d*\\.\\d+")) NodeData.FloatData(s.toDouble)
    else NodeData.StringData(s)

  private def refText(ref: ExternalNodeReference): String =
    s"${ref.documentUrl}@${ref.documentVersionId}#${ref.nodeId}"

  // "url@v#n" — parsed from the right so the url may itself contain
  // '@' or '#'.
  private def parseRef(body: String): ExternalNodeReference =
    ExternalNodeReference(
      body.take(body.lastIndexOf('@')),
      body.substring(body.lastIndexOf('@') + 1, body.lastIndexOf('#')).toInt,
      body.drop(body.lastIndexOf('#') + 1).toInt)
}
