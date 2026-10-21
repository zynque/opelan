package opelan.ui.typeddoc

import opelan.examples.{Expr, ExprLanguage, SheetLanguage,
  exprFromDocument, sheetDef}
import opelan.foundation.document._
import opelan.foundation.document.Detached._
import opelan.foundation.langdef.langDefDoc
import opelan.ui.editor.{
  DocPush, EditorInput, EditorOutput, SelectTarget, SyncedEntry, SyncedHistory}

class TypedDocSuite extends munit.FunSuite {

  private def init: TypedDocModel = TypedDoc.init

  private def editorInput(m: TypedDocModel): EditorInput =
    TypedDoc.children(m).head.input.asInstanceOf[EditorInput]

  test("init loads a typed expression sample") {
    val m = init
    assertEquals(typeRefOf(m.doc), Some(ExprLanguage.typeRef))
    assertEquals(m.view, DocView.Editor)
  }

  test("children mounts the document editor with the pushed document") {
    val m = init
    assertEquals(editorInput(m), EditorInput.SyncDocument(m.pushedDoc))
  }

  test("SwitchView selects the text surface") {
    val u = typedDocUpdate(init, TypedDocInput.SwitchView(DocView.Text))
    assertEquals(u.state.view, DocView.Text)
  }

  test("editor edits update the mirrored doc but are not pushed back") {
    val m = init
    val edited: Document[NodeData] = beginDocument(NodeData.IntData(1))
    val u = typedDocUpdate(m, TypedDocInput.FromEditor(EditorOutput.DocChanged(edited)))
    assertEquals(u.state.doc, edited)
    assertEquals(u.state.pushedDoc, m.pushedDoc)
    assertEquals(editorInput(u.state), EditorInput.SyncDocument(m.pushedDoc))
    assert(u.out.contains(TypedDocOutput.DocChanged(edited)))
  }

  test("Load falls back to the new language's default view") {
    // On a derived view, jump to a doc whose language lacks it — the
    // go-to-definition path: "sheet" doesn't exist on DocLanguage, so
    // the pane lands on its default "def" view instead of an error.
    val m = typedDocUpdate(
      init, TypedDocInput.SwitchView(DocView.Derived("sheet"))).state
    val u = typedDocUpdate(m, TypedDocInput.Load(langDefDoc(sheetDef)))
    assertEquals(u.state.view, DocView.Derived("def"))
  }

  test("Load onto an untyped doc leaves a derived view for the editor") {
    val m = typedDocUpdate(
      init, TypedDocInput.SwitchView(DocView.Derived("print"))).state
    val u = typedDocUpdate(
      m, TypedDocInput.Load(beginDocument(NodeData.StringData("raw"))))
    assertEquals(u.state.view, DocView.Editor)
  }

  test("Load keeps a derived view the new language also offers") {
    val m = typedDocUpdate(
      init, TypedDocInput.SwitchView(DocView.Derived("sheet"))).state
    val u = typedDocUpdate(m, TypedDocInput.Load(SheetLanguage.sampleDoc))
    assertEquals(u.state.view, DocView.Derived("sheet"))
  }

  test("Load pushes the new document into the editor") {
    val m = init
    val d: Document[NodeData] = beginDocument(NodeData.StringData("other"))
    val u = typedDocUpdate(m, TypedDocInput.Load(d))
    assertEquals(u.state.doc, d)
    assertEquals(u.state.pushedDoc, d)
    assertEquals(editorInput(u.state), EditorInput.SyncDocument(d))
  }

  test("TextEdited parses into the content subtree and pushes the doc") {
    val m = init
    val u = typedDocUpdate(m, TypedDocInput.TextEdited("1 + 2"))
    assertEquals(u.state.text, "1 + 2")
    assertEquals(u.state.textEpoch, m.textEpoch)
    val content = contentId(u.state.doc).get
    assertEquals(exprFromDocument(u.state.doc, content), Right(Expr.Add(Expr.Lit(1), Expr.Lit(2))))
  }

  test("TextEdited reparses through a carried text session") {
    val u1 = typedDocUpdate(init, TypedDocInput.TextEdited("1 + 2"))
    assert(u1.state.textSession.isDefined)
    val u2 = typedDocUpdate(u1.state, TypedDocInput.TextEdited("1 + 2 * 3"))
    assertEquals(u2.state.textSession.map(_.session.text), Some("1 + 2 * 3"))
    // the same document a whole-text parse commits
    assertEquals(Right(u2.state.doc), opelan.foundation.language.applyDocText(
      u1.state.doc, opelan.examples.registryForDoc, "1 + 2 * 3"))
  }

  test("TextEdited leaves holes where text does not fit") {
    val u = typedDocUpdate(init, TypedDocInput.TextEdited("1 + abc"))
    val content = contentId(u.state.doc).get
    assertEquals(
      exprFromDocument(u.state.doc, content),
      Right(Expr.Add(Expr.Lit(1), Expr.Hole("abc"))))
    assert(u.state.status.contains("hole"))
  }

  test("editor edits re-derive the text and bump the epoch") {
    val m = init
    val edited: Document[NodeData] = beginDocument(NodeData.IntData(1))
    val u = typedDocUpdate(m, TypedDocInput.FromEditor(EditorOutput.DocChanged(edited)))
    assertEquals(u.state.textEpoch, m.textEpoch + 1)
  }

  test("SyncHistory pushes the document and shared history into the editor") {
    val d: Document[NodeData] = beginDocument(NodeData.StringData("remote"))
    val h = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "amber-fox", d)), Vector("h1"))
    val u = typedDocUpdate(init, TypedDocInput.SyncHistory(d, h))
    assertEquals(u.state.doc, d)
    assertEquals(u.state.pushMode, DocPush.Synced(h))
    assertEquals(
      editorInput(u.state),
      EditorInput.SyncDocument(d, None, DocPush.Synced(h)))
    // no DocChanged echo — the push came from the session owner
    assert(u.out.forall(o => !o.isInstanceOf[TypedDocOutput.DocChanged]))
  }

  test("a text edit pushes the document as a merge") {
    val u = typedDocUpdate(init, TypedDocInput.TextEdited("1 + 2"))
    assertEquals(u.state.pushMode, DocPush.Merge("Text edit"))
  }

  test("Load after a synced push resets to a fresh document") {
    val d1: Document[NodeData] = beginDocument(NodeData.StringData("remote"))
    val h = SyncedHistory(
      Vector(SyncedEntry("h1", Vector(), "amber-fox", d1)), Vector("h1"))
    val m = typedDocUpdate(init, TypedDocInput.SyncHistory(d1, h)).state
    val d2: Document[NodeData] = beginDocument(NodeData.StringData("other"))
    val u = typedDocUpdate(m, TypedDocInput.Load(d2))
    assertEquals(u.state.pushMode, DocPush.Open)
    assertEquals(editorInput(u.state), EditorInput.SyncDocument(d2))
  }

  test("Load with a selection pushes it through to the editor") {
    val m = init
    val d: Document[NodeData] =
      buildDocument(n(s("r"), sl("a")))
    val target = d.childrenOf(d.rootId).head
    val u = typedDocUpdate(m, TypedDocInput.Load(d, Some(target)))
    assertEquals(u.state.pushedSelect, Some(SelectTarget(target)))
    assertEquals(
      editorInput(u.state),
      EditorInput.SyncDocument(d, Some(SelectTarget(target))))
  }

  private def cellId(doc: Document[NodeData], r: Int, c: Int): Int = {
    val sheet = contentId(doc).get
    doc.childrenOf(doc.childrenOf(sheet)(r))(c)
  }

  test("CommitInputText rewrites the node in place and pushes the doc") {
    val m = typedDocUpdate(init, TypedDocInput.LoadSheetSample).state
    val price = cellId(m.doc, 1, 2) // Apples' price
    val u = typedDocUpdate(m, TypedDocInput.CommitInputText(price, "1.50"))
    assertEquals(
      u.state.doc.getNode(price).map(_.data), Some(NodeData.FloatData(1.5)))
    assertEquals(u.state.pushedDoc, u.state.doc)
    assert(u.out.contains(TypedDocOutput.DocChanged(u.state.doc)))
  }

  test("an input commit keeps the ids of unchanged subtrees") {
    val m = typedDocUpdate(init, TypedDocInput.LoadSheetSample).state
    val total = cellId(m.doc, 1, 3) // Apples' line total = qty * price
    val refs = m.doc.childrenOf(total)
    val Seq(NodeData.InternalNodeRef(qty), NodeData.InternalNodeRef(price)) =
      refs.map(id => m.doc.getNode(id).get.data)
    val u = typedDocUpdate(
      m, TypedDocInput.CommitInputText(total, s"ref:$qty + ref:$price"))
    // mul → add changed the node's data, but the ref nodes kept their
    // ids and the cell itself was never replaced.
    assertEquals(u.state.doc.childrenOf(total), refs)
    assertEquals(
      exprFromDocument(u.state.doc, total),
      Right(Expr.Add(Expr.Ref(qty), Expr.Ref(price))))
    // A fresh part gets a fresh id; the rest stay put.
    val u2 = typedDocUpdate(
      m, TypedDocInput.CommitInputText(total, s"ref:$qty * 3"))
    val after = u2.state.doc.childrenOf(total)
    assertEquals(after(0), refs(0))
    assertNotEquals(after(1), refs(1))
  }

  test("re-committing an input reparses through its session") {
    // Seeded def docs make the sheet a DefLanguage, whose input grammar
    // reads through lang: expression's incremental syntax.
    val seeded = List(ExprLanguage, SheetLanguage).foldLeft(Store.empty)(
      (s, l) => s.put(l.typeRef.documentUrl, l.langDoc)._1)
    val m = typedDocUpdate(
      init.copy(store = seeded), TypedDocInput.LoadSheetSample).state
    val total = cellId(m.doc, 1, 3)
    val Seq(NodeData.InternalNodeRef(qty), _) =
      m.doc.childrenOf(total).map(id => m.doc.getNode(id).get.data)
    // Parenthesized terms: each is its own memoized rule application.
    val long = (1 to 10).map(i => s"(ref:$qty * $i)").mkString(" + ")
    val u1 = typedDocUpdate(m, TypedDocInput.CommitInputText(total, long))
    assertEquals(u1.state.inputSessions.sessions.keySet, Set(total))
    val edited = long.replace("* 5", "* 6")
    val u2 = typedDocUpdate(u1.state, TypedDocInput.CommitInputText(total, edited))
    val session = u2.state.inputSessions.sessions(total)
    val fresh = opelan.foundation.language.TextSession.start(
      session.syntax, edited)
    assert(session.evaluations * 4 < fresh.evaluations,
      s"${session.evaluations} vs ${fresh.evaluations}")
    assertEquals(
      exprFromDocument(u2.state.doc, total).map(_.toString),
      exprFromDocument(
        typedDocUpdate(m, TypedDocInput.CommitInputText(total, edited))
          .state.doc, total).map(_.toString))
  }

  test("a text edit keeps the ids of unchanged content") {
    val m = init
    // TextEdited goes through applyDocText: recommitting the current
    // text keeps the content node's id (and the whole subtree's).
    val cid = contentId(m.doc).get
    val u = typedDocUpdate(m, TypedDocInput.TextEdited(m.text))
    assertEquals(u.state.doc.childrenOf(u.state.doc.rootId), m.doc.childrenOf(m.doc.rootId))
    assertEquals(u.state.doc.childrenOf(cid), m.doc.childrenOf(cid))
  }

  test("an unchanged CommitInputText is a no-op") {
    val m = typedDocUpdate(init, TypedDocInput.LoadSheetSample).state
    val price = cellId(m.doc, 1, 2)
    val u = typedDocUpdate(m, TypedDocInput.CommitInputText(price, "1.2"))
    assertEquals(u.state, m)
    assert(u.out.isEmpty)
  }

  test("CommitInputText on a doc with no input grammar reports it") {
    val u = typedDocUpdate(init, TypedDocInput.CommitInputText(0, "x"))
    assert(u.state.status.contains("input grammar"))
    assert(u.out.exists(_.isInstanceOf[TypedDocOutput.Status]))
  }

  test("SelectSource pushes a focus-less selection into the editor") {
    val m = init
    val target = contentId(m.doc).get
    val u = typedDocUpdate(m, TypedDocInput.SelectSource(target))
    assertEquals(
      u.state.pushedSelect, Some(SelectTarget(target, focus = false)))
    assertEquals(
      editorInput(u.state),
      EditorInput.SyncDocument(
        m.doc, Some(SelectTarget(target, focus = false)),
        DocPush.Merge("Select")))
    assertEquals(u.state.view, DocView.Editor)
  }

  test("SelectSource on a missing node changes nothing") {
    val u = typedDocUpdate(init, TypedDocInput.SelectSource(999))
    assertEquals(u.state.pushedSelect, None)
  }

  test("SelectSource inside a derived view stays on the app surface") {
    val m = typedDocUpdate(
      init, TypedDocInput.SwitchView(DocView.Derived("print"))).state
    val target = contentId(m.doc).get
    val u = typedDocUpdate(m, TypedDocInput.SelectSource(target))
    assertEquals(u.state.view, DocView.Derived("print"))
    assertEquals(
      u.state.pushedSelect, Some(SelectTarget(target, focus = false)))
  }

  test("GoToDefinition follows the document's type ref") {
    val u = typedDocUpdate(init, TypedDocInput.GoToDefinition)
    assert(u.out.contains(TypedDocOutput.FollowRef(ExprLanguage.typeRef)))
  }

  test("GoToDefinition on an untyped document reports it") {
    val raw =
      typedDocUpdate(init, TypedDocInput.Load(buildDocument(n(s("r"))))).state
    val u = typedDocUpdate(raw, TypedDocInput.GoToDefinition)
    assert(u.out.forall(o => !o.isInstanceOf[TypedDocOutput.FollowRef]))
    assertEquals(u.state.status, "Untyped document")
  }

  test("RequestDoc re-emits the current document") {
    val m = typedDocUpdate(init, TypedDocInput.TextEdited("7")).state
    val u = typedDocUpdate(m, TypedDocInput.RequestDoc)
    assertEquals(u.out, Vector(TypedDocOutput.DocChanged(m.doc)))
    assertEquals(u.state, m)
  }

  test("a Pane push applies per serial; a pull re-emits the live doc") {
    val d: Document[NodeData] = beginDocument(NodeData.StringData("remote"))
    val p = TypedDocInput.Pane(Some(TypedDocInput.Load(d)), 1, 0)
    val u1 = typedDocUpdate(init, p)
    assertEquals(u1.state.doc, d)
    // The owner edited locally — the same props re-delivered do not reload.
    val other: Document[NodeData] = beginDocument(NodeData.IntData(9))
    val edited = typedDocUpdate(
      u1.state, TypedDocInput.FromEditor(EditorOutput.DocChanged(other))).state
    val u2 = typedDocUpdate(edited, p)
    assertEquals(u2.state.doc, other)
    // A bumped push serial re-applies even the identical push.
    val u3 = typedDocUpdate(
      u2.state, TypedDocInput.Pane(Some(TypedDocInput.Load(d)), 2, 0))
    assertEquals(u3.state.doc, d)
    // A bumped pull serial re-emits DocChanged.
    val u4 = typedDocUpdate(
      u3.state, TypedDocInput.Pane(Some(TypedDocInput.Load(d)), 2, 1))
    assertEquals(u4.out, Vector(TypedDocOutput.DocChanged(d)))
  }

  test("editor FollowRef outputs are passed through") {
    val ref = ExternalNodeReference("opelan:docs/x", 0, 1)
    val u = typedDocUpdate(init, TypedDocInput.FromEditor(EditorOutput.FollowRef(ref)))
    assertEquals(u.out, Vector(TypedDocOutput.FollowRef(ref)))
  }

  test("an echo of the pushed doc does not re-derive the text") {
    val m = typedDocUpdate(init, TypedDocInput.TextEdited("5")).state
    val u = typedDocUpdate(m, TypedDocInput.FromEditor(EditorOutput.DocChanged(m.doc)))
    assertEquals(u.state.textEpoch, m.textEpoch)
    assertEquals(u.state.text, "5")
  }

  // A store holding two versions of the pane's language: the doc's
  // pins all target v0 while v1 is head.
  private def bumpedStore: Store =
    Store.empty
      .put(ExprLanguage.typeRef.documentUrl, ExprLanguage.langDoc)._1
      .put(ExprLanguage.typeRef.documentUrl,
        langDefDoc(opelan.examples.exprDef.copy(name = "v2")))._1

  test("AcceptUpdates bumps the doc's pinned refs to their heads") {
    val m = init.copy(store = bumpedStore)
    val u = typedDocUpdate(m, TypedDocInput.AcceptUpdates)
    assertEquals(
      typeRefOf(u.state.doc).map(_.documentVersionId), Some(1))
    assert(u.out.exists {
      case TypedDocOutput.DocChanged(d) =>
        typeRefOf(d).map(_.documentVersionId).contains(1)
      case _ => false
    })
    assertEquals(pendingUpdates(u.state.doc, u.state.store), Nil)
  }

  test("AcceptUpdates reports when nothing is pending") {
    val u = typedDocUpdate(init, TypedDocInput.AcceptUpdates)
    assertEquals(u.out, Vector(TypedDocOutput.Status("No pending updates")))
  }

  test("ToggleAutoUpdate writes and removes the flag") {
    val u1 = typedDocUpdate(init, TypedDocInput.ToggleAutoUpdate)
    assert(opelan.foundation.langdef.autoUpdateEnabled(u1.state.doc))
    val u2 = typedDocUpdate(u1.state, TypedDocInput.ToggleAutoUpdate)
    assert(!opelan.foundation.langdef.autoUpdateEnabled(u2.state.doc))
  }

  test("an auto-updating doc accepts pending pins without the click") {
    val flagged =
      opelan.foundation.langdef.setAutoUpdate(init.doc, true).toOption.get
    val m = init.copy(doc = flagged, store = bumpedStore)
    // Any input — a view switch suffices — triggers the recorded accept.
    val u = typedDocUpdate(m, TypedDocInput.SwitchView(DocView.Text))
    assertEquals(
      typeRefOf(u.state.doc).map(_.documentVersionId), Some(1))
  }
}
