package opelan.ui.workbench

import opelan.ui.fp._
import opelan.ui.fp.demo.{CounterList, CounterListInput}
import opelan.ui.fx._
import opelan.ui.typeddoc.{TypedDoc, TypedDocInput, TypedDocOutput}
import opelan.ui.workbench.WorkbenchInput._

// The application as a component: every decision — open, save, follow,
// sync, branch — is a pure transition over WorkbenchModel, and every
// effect is a declared child. Four invisible seam components perform the
// impure work (storage, prompts, randomness, live sync); two visual panes
// occupy the named mounts. Requests flow down as props — child input is
// desired state, so the seams diff it like DOM; results come back as the
// From* inputs.
//
//   WorkbenchModel.scala    state value + input types
//   WorkbenchUpdate.scala   transitions (dispatch)
//   WorkbenchEvents.scala   child-output handlers
//   WorkbenchDocs.scala     open/create/save/join helpers
//   WorkbenchView.scala     sidebar, toolbar, pane slots, status bar
//   WorkbenchShell.scala    the browser edge — mount only
//   Workspace.scala         the document-store decisions, pure
// `docPane` is the typed-document child — injectable so tests can mount
// the workbench with a pane that doesn't need a real editor surface.
class Workbench(
    docPane: Component[TypedDocInput, TypedDocOutput] = TypedDoc)
    extends Component[WorkbenchInput, Nothing] {
  type State = WorkbenchModel

  def init: State = WorkbenchModel()

  def update(state: State, input: WorkbenchInput)
      : Update[State, Nothing, WorkbenchInput] =
    WorkbenchUpdate.workbenchUpdate(state, input)

  def view(state: State): View[WorkbenchInput] = workbenchView(state)

  override def children(
      s: State): Vector[Child[?, ?, WorkbenchInput]] =
    Vector(
      Child("storage", StorageHub, s.storageQ.headOption, FromStorage(_)),
      Child("prompt", Prompt, s.prompt, FromPrompt(_)),
      Child("random", Random, s.randomReq, FromRandom(_)),
      Child("sync", SyncHub,
        SyncMsg.Props(SyncProps(s.synced, s.syncCmd)), FromSync(_)),
      Child("doc", docPane,
        TypedDocInput.Pane(s.panePush, s.pushSeq, s.pullSerial),
        FromPane(_), slot = "doc"),
      Child("demo", CounterList, CounterListInput.Noop,
        o => FromDemo(o.toString), slot = "demo"))
}

// The production workbench: the real TypedDoc pane.
object Workbench extends Workbench(TypedDoc)
