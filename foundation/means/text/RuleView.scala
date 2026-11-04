package opelan.foundation.means.text

import opelan.foundation.document._
import opelan.foundation.means._
import opelan.foundation.view.{Verbosity, ViewTree}
import opelan.foundation.view.ViewTree._

// The view surface of `ruleSpans`' roles (foundation/view): each styled
// role becomes a `styled:role` element for the single interpreter to paint
// — `means` and `where` emphasized, placeholders italic the way math
// italicizes variables — while spaces and punctuation fuse into the
// surrounding text run, so a line stays a handful of elements rather
// than one per token. The same spans feed `printRule`'s flat text, so
// print and view cannot drift apart.
def ruleView(rule: Rule, verbosity: Verbosity = Verbosity.Verbose)
    : DetachedNode[NodeData] =
  flow(spansView(ruleSpans(rule, verbosity))*)

// A rule field's display children — what an `input` shows while it
// sits unedited. A phrase-spelled meaning keeps the flat text its
// language-aware projection supplies; the caller chooses.
def patternView(pattern: Pattern): List[DetachedNode[NodeData]] =
  spansView(pieceSpans(pattern.pieces))

def meaningView(meaning: Meaning): List[DetachedNode[NodeData]] =
  spansView(meaningSpans(meaning))

def whereView(constraints: List[Constraint]): List[DetachedNode[NodeData]] =
  spansView(whereSpans(constraints))

def spansView(spans: List[Styled]): List[DetachedNode[NodeData]] = {
  val (children, rest) = spans.foldLeft(
      (Vector.empty[DetachedNode[NodeData]], "")) {
    case ((acc, run), Styled(text, role)) =>
      roleName(role) match {
        case Some(name) =>
          (acc ++ (if (run.isEmpty) Nil else List(txt(run))) :+
            styled(name, txt(text)), "")
        case None => (acc, run + text)
      }
  }
  (children ++ (if (rest.isEmpty) Nil else List(txt(rest)))).toList
}

// The `styled:` roles a surface paints; Space and Punctuation stay text.
private def roleName(role: Role): Option[String] = role match {
  case Role.Keyword     => Some("keyword")
  case Role.Placeholder => Some("placeholder")
  case Role.Literal     => Some("literal")
  case Role.Tag         => Some("tag")
  case Role.Term        => Some("term")
  case Role.Marker      => Some("marker")
  case _                => None
}
