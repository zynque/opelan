package opelan.ui.workbench

import opelan.collaboration.automerge.ChangeInfo
import opelan.foundation.document._
import opelan.ui.editor.{SyncedEntry, SyncedHistory}
import opelan.ui.fp.Update
import opelan.ui.typeddoc.{TypedDocInput, TypedDocOutput}
import WorkbenchInput._
import WorkbenchOutput._

// The workbench's pure transition function: every decision the old
// imperative shell made — open / save / follow-ref / sync / branch —
// computed as (new state, effects). The shell interprets the outputs.
def workbenchUpdate(
    m: WorkbenchModel, in: WorkbenchInput): Update[WorkbenchModel, WorkbenchOutput] =
  in match {
    case Boot => Update(m, Vector(HydrateStorage))

    case SwitchShellView(v) =>
      Update(m.copy(view = v, status = s"Switched to ${viewName(v)} view"))

    case NewDocument => Update(m, Vector(PromptName(save = false)))

    case DocumentNamed(name) =>
      val url = m.workspace.freshUrl(name)
      val doc: Document[NodeData] = beginDocument(NodeData.StringData(name))
      val (ws, written) = m.workspace.save(url, doc)
      written match {
        case Some(v) =>
          open(m.copy(workspace = ws), url, v, doc,
            extra = Vector(PersistDoc(url, v, doc)),
            status = Some(s"Created $url@v$v"))
        case None => Update(m) // a fresh url always writes
      }

    case SelectDocument(url) =>
      m.workspace.store.head(url) match {
        case Some((v, d)) => open(m, url, v, d)
        case None         => status(m, s"No versions of $url")
      }

    // The pane owns the live document; pull it via RequestDoc and save
    // when the DocChanged reply arrives.
    case SaveRequested =>
      if (m.pendingSave) Update(m)
      else Update(
        m.copy(pendingSave = true),
        Vector(PushPane(TypedDocInput.RequestDoc)))

    case SaveAsNamed(name) =>
      m.workspace.doc match {
        case Some(d) => doSave(m, m.workspace.freshUrl(name), d)
        case None    => Update(m)
      }

    case ToggleSync =>
      if (!m.syncOn)
        Update(
          m.copy(syncOn = true,
            status = "Live sync on — tabs share edits over BroadcastChannel"),
          m.workspace.openUrl.map(u =>
            AttachSync(u, m.workspace.doc.map(render).getOrElse(""))).toVector)
      else
        // Leaving sync returns the pane to a local document: reload it so
        // undo doesn't keep requesting session reverts that can't run.
        Update(
          m.copy(syncOn = false, status = "Live sync off"),
          Vector(DetachSync) ++
            m.workspace.doc.map(d => PushPane(TypedDocInput.Load(d))))

    case FromPane(out) => fromPane(m, out)

    case StorageLoaded(store) =>
      val m2 = m.copy(
        workspace = m.workspace.copy(store = store),
        status = s"Storage initialized — ${store.urls.length} document(s)")
      store.urls.headOption.flatMap(u =>
        store.head(u).map { case (v, d) => (u, v, d) }) match {
        case Some((url, v, d)) => open(m2, url, v, d)
        case None              => Update(m2)
      }

    case StorageFailed(msg) => status(m, s"Storage initialization failed: $msg")
    case StatusMsg(msg)     => status(m, msg)

    case SessionAttached(url, name) =>
      status(m, s"Sync attached — $url ($name)")

    // A session's change graph grew (local or remote): rebuild the pane's
    // doc and history from it. The "remote edit" status fires only when
    // the latest change is a peer's and the text actually moved — local
    // edits and metadata-only growth stay quiet.
    case SessionUpdated(url, text, entries, heads, actor) =>
      if (!m.workspace.openUrl.contains(url)) Update(m)
      else parse(text) match {
        case Some(d) =>
          val seen = m.syncedTexts.get(url).contains(text)
          val remote = entries.lastOption.exists(_.actor != actor)
          Update(
            m.copy(
              workspace = m.workspace.open(
                url, m.workspace.openVersion.getOrElse(0), d),
              syncedTexts =
                if (seen) m.syncedTexts else m.syncedTexts + (url -> text),
              status =
                if (!seen && remote) s"Synced remote edit — $url" else m.status),
            Vector(PushPane(TypedDocInput.SyncHistory(d, syncedHistory(entries, heads)))))
        case None => status(m, s"Remote edit to $url did not parse")
      }

    case OpenBranch(base, branchUrl, d) =>
      val u = open(m, branchUrl, 0, d,
        status = Some(s"Branched — $branchUrl (peers asked to rejoin)"))
      u.copy(out = u.out :+ AnnounceBranch(base, branchUrl))

    // Join with an empty seed: the peer's branch history arrives whole
    // over sync rather than being overwritten by the placeholder.
    case JoinBranch(branchUrl) =>
      if (!m.syncOn) Update(m)
      else {
        val placeholder: Document[NodeData] =
          beginDocument(NodeData.StringData("branch"))
        Update(
          m.copy(
            workspace = m.workspace.open(branchUrl, 0, placeholder),
            status = s"Joined branch $branchUrl"),
          Vector(
            PushPane(TypedDocInput.Load(placeholder)),
            AttachSync(branchUrl, "")))
      }
  }

// Outputs coming up from the TypedDoc pane.
private def fromPane(
    m: WorkbenchModel, out: TypedDocOutput): Update[WorkbenchModel, WorkbenchOutput] =
  out match {
    case TypedDocOutput.DocChanged(d) =>
      val m2 = m.copy(workspace = m.workspace.changed(d))
      val feed = sessionFeed(m2, d)
      if (m.pendingSave) {
        val u = saveDocument(m2.copy(pendingSave = false), d)
        u.copy(out = u.out ++ feed)
      } else Update(m2, feed)

    // History navigation: the mirror tracks what the user sees (saving
    // checkpoints the viewed version) but no session ever hears of it.
    case TypedDocOutput.DocViewed(d) =>
      val m2 = m.copy(workspace = m.workspace.changed(d))
      if (m.pendingSave) saveDocument(m2.copy(pendingSave = false), d)
      else Update(m2)

    case TypedDocOutput.UndoRequested =>
      m.workspace.openUrl.filter(_ => m.syncOn) match {
        case Some(url) => Update(m, Vector(SessionUndo(url)))
        case None      => status(m, "Nothing to undo")
      }
    case TypedDocOutput.RedoRequested =>
      m.workspace.openUrl.filter(_ => m.syncOn) match {
        case Some(url) => Update(m, Vector(SessionRedo(url)))
        case None      => status(m, "Nothing to redo")
      }

    // The user edited a checked-out version of a synced doc: ask the
    // shell to fork a branch draft seeded with the edited doc.
    case TypedDocOutput.BranchRequested(d) =>
      m.workspace.openUrl.filter(_ => m.syncOn) match {
        case Some(base) => Update(m, Vector(RequestBranch(base, d)))
        case None       => Update(m)
      }

    // The pane asked to follow a ref (Ctrl+Enter on an ExternalNodeRef
    // node): resolve it against the store and open the pinned version.
    case TypedDocOutput.FollowRef(ref) =>
      m.workspace.follow(ref) match {
        case Some((url, v, d, nodeId)) =>
          open(m, url, v, d, Some(nodeId),
            status = Some(s"Followed ref → $url@v$v#$nodeId"))
        case None =>
          status(m, s"Unresolved ref: ${ref.documentUrl}@${ref.documentVersionId} (not in document store)")
      }

    case TypedDocOutput.Status(msg) => status(m, msg)
  }

// Push a stored document (at a specific version) into the pane, attaching
// a session when sync is on.
private def open(
    m: WorkbenchModel, url: String, version: Int, d: Document[NodeData],
    select: Option[Int] = None,
    extra: Vector[WorkbenchOutput] = Vector.empty,
    status: Option[String] = None): Update[WorkbenchModel, WorkbenchOutput] =
  Update(
    m.copy(
      workspace = m.workspace.open(url, version, d),
      status = status.getOrElse(m.status)),
    Vector(PushPane(TypedDocInput.Load(d, select))) ++
      (if (m.syncOn) Vector(AttachSync(url, render(d))) else Vector.empty) ++
      extra)

// Saving needs the live doc only when choosing a name; the pane-supplied
// doc is what gets checkpointed.
private def saveDocument(
    m: WorkbenchModel, d: Document[NodeData]): Update[WorkbenchModel, WorkbenchOutput] =
  m.workspace.openUrl match {
    case Some(url) => doSave(m, url, d)
    case None      => Update(m, Vector(PromptName(save = true)))
  }

private def doSave(
    m: WorkbenchModel, url: String, d: Document[NodeData]): Update[WorkbenchModel, WorkbenchOutput] = {
  val (ws, written) = m.workspace.save(url, d)
  written match {
    case Some(v) =>
      Update(m.copy(workspace = ws), Vector(PersistDoc(url, v, d)))
    case None =>
      Update(m.copy(workspace = ws, status = s"No changes to save — $url"))
  }
}

// A local edit feeds the open doc's session as outline text; the session
// ignores repeats, so load/sync echoes terminate there.
private def sessionFeed(
    m: WorkbenchModel, d: Document[NodeData]): Vector[WorkbenchOutput] =
  m.workspace.openUrl.filter(_ => m.syncOn)
    .map(u => SessionText(u, render(d))).toVector

private def syncedHistory(
    entries: Vector[ChangeInfo],
    heads: Vector[String]): SyncedHistory =
  SyncedHistory(
    entries.flatMap(e => parse(e.snapshotText).map(doc =>
      SyncedEntry(e.hash, e.deps,
        e.message.getOrElse(shortActor(e.actor)), doc))),
    heads)

private def shortActor(actor: String): String =
  s"actor-${actor.take(8)}"

private def status(
    m: WorkbenchModel, msg: String): Update[WorkbenchModel, WorkbenchOutput] =
  Update(m.copy(status = msg))

private def viewName(v: ShellView): String = v match {
  case ShellView.Document   => "document"
  case ShellView.Components => "components"
}
