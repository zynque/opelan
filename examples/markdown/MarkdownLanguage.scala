package opelan.examples.markdown

import opelan.examples.{languageForRef, languageFromDef}
import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.typed._
import opelan.foundation.document.Detached._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc._

// The markdown language — `markdownDef` interpreted live. The object
// adds only the seeded sample document; parsing, printing, views, and
// the edit surface all come from DefLanguage over the def's impls.
object MarkdownLanguage extends DefLanguage(
    markdownDef, markdownImpls,
    ref => languageForRef(ref, Store.empty),
    dd => Some(languageFromDef(dd))) {

  // The demo's markdown document, exercising each supported construct:
  // both heading levels, a list, and inline emphasis. Its content is
  // the parser's output — what a text commit of this source lands.
  def sampleDoc: Document[NodeData] =
    makeTyped(typeRef,
      er(docTitleRef), er(docImportsRef),
      NodeData.StringData("Markdown notes"),
      markdownParse(
        """# Notes
          |
          |## Groceries
          |
          |* Apples
          |* Bread
          |* **Good** coffee
          |
          |Plain *italic* and **bold** runs.""".stripMargin))
}
