package opelan.ui.typeddoc

import opelan.foundation.language.{ExprLanguage, docText}
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
    val d = ExprLanguage.sampleDoc
    TypedDocModel(
      doc = d,
      pushedDoc = d,
      text = docText(d),
      status = "Expression sample loaded")
  }

  def update(state: State, input: TypedDocInput): Update[State, TypedDocOutput, TypedDocInput] =
    typedDocUpdate(state, input)

  def view(state: State): View[TypedDocInput] = typedDocView(state)

  override def children(state: State): Vector[Child[?, ?, TypedDocInput]] =
    Vector(Child(
      key = "editor",
      component = DocumentEditor,
      input = EditorInput.SyncDocument(
        state.pushedDoc, state.pushedSelect, state.pushMode),
      onOutput = TypedDocInput.FromEditor(_)))
}
