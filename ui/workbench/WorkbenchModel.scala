package opelan.ui.workbench

import opelan.foundation.document._
import opelan.ui.fx._
import opelan.ui.typeddoc.{TypedDocInput, TypedDocOutput}

// Which main-area pane the toolbar is showing. Both stay mounted —
// hiding rather than unmounting keeps each pane's DOM (editor state,
// focus) intact across switches.
enum ShellView {
  case Document, Components
}

// What an outstanding prompt is for — set when prompt is raised, read
// when its reply arrives.
enum PromptFor {
  case NewDoc, SaveAs
  case Join(url: String)
}

// The workbench's complete state — a pure value. Every effect the shell
// once performed is now a prop of a seam child (see Workbench.children):
//   storageQ    document-store requests; head is the props to StorageHub
//   prompt      pending window prompt (purpose tagged by promptFor)
//   randomReq   pending generated value (branch-name suffix)
//   synced      desired sync sessions: url → latest outline text; an
//               entry's presence attaches, its value feeds localText
//   syncCmd     a serial'd one-shot session command (undo/redo/announce)
//   panePush    the document currently pushed into the pane + its serial
//   pullSerial  bumped to ask the pane to re-emit its live document
//   pendingSave a save waiting for that pull's DocChanged reply
//   pendingBranch (base url, edited doc) awaiting its generated suffix
//   syncedTexts last text each session pushed — remote-edit status
//               suppression (fires only when the text actually moved)
case class WorkbenchModel(
    workspace: Workspace = Workspace.empty,
    view: ShellView = ShellView.Document,
    status: String = "Ready",
    syncOn: Boolean = false,
    synced: Map[String, String] = Map.empty,
    syncedTexts: Map[String, String] = Map.empty,
    panePush: Option[TypedDocInput] = None,
    pushSeq: Int = 0,
    pullSerial: Int = 0,
    pendingSave: Boolean = false,
    pendingBranch: Option[(String, Document[NodeData])] = None,
    storageQ: Vector[StorageReq] = Vector.empty,
    prompt: Option[PromptReq] = None,
    promptFor: Option[PromptFor] = None,
    randomReq: Option[RandomReq] = None,
    syncCmd: Option[(Int, SyncCmd)] = None,
    nextSerial: Int = 0)

// Everything that can happen to the workbench: toolbar/sidebar events
// plus the seam and pane children's outputs routed back up as inputs.
enum WorkbenchInput {
  // Sent once by Runtime.mount — kicks off storage hydration.
  case Boot
  case SwitchShellView(view: ShellView)
  case NewDocument
  case SelectDocument(url: String)
  case SaveRequested
  case ToggleSync
  case FromPane(out: TypedDocOutput)
  case FromStorage(ev: StorageEv)
  case FromPrompt(ev: PromptEv)
  case FromRandom(ev: RandomEv)
  case FromSync(ev: SyncEv)
  case FromDemo(message: String)
}
