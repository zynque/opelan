package opelan.ui.workbench

import opelan.collaboration.automerge.ChangeInfo
import opelan.foundation.document._
import opelan.ui.editor.{SyncedEntry, SyncedHistory}
import opelan.ui.fx._
import opelan.ui.typeddoc.{TypedDocInput, TypedDocOutput}
import opelan.ui.typeddoc.TypedDocOutput._
import opelan.ui.workbench.WorkbenchUpdate._

// Handlers for child outputs routed back up as workbench inputs. Each
// returns the next desired state; requests to other seams are declared
// by setting the corresponding state fields.
private[workbench] def fromPane(m: WorkbenchModel, out: TypedDocOutput): Upd =
  out match {
    // The pane's doc changed: record it, mirror it into the live
    // session's desired text (its localText ignores the echo).
    case DocChanged(d) =>
      val m2 = m.copy(
        workspace = m.workspace.changed(d),
        synced = m.workspace.openUrl.filter(m.synced.contains)
          .map(u => m.synced + (u -> render(d))).getOrElse(m.synced))
      if (m.pendingSave) saveDocument(m2.copy(pendingSave = false), d)
      else up(m2)

    case DocViewed(d) =>
      val m2 = m.copy(workspace = m.workspace.changed(d))
      if (m.pendingSave) saveDocument(m2.copy(pendingSave = false), d)
      else up(m2)

    case UndoRequested =>
      sessionCmd(m, SyncCmd.Undo(_), "Nothing to undo")
    case RedoRequested =>
      sessionCmd(m, SyncCmd.Redo(_), "Nothing to redo")

    // Branching needs a generated suffix — request it and remember the
    // (base url, edited doc) pair; FromRandom finishes the job.
    case BranchRequested(d) =>
      m.workspace.openUrl.filter(m.synced.contains) match {
        case Some(base) =>
          val (m2, id) = nextReq(m)
          up(m2.copy(
            randomReq = Some(RandomReq.Suffix(id, 6)),
            pendingBranch = Some(base -> d)))
        case None => up(m)
      }

    case FollowRef(ref) =>
      m.workspace.store.head(ref.documentUrl) match {
        case Some((v, d)) =>
          open(m, ref.documentUrl, v, d,
            sel = Some(ref.nodeId),
            status = Some(s"Jumped to ${ref.documentUrl}@v$v"))
        case None =>
          up(m.copy(status = s"No versions of ${ref.documentUrl}"))
      }

    case Status(msg) => up(m.copy(status = msg))
  }

private[workbench] def sessionCmd(
    m: WorkbenchModel, f: String => SyncCmd, empty: String): Upd =
  m.workspace.openUrl.filter(m.synced.contains) match {
    case Some(url) =>
      val (m2, id) = nextReq(m)
      up(m2.copy(syncCmd = Some(id -> f(url))))
    case None => up(m.copy(status = empty))
  }

// Only the queue's head is ever in flight (it alone is the child's
// props), so every response belongs to the head — pop it.
private[workbench] def fromStorage(m: WorkbenchModel, ev: StorageEv): Upd =
  ev match {
    case StorageEv.Hydrated(store) =>
      val m2 = m.copy(
        workspace = m.workspace.copy(store = store),
        storageQ = m.storageQ.tail,
        status = s"Storage initialized — ${store.urls.length} document(s)")
      store.urls.headOption
        .flatMap(u => store.head(u).map((u, _, _))) match {
        case Some((url, v, d)) => open(m2, url, v, d)
        case None => up(m2)
      }
    case StorageEv.HydrateFailed(msg) =>
      up(m.copy(storageQ = m.storageQ.tail,
        status = s"Storage initialization failed: $msg"))
    case StorageEv.Persisted(url, v) =>
      up(m.copy(storageQ = m.storageQ.tail, status = s"Saved $url@v$v"))
    case StorageEv.PersistFailed(msg) =>
      up(m.copy(storageQ = m.storageQ.tail, status = s"Save failed: $msg"))
  }

private[workbench] def fromPrompt(m: WorkbenchModel, ev: PromptEv): Upd = {
  val cleared = m.copy(prompt = None, promptFor = None)
  (m.promptFor, ev) match {
    case (Some(PromptFor.NewDoc), PromptEv.Answered(_, Some(name))) =>
      createDoc(cleared, name)
    case (Some(PromptFor.SaveAs), PromptEv.Answered(_, Some(name))) =>
      cleared.workspace.doc
        .map(d => doSave(cleared, cleared.workspace.freshUrl(name), d))
        .getOrElse(up(cleared))
    case (Some(PromptFor.Join(url)), PromptEv.Decided(_, true)) =>
      joinBranch(cleared, url)
    case _ => up(cleared)
  }
}

private[workbench] def fromRandom(m: WorkbenchModel, ev: RandomEv): Upd = {
  val cleared = m.copy(randomReq = None)
  (m.pendingBranch, ev) match {
    case (Some((base, d)), RandomEv.Generated(_, suffix)) =>
      // The base session is still attached, so its Automerge doc holds
      // every edit the branch doc does — seeding the branch session
      // with the rendered text is enough, no bytes needed.
      val branchUrl = s"$base~$suffix"
      val (m2, id) = nextReq(cleared.copy(pendingBranch = None))
      open(m2.copy(syncCmd = Some(id -> SyncCmd.Announce(base, branchUrl))),
        branchUrl, 0, d,
        status = Some(s"Branched — $branchUrl (peers asked to rejoin)"))
    case _ => up(cleared)
  }
}

private[workbench] def fromSync(m: WorkbenchModel, ev: SyncEv): Upd =
  ev match {
    case SyncEv.Attached(url, name) =>
      up(m.copy(status = s"Session ready — $name on $url"))

    // A session's Automerge doc changed — local or remote. Parse its
    // text and push the synced history into the pane.
    case SyncEv.Grew(url, text, entries, heads, actor) =>
      if (!m.workspace.openUrl.contains(url)) up(m)
      else parse(text) match {
        case Some(d) =>
          val seen = m.syncedTexts.get(url).contains(text)
          val remote = entries.lastOption.exists(_.actor != actor)
          val m2 = push(m.copy(
            workspace = m.workspace.open(url,
              m.workspace.openVersion.getOrElse(0), d),
            syncedTexts = if (seen) m.syncedTexts
              else m.syncedTexts + (url -> text)),
            TypedDocInput.SyncHistory(d, syncedHistory(entries, heads)))
          up(m2.copy(status =
            if (!seen && remote) s"Synced remote edit — $url"
            else m2.status))
        case None =>
          up(m.copy(status = s"Remote edit to $url did not parse"))
      }

    // A peer announced a branch — ask before rejoining there.
    case SyncEv.OfferBranch(branchUrl) =>
      val (m2, id) = nextReq(m)
      up(m2.copy(
        prompt = Some(PromptReq.Confirm(id,
          s"A collaborator started a branch — rejoin at $branchUrl?")),
        promptFor = Some(PromptFor.Join(branchUrl))))

    case SyncEv.Status(msg) => up(m.copy(status = msg))
  }

// DocSession's Automerge history converted for the pane: each change's
// snapshot text parsed back into a document, labelled by its author.
private[workbench] def syncedHistory(
    entries: Vector[ChangeInfo],
    heads: Vector[String]): SyncedHistory =
  SyncedHistory(
    entries.flatMap(e => parse(e.snapshotText).map(doc =>
      SyncedEntry(e.hash, e.deps,
        e.message.getOrElse(shortActor(e.actor)), doc))),
    heads)

private[workbench] def shortActor(actor: String): String =
  s"actor-${actor.take(8)}"
