package opelan.ui.workbench

import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.langdef.autoUpdateEnabled
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
    synced = if (m.syncOn) m.synced + (url -> serialize(d)) else m.synced,
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
      val (m3, bumps) = autoCascade(m2, url)
      up(m3.copy(
        storageQ =
          m3.storageQ ++ (StorageReq.Persist(id, url, v, d) +: bumps)))
    case None =>
      up(m.copy(workspace = ws, status = s"No changes to save — $url"))
  }
}

// Auto-update cascade after a save: every auto-update-opted document
// whose refs pin behind a head accepts them — each accept is a `put`, a
// recorded versioned edit that can pend the next layer, so passes
// repeat to a fixpoint (bounded by the URL count: a dep bumped late in
// a pass can repend a doc already passed). The open document is left to
// its pane's own auto-accept; synced docs are skipped — their sessions
// own their text. Returns the model with the cascade's store plus one
// quiet persist request per bumped version.
private[workbench] def autoCascade(
    m: WorkbenchModel,
    savedUrl: String): (WorkbenchModel, Vector[StorageReq]) = {
  val skip = m.synced.keySet ++ m.workspace.openUrl.toSet + savedUrl
  def pass(s: Store): (Store, List[(String, Int, Document[NodeData])]) =
    s.urls.filterNot(skip).foldLeft(
      (s, List.empty[(String, Int, Document[NodeData])])) {
        case ((st, bs), u) =>
          st.head(u) match {
            case Some((_, d)) if autoUpdateEnabled(d) =>
              acceptUpdates(d, st) match {
                case Some(d2) =>
                  val (st2, v) = st.put(u, d2)
                  (st2, bs :+ (u, v, d2))
                case None => (st, bs)
              }
            case _ => (st, bs)
          }
      }
  def fixpoint(
      s: Store, acc: Vector[(String, Int, Document[NodeData])], rounds: Int)
      : (Store, Vector[(String, Int, Document[NodeData])]) =
    pass(s) match {
      case (s2, Nil) => (s2, acc)
      case (s2, bs) =>
        if (rounds >= s.urls.size) (s2, acc ++ bs)
        else fixpoint(s2, acc ++ bs, rounds + 1)
    }
  val (s, bumped) = fixpoint(m.workspace.store, Vector.empty, 0)
  bumped.foldLeft(
    (m.copy(workspace = m.workspace.copy(store = s)),
      Vector.empty[StorageReq])) {
        case ((mm, rs), (u, v, d)) =>
          val (m2, id) = nextReq(mm)
          (m2, rs :+ StorageReq.Persist(id, u, v, d, quiet = true))
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
