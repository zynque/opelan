package opelan.foundation.language

import opelan.foundation.document._

// A language gives meaning to a document type: it knows how to interpret
// the content subtree of a typed document. For now languages are defined
// in Scala; later each definition will itself be a document and the ref
// will point at real content (bootstrapping).
//
// A language's derived views are rendered as view trees — detached
// document data in the foundation/view vocabulary — and the ui layer
// interprets them into Views. The structural "editor" view is generic
// and needs no language support, so it is not listed in `views`.
trait Language {

  // Identity: the external reference typed documents use to name this
  // language. documentUrl names the (future) definition document and
  // nodeId picks the type within it.
  def typeRef: ExternalNodeReference

  def name: String

  // Derived views the language supports (e.g. "print", "eval"). The
  // list is the perspective menu the UI presents for documents of this
  // language; the first entry is the default a host falls back to when
  // a requested view doesn't exist here.
  def views: List[String]

  def defaultView: Option[String] = views.headOption

  // Render a derived view of the typed document's content as a view
  // tree. Left for genuine failures (no content, wrong type), Right
  // otherwise — a hole-blocked result is a GapData node in the tree, not
  // an error. Emitted nodes may carry SourceRef provenance so the view
  // can point back at the document nodes that produced them.
  def render(view: String, doc: Document[NodeData]): Either[String, DetachedNode[NodeData]]

  // Text syntax, if the language has one: parse cell text into a content
  // subtree. Recovering parsers return Some even for malformed input,
  // embedding holes (GapData nodes) where the text does not fit.
  def parse(text: String): Option[DetachedNode[NodeData]] = None

  // Cell grammar, if the language's views emit "input" elements: parse
  // text committed in a projected editor into a replacement subtree for
  // the node it edits. None = the language's views are read-only.
  def parseCell(text: String): Option[DetachedNode[NodeData]] = None

  // A projected cell commit through the host's per-cell sessions, so
  // re-editing a cell reparses in proportion to the edit. Its content
  // must equal `parseCell`'s; the default parses whole.
  def commitCell(id: Int, text: String, cells: CellSessions)
      : Option[(DetachedNode[NodeData], CellSessions)] =
    parseCell(text).map((_, cells))

  // The text syntax as an incremental parser, if the language has one:
  // the text surface keeps a TextSession over it, so an edit reparses in
  // proportion to its size. Its content must equal `parse`'s.
  def textSyntax: Option[TextSyntax] = None

  // Render with the host's remembered hole attempts, so holes are
  // retried only when a language change touched a rule they consulted.
  def renderWith(view: String, doc: Document[NodeData], retry: HoleRetry)
      : Either[String, DetachedNode[NodeData]] = render(view, doc)

  // Remember a parse attempt for each hole in `doc` (reusing `retry`'s
  // attempts where a language change left them valid).
  def rememberHoles(doc: Document[NodeData], retry: HoleRetry): HoleRetry =
    retry

  // The document `typeRef` names: a readable description of this
  // language, seeded into the document store so following a typed
  // document's type ref lands on a real node. Descriptive, not
  // authoritative — the registry still resolves the ref to this
  // implementation — until the definition format arrives (M2) and the
  // document *becomes* the language.
  def langDoc: Document[NodeData]
}
