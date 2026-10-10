package opelan.ui.fx

import org.scalajs.dom
import opelan.ui.fp._

// Seam component: window prompts/confirms. The parent raises a request
// as props (Some = pending, None = idle); the answer comes back as an
// output carrying the request's id. `dom.window.prompt` blocks
// synchronously, so no Cmd is needed — the reply emits during update.
enum PromptReq {
  case Ask(id: Int, title: String, default: String)
  case Confirm(id: Int, question: String)
}

enum PromptEv {
  case Answered(id: Int, text: Option[String])
  case Decided(id: Int, yes: Boolean)
}

object Prompt extends Component[Option[PromptReq], PromptEv] {
  type State = Set[Int] // answered request ids — re-delivery guard

  def init: State = Set.empty

  def update(s: State, in: Option[PromptReq]): Update[State, PromptEv, Option[PromptReq]] =
    in match {
      case Some(PromptReq.Ask(id, title, default)) if !s(id) =>
        val answer = Option(dom.window.prompt(title, default)).filter(_.nonEmpty)
        Update(s + id, Vector(PromptEv.Answered(id, answer)))
      case Some(PromptReq.Confirm(id, question)) if !s(id) =>
        Update(s + id, Vector(PromptEv.Decided(id, dom.window.confirm(question))))
      case _ => Update(s)
    }

  def view(s: State): View[Option[PromptReq]] = View.Text("")
}
