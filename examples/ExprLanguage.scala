package opelan.examples

import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.document.typed._
import opelan.foundation.document.Detached._
import opelan.foundation.langdef._

// The arithmetic-expression language — `exprDef` interpreted live. The
// object adds only what a definition can't express: the sample
// document. Parse, incremental text syntax, views, and input sessions
// all come from DefLanguage over the def's means rules; the object is
// the code registry's fallback for a store that holds no lang: doc.
object ExprLanguage extends DefLanguage(
    exprDef, exprImpls,
    ref => languageForRef(ref, Store.empty),
    dd => Some(languageFromDef(dd))) {

  // A typed sample document: (1 + 2) - (3 - 4)  ==  4
  def sampleDoc: Document[NodeData] =
    makeTyped(
      typeRef,
      er(docTitleRef),
      er(docImportsRef),
      NodeData.StringData("expr sample"),
      exprToDetached(
        Expr.Sub(
          Expr.Add(Expr.Lit(1), Expr.Lit(2)),
          Expr.Sub(Expr.Lit(3), Expr.Lit(4)))))
}
