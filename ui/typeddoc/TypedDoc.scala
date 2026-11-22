package opelan.ui.typeddoc

import opelan.examples.{exprSampleDoc, languageForDoc}
import opelan.examples.expr.{exprDoc, exprTypeRef}
import opelan.foundation.document.store._
import opelan.foundation.langdef.{DocLanguage, placeholderDoc, placeholderUrl}
import opelan.foundation.language.docText
import opelan.ui.editor.{DocumentEditor, EditorInput}
import opelan.ui.fp._

// Typed-document pane: the workbench's view onto a typed document.
//
// The structural editor is a DocumentEditor child that stays mounted in
// every surface mode, so switching between editor and text preserves
// selection, edit state, and undo history. The pane mirrors the child's
// document (via DocChanged outputs) so the sidebar's derived views
// always render the current content.
//
//   TypedDocModel.scala   state value + view/input/output types
//   TypedDocUpdate.scala  pure transitions
//   TypedDocView.scala    toolbar, panes, status bar
object TypedDoc extends Component[TypedDocInput, TypedDocOutput] {

  type State = TypedDocModel

  def init: State = {
    // The placeholder state before the workbench pushes its real store:
    // seed everything the sample's type ref needs — the expression def
    // doc, the meta def it is typed by, and the placeholder registry its
    // constructs resolve through — so resolution never leans on the
    // DocLanguage fallback for this doc's own dependencies.
    val store = List(
        exprTypeRef.documentUrl -> exprDoc,
        DocLanguage.typeRef.documentUrl -> DocLanguage.langDoc,
        placeholderUrl -> placeholderDoc)
      .foldLeft(Store.empty)((s, e) => s.put(e._1, e._2)._1)
    val d = exprSampleDoc
    TypedDocModel(
      doc = d,
      pushedDoc = d,
      text = docText(d, d2 => languageForDoc(d2, store)),
      status = "Expression sample loaded",
      store = store)
  }

  def update(state: State, input: TypedDocInput): Update[State, TypedDocOutput, TypedDocInput] =
    typedDocUpdate(state, input)

  def view(state: State): View[TypedDocInput] = typedDocView(state)

  override def children(state: State): Vector[Child[?, ?, TypedDocInput]] =
    Vector(Child(
      key = "editor",
      component = DocumentEditor,
      input = EditorInput.SyncDocument(
        state.pushedDoc, state.pushedSelect, state.pushMode, state.store),
      onOutput = TypedDocInput.FromEditor(_)))
}
