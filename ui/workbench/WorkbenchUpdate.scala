package opelan.ui.workbench

import opelan.foundation.document._
import opelan.ui.fx._
import opelan.ui.fp.Update
import opelan.ui.typeddoc.TypedDocInput
import opelan.ui.workbench.WorkbenchInput._

// Pure workbench transitions. Nothing here performs an effect — state
// fields carry requests that Workbench.children turns into seam-child
// props, and seam outputs arrive back through the From* inputs.
object WorkbenchUpdate {

  type Upd = Update[WorkbenchModel, Nothing, WorkbenchInput]

  def up(m: WorkbenchModel): Upd = Update(m)

  def nextReq(m: WorkbenchModel): (WorkbenchModel, Int) =
    (m.copy(nextSerial = m.nextSerial + 1), m.nextSerial)

  // Push a doc into the pane: bump the serial so equal pushes redeliver
  // (toggling sync off reloads the same doc to reset history).
  def push(m: WorkbenchModel, in: TypedDocInput): WorkbenchModel =
    m.copy(panePush = Some(in), pushSeq = m.pushSeq + 1)

  def openEntry(m: WorkbenchModel): Map[String, String] =
    (for { u <- m.workspace.openUrl; d <- m.workspace.doc }
     yield Map(u -> render(d))).getOrElse(Map.empty)

  def workbenchUpdate(m: WorkbenchModel, in: WorkbenchInput): Upd =
    in match {
      case Boot =>
        val (m2, id) = nextReq(m)
        up(m2.copy(storageQ = m2.storageQ :+ StorageReq.Hydrate(id)))

      case SwitchShellView(v) =>
        up(m.copy(view = v, status = s"Switched to ${viewName(v)} view"))

      case NewDocument =>
        val (m2, id) = nextReq(m)
        up(m2.copy(
          prompt = Some(PromptReq.Ask(id, "Document name:", "untitled")),
          promptFor = Some(PromptFor.NewDoc)))

      case SelectDocument(url) =>
        m.workspace.store.head(url) match {
          case Some((v, d)) => open(m, url, v, d)
          case None => up(m.copy(status = s"No versions of $url"))
        }

      case SaveRequested =>
        if (m.pendingSave) up(m)
        else up(m.copy(pendingSave = true, pullSerial = m.pullSerial + 1))

      case ToggleSync =>
        if (!m.syncOn)
          // Declaring the open doc's url in `synced` is the attach —
          // SyncHub creates the session when the prop appears.
          up(m.copy(
            syncOn = true,
            synced = openEntry(m),
            status = "Live sync on — tabs share edits over BroadcastChannel"))
        else
          // Dropping every entry unmounts the sessions (each close()
          // happens in the child's lifecycle). Pushing the same doc
          // again resets the pane's shared-history view via bump.
          up(m.copy(
            syncOn = false,
            synced = Map.empty,
            panePush = m.workspace.doc.map(TypedDocInput.Load(_)),
            pushSeq = m.pushSeq + 1,
            status = "Live sync off"))

      case FromPane(out)   => fromPane(m, out)
      case FromStorage(ev) => fromStorage(m, ev)
      case FromPrompt(ev)  => fromPrompt(m, ev)
      case FromRandom(ev)  => fromRandom(m, ev)
      case FromSync(ev)    => fromSync(m, ev)

      case FromDemo(msg) => up(m.copy(status = msg))
    }

  def viewName(v: ShellView): String =
    v match {
      case ShellView.Document => "documents"
      case ShellView.Components => "components"
    }
}
