package opelan.ui.workbench

import opelan.ui.fp._

// The application shell as a component: every decision — open, save,
// follow-ref, sync, branch — is a pure transition over WorkbenchModel.
// Effects (storage, prompts, sessions, pushing inputs to the panes)
// leave as WorkbenchOutputs for WorkbenchShell to run; their results
// return as WorkbenchInputs.
//
//   WorkbenchModel.scala   state value + input/output types
//   WorkbenchUpdate.scala  pure transitions
//   WorkbenchView.scala    sidebar, toolbar, pane slots, status bar
//   WorkbenchShell.scala   effect interpreter + entry point
//   Workspace.scala        the document-store decisions, pure
object Workbench extends Component[WorkbenchInput, WorkbenchOutput] {
  type State = WorkbenchModel

  def init: State = WorkbenchModel()

  def update(
      state: State, input: WorkbenchInput): Update[State, WorkbenchOutput] =
    workbenchUpdate(state, input)

  def view(state: State): View[WorkbenchInput] = workbenchView(state)
}
