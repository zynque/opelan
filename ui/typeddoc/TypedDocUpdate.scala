package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.foundation.language.ExprLanguage
import opelan.ui.editor.EditorOutput
import opelan.ui.fp.Update
import TypedDocInput._
import TypedDocOutput._

// The pure transition function for the typed-document pane.
object TypedDocUpdate {

  def apply(m: TypedDocModel, input: TypedDocInput): Update[TypedDocModel, TypedDocOutput] =
    input match {
      case SwitchView(v) => Update(m.copy(view = v))
      case Load(d)       => load(m, d, "Document loaded")
      case LoadExprSample =>
        load(m, ExprLanguage.sampleDoc, "Expression sample loaded")

      case FromEditor(EditorOutput.DocChanged(d)) =>
        Update(m.copy(doc = d), Vector(DocChanged(d)))
      case FromEditor(EditorOutput.Status(s)) =>
        Update(m.copy(status = s), Vector(Status(s)))
    }

  private def load(
      m: TypedDocModel,
      d: Document[NodeData],
      msg: String): Update[TypedDocModel, TypedDocOutput] =
    Update(
      m.copy(doc = d, pushedDoc = d, status = msg),
      Vector(DocChanged(d), Status(msg)))
}
