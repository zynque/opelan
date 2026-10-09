package opelan.ui.workbench

import opelan.collaboration.automerge.ChangeInfo
import opelan.foundation.document._
import opelan.ui.typeddoc.{TypedDocInput, TypedDocOutput}

// Which main-area pane the toolbar is showing. Both stay mounted —
// hiding rather than unmounting keeps each pane's DOM (editor state,
// focus) intact across switches.
enum ShellView {
  case Document, Components
}

// The workbench's complete decision state — a pure value. `workspace`
// tracks the document store and what the pane is showing; `pendingSave`
// marks a save waiting for the pane to report its live document (the
// DocChanged reply checkpoints the viewed version); `syncOn` is the
// Sync toggle; `syncedTexts` remembers the last text each session pushed
// so "remote edit" status fires only when the text actually moved.
case class WorkbenchModel(
    workspace: Workspace = Workspace.empty,
    view: ShellView = ShellView.Document,
    status: String = "Ready",
    syncOn: Boolean = false,
    pendingSave: Boolean = false,
    syncedTexts: Map[String, String] = Map.empty)

// Everything that can happen to the workbench — toolbar/sidebar events,
// outputs routed up from the document pane, and the shell reporting
// effect results (storage loaded, session grew, prompt answered) back
// as inputs.
enum WorkbenchInput {
  // Sent once after mount to kick off storage hydration.
  case Boot
  case SwitchShellView(view: ShellView)
  case NewDocument
  case SelectDocument(url: String)
  case SaveRequested
  case ToggleSync
  // Prompt replies: the name for a new document / for a save-as.
  case DocumentNamed(name: String)
  case SaveAsNamed(name: String)
  // Outputs coming up from the TypedDoc pane.
  case FromPane(out: TypedDocOutput)
  case StorageLoaded(store: Store)
  case StorageFailed(message: String)
  // A session finished attaching (persisted bytes loaded, subscribed).
  case SessionAttached(url: String, name: String)
  // A session's change graph grew: the current text, the full graph, and
  // the session's own actor id (to tell remote changes from echoes).
  case SessionUpdated(
    url: String, text: String,
    entries: Vector[ChangeInfo], heads: Vector[String],
    actor: String)
  // A RequestBranch output answered with a fresh branch URL.
  case OpenBranch(base: String, branchUrl: String, doc: Document[NodeData])
  // A peer's branch announcement the user accepted (the shell confirmed).
  case JoinBranch(branchUrl: String)
  // Effect results that only change the status line.
  case StatusMsg(message: String)
}

// Effects for the shell to run — the only way update reaches the world.
enum WorkbenchOutput {
  case HydrateStorage
  case PersistDoc(url: String, version: Int, doc: Document[NodeData])
  // Ask the user for a document name; `save` picks the reply input
  // (SaveAsNamed vs DocumentNamed).
  case PromptName(save: Boolean)
  // Forward an input to the document pane (mounted by the shell).
  case PushPane(input: TypedDocInput)
  // Attach a live session for `url`, seeded with `seedText`.
  case AttachSync(url: String, seedText: String)
  case DetachSync
  // Feed a local edit (as outline text) into the open doc's session.
  case SessionText(url: String, text: String)
  case SessionUndo(url: String)
  case SessionRedo(url: String)
  // The user edited a checked-out version of a synced doc — the shell
  // names a branch URL and answers with OpenBranch.
  case RequestBranch(base: String, doc: Document[NodeData])
  case AnnounceBranch(base: String, branchUrl: String)
}
