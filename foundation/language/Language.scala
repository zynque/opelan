package opelan.foundation.language

import opelan.foundation.document._

// A language gives meaning to a document type: it knows how to interpret
// the content subtree of a typed document. For now languages are defined
// in Scala; later each definition will itself be a document and the ref
// will point at real content (bootstrapping).
//
// A language's derived views are rendered as fragments — text runs
// interleaved with holes — and the ui layer decides how to present them.
// The structural "editor" view is generic and needs no language support,
// so it is not listed in `views`.
trait Language {

  // Identity: the external reference typed documents use to name this
  // language. documentUrl names the (future) definition document and
  // nodeId picks the type within it.
  def typeRef: ExternalNodeReference

  def name: String

  // Derived views the language supports (e.g. "print", "eval").
  def views: List[String]

  // Render a derived view of the typed document's content as fragments:
  // Left for genuine failures (no content, wrong type), Right otherwise —
  // a hole-blocked result is a Hole fragment, not an error.
  def render(view: String, doc: Document[NodeData]): Either[String, List[Frag]]

  // Text syntax, if the language has one: parse cell text into a content
  // subtree. Recovering parsers return Some even for malformed input,
  // embedding holes (GapData nodes) where the text does not fit.
  def parse(text: String): Option[DetachedNode[NodeData]] = None
}

// A piece of a rendered derived view: a run of text, or a hole — a typed
// gap the view is waiting on (verbatim source text, empty when nothing
// has been typed there yet). The ui styles holes inline so incomplete
// input reads as "waiting", not "error".
enum Frag {
  case Text(text: String)
  case Hole(text: String)
}

object Frag {
  // Flat text for surfaces with no styling: a hole prints its verbatim
  // text (or '?' when empty), so flattened output reparses as holes.
  def flatten(frags: List[Frag]): String =
    frags.map {
      case Text(t) => t
      case Hole(t) => if (t.isEmpty) "?" else t
    }.mkString
}

// Registry mapping document-type references to languages defined in code.
object Languages {

  val all: List[Language] = List(ExprLanguage)

  def forRef(ref: ExternalNodeReference): Option[Language] =
    all.find(l =>
      l.typeRef.documentUrl == ref.documentUrl && l.typeRef.nodeId == ref.nodeId)

  // The language of a typed document, if the type is known.
  def forDoc(doc: Document[NodeData]): Option[Language] =
    Typed.typeRefOf(doc).flatMap(forRef)
}
