package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.language.{HoleRetry, InputSessions, Language, TextSyntax}

// A def's input grammar: how text committed in a projected input becomes
// content. A builtin parser runs as named; a grammar that refers to
// another language reads the text with that language's parser — through
// the host's remembered hole attempts when rendering, through its
// per-input sessions when committing — and then classifies the result:
// explicit "?"-holes and whitespace-only inputs aside, real content
// stands, and a pure hole becomes the fallback (a label or a gap).
// Rejected text that mentions a reference is a formula the grammar
// can't read yet, so it stays a hole even under the label fallback,
// ready to heal.
case class InputGrammar(
    parser: Option[InputParserDef],
    impls: Impls,
    resolve: ExternalNodeReference => Option[Language]) {

  // The incremental syntax of the language the grammar refers to.
  def syntax: Option[TextSyntax] =
    parser.collect { case InputParserDef.Language(ref, _) => ref }
      .flatMap(resolve).flatMap(_.textSyntax)

  def parse(retry: HoleRetry)(text: String): Option[DetachedNode[NodeData]] =
    parser.map {
      case InputParserDef.Builtin(name) =>
        impls.inputParsers.get(name)
          .map(_(text))
          .getOrElse(DetachedNode.leaf(NodeData.GapData(text)))
      case InputParserDef.Language(ref, fb) =>
        shortcut(fb, text).getOrElse(classify(ref, fb, text,
          resolve(ref).flatMap(l => l.textSyntax
            .map(retry.parse(_, text)._1).orElse(l.parse(text)))))
    }

  // A commit: the same content as `parse`, read through the input's
  // session when the referred language parses incrementally.
  def commit(id: Int, text: String, inputs: InputSessions)
      : Option[(DetachedNode[NodeData], InputSessions)] =
    parser match {
      case Some(InputParserDef.Language(ref, fb))
          if shortcut(fb, text).isEmpty =>
        val language = resolve(ref)
        Some(language.flatMap(_.textSyntax) match {
          case Some(s) =>
            val (read, next) = inputs.parse(id, s, text)
            (classify(ref, fb, text, Some(read)), next)
          case None =>
            (classify(ref, fb, text, language.flatMap(_.parse(text))), inputs)
        })
      case _ => parse(HoleRetry())(text).map((_, inputs))
    }

  private def shortcut(
      fb: InputFallback, text: String): Option[DetachedNode[NodeData]] =
    if (text.startsWith("?"))
      Some(DetachedNode.leaf(NodeData.GapData(text.drop(1))))
    else if (text.trim.isEmpty) Some(DetachedNode.leaf(fallbackData(fb, "")))
    else None

  private def classify(
      ref: ExternalNodeReference, fb: InputFallback, text: String,
      read: Option[DetachedNode[NodeData]]): DetachedNode[NodeData] =
    read match {
      case Some(t) if isHoleLeaf(t) && mentionsRef(ref, text) =>
        DetachedNode.leaf(NodeData.GapData(text))
      case Some(t) if isHoleLeaf(t) =>
        DetachedNode.leaf(fallbackData(fb, text))
      case Some(t) => t
      case None    => DetachedNode.leaf(fallbackData(fb, text))
    }

  private def mentionsRef(ref: ExternalNodeReference, text: String): Boolean =
    resolve(ref).exists(l => text.split("\\s+").exists(w =>
      l.parse(w).exists(t =>
        t.children.isEmpty && t.data.isInstanceOf[NodeData.InternalNodeRef])))

  private def isHoleLeaf(t: DetachedNode[NodeData]): Boolean =
    t.children.isEmpty && t.data.isInstanceOf[NodeData.GapData]

  private def fallbackData(fb: InputFallback, text: String): NodeData =
    fb match {
      case InputFallback.Label => NodeData.StringData(text)
      case InputFallback.Hole  => NodeData.GapData(text)
    }
}
