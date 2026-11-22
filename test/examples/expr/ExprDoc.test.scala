package opelan.examples.expr

import opelan.foundation.langdef.DocLanguage
import opelan.foundation.language.docText

// The seed document's text is what the editor surface renders — the
// parse at load must produce a document that projects back to it
// exactly.
class ExprDocSuite extends munit.FunSuite {

  test("the seed text parses to a doc that renders it back") {
    assertEquals(
      docText(exprDoc, _ => Some(DocLanguage)),
      exprDocText.stripLineEnd)
  }
}
