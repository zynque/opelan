package opelan.examples.expr

import opelan.examples.{languageForRef, languageFromDef}
import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.typed._
import opelan.foundation.document.Detached._
import opelan.foundation.langdef._
import opelan.foundation.langdef.doc._

// The arithmetic-expression language — the seeded `lang: expression`
// document, decoded and interpreted live. The object adds only what a
// definition can't express: the sample document. Parse, incremental
// text syntax, views, and input sessions all come from DefLanguage
// over the decoded def's means rules; the object is the code
// registry's fallback for a store that holds no lang: doc.
object ExprLanguage extends DefLanguage(
    decodedExprDef, exprImpls,
    ref => languageForRef(ref, Store.empty),
    dd => Some(languageFromDef(dd))) {

  // A typed sample document: (1 + 2) - (3 - 4)  ==  4
  def sampleDoc: Document[NodeData] =
    makeTyped(
      typeRef,
      er(docTitleRef),
      er(docImportsRef),
      NodeData.StringData("Expression sample"),
      parse("1 + 2 - (3 - 4)").getOrElse(
        throw new IllegalStateException("expression sample failed to parse")))
}
