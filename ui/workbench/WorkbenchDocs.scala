package opelan.ui.workbench

import opelan.foundation.document._
import opelan.ui.fx._
import opelan.ui.typeddoc.TypedDocInput
import opelan.ui.workbench.WorkbenchUpdate._

// Document lifecycle helpers for the workbench update: opening, creating,
// saving, joining a branch. All pure — persistence is declared by
// appending a request to storageQ; pushes by setting panePush.
private[workbench] def open(
    m: WorkbenchModel, url: String, v: Int, d: Document[NodeData],
    sel: Option[Int] = None, status: Option[String] = None): Upd =
  // Opening a doc while sync is on attaches its session too — the
  // pane stays in lockstep with whatever doc is open.
  up(push(m.copy(
    workspace = m.workspace.open(url, v, d),
    synced = if (m.syncOn) m.synced + (url -> render(d)) else m.synced,
    status = status.getOrElse(m.status)),
    TypedDocInput.Load(d, sel)))

private[workbench] def createDoc(m: WorkbenchModel, name: String): Upd = {
  val url = m.workspace.freshUrl(name)
  val doc: Document[NodeData] = beginDocument(NodeData.StringData(name))
  val (ws, written) = m.workspace.save(url, doc)
  val (m2, pid) = nextReq(m.copy(workspace = ws))
  written match {
    case Some(v) =>
      open(m2.copy(
        storageQ = m2.storageQ :+ StorageReq.Persist(pid, url, v, doc)),
        url, v, doc, status = Some(s"Created $url@v$v"))
    case None => up(m2)
  }
}

private[workbench] def saveDocument(
    m: WorkbenchModel, d: Document[NodeData]): Upd =
  m.workspace.openUrl match {
    case Some(url) => doSave(m, url, d)
    case None =>
      val (m2, id) = nextReq(m)
      up(m2.copy(
        prompt = Some(PromptReq.Ask(id, "Save document as:", "untitled")),
        promptFor = Some(PromptFor.SaveAs)))
  }

private[workbench] def doSave(
    m: WorkbenchModel, url: String, d: Document[NodeData]): Upd = {
  val (ws, written) = m.workspace.save(url, d)
  written match {
    case Some(v) =>
      val (m2, id) = nextReq(m.copy(workspace = ws))
      up(m2.copy(
        storageQ = m2.storageQ :+ StorageReq.Persist(id, url, v, d)))
    case None =>
      up(m.copy(workspace = ws, status = s"No changes to save — $url"))
  }
}

private[workbench] def joinBranch(m: WorkbenchModel, url: String): Upd = {
  val placeholder: Document[NodeData] =
    beginDocument(NodeData.StringData("branch"))
  up(push(m.copy(
    workspace = m.workspace.open(url, 0, placeholder),
    synced = if (m.syncOn) m.synced + (url -> "") else m.synced,
    status = s"Joined branch $url"),
    TypedDocInput.Load(placeholder)))
}
