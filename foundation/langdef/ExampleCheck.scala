package opelan.foundation.langdef

import opelan.foundation.document._
import opelan.foundation.means.printWith

// A worked example's check: `input means expected` — both texts read
// with the definition's own rules must produce the same tree. The
// marker text tells what the input actually read as (the derived
// printer); an example that can't be checked — no live language — is
// left unmarked rather than marked wrong.
private[langdef] def exampleChecker(
    d: LanguageDef, ctx: ViewContext): (String, String) => Option[String] =
  ctx.interpretDef(d).fold[(String, String) => Option[String]]((_, _) => None) {
    lang => (input, expected) =>
      (lang.parse(input), lang.parse(expected)) match {
        case (Some(a), Some(e)) if a == e => None
        case (Some(a), Some(_)) => Some(s"reads as ${actualText(d, a)}")
        case _ => None
      }
  }

// What a tree reads as: the derived printer over the def's own rules,
// falling back to the data's outline form when no rules exist.
private def actualText(
    d: LanguageDef, t: DetachedNode[NodeData]): String =
  d.syntax.flatMap(_.rules) match {
    case Some(set) =>
      val krefs = kindRefs(d)
      printWith(set,
        k => krefs.get(k).map(NodeData.ExternalNodeRef(_))
          .getOrElse(NodeData.GapData(k)),
        NodeDataText.show)(t)
    case None => NodeDataText.show(t.data)
  }
