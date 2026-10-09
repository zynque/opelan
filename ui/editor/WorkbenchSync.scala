package opelan.ui.editor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import org.scalajs.dom
import opelan.foundation.document._
import opelan.collaboration.{DocSession, sessionIdentity}
import opelan.collaboration.automerge.ChangeInfo
import opelan.collaboration.backends.BroadcastTransport
import opelan.data.storage.{loadSyncDoc, saveSyncDoc}
import opelan.ui.typeddoc.TypedDocInput

// Live-sync plumbing for the workbench shell: a DocSession per document
// URL, enabled by the toolbar's Sync toggle. Local DocChanged edits flow
// into the session as outline-text diffs; the session reports its shared
// change graph back, which reparses into doc + history pushed into the
// pane — the DocChanged echo that produces is absorbed by DocSession's
// lastText check, so it doesn't loop.
//
// Session identity is per-tab (sessionStorage): a unique actor id keeps
// concurrent tabs from colliding on (actor, seq), and the generated name
// tags each change so shared history shows authors. Automerge state
// persists to the `automerge_docs` object store, so a reloading tab
// resumes sync rather than starting as a fresh actor.
// This is the operational (draft) layer — saving to the document store
// remains an explicit, curated act.
trait WorkbenchSync extends WorkbenchDocs {
  private var sessions = Map.empty[String, DocSession]
  private var attaching = Set.empty[String]
  private var syncedTexts = Map.empty[String, String]
  private var syncOn = false

  protected def toggleSync(): Unit = {
    syncOn = !syncOn
    if (syncOn) workspace.openUrl.foreach(u => attachSync(u))
    else closeSessions()
    updateStatus(
      if (syncOn) "Live sync on — tabs share edits over BroadcastChannel"
      else "Live sync off")
  }

  protected def closeSessions(): Unit = {
    sessions.values.foreach(_.close())
    sessions = Map.empty
    // Leaving sync returns the pane to a local document: reset history so
    // undo doesn't keep requesting session reverts that can't run.
    workspace.doc.foreach(d =>
      documentEditor.foreach(_.send(TypedDocInput.Load(d))))
  }

  // WorkbenchDocs hooks (see openDocument / docOutput).
  override protected def documentOpened(url: String): Unit =
    if (syncOn) attachSync(url)

  override protected def documentEdited(d: Document[NodeData]): Unit =
    workspace.openUrl.flatMap(sessions.get)
      .foreach(_.localText(render(d)))

  // Synced-head undo/redo: the session reverts this actor's own last
  // change (a new change in the graph — never a rewind of shared state).
  override protected def undoRequested(): Unit =
    updateStatus(workspace.openUrl.flatMap(sessions.get)
      .map(_.undo()).getOrElse("Nothing to undo"))

  override protected def redoRequested(): Unit =
    updateStatus(workspace.openUrl.flatMap(sessions.get)
      .map(_.redo()).getOrElse("Nothing to redo"))

  // The user edited a checked-out version: fork a branch draft seeded
  // with the edited doc rather than reverting the shared frontier.
  // The main session keeps syncing untouched; peers get a rejoin offer.
  override protected def branchRequested(d: Document[NodeData]): Unit =
    workspace.openUrl.filter(sessions.contains).foreach { base =>
      val branchUrl = s"$base~${Random.alphanumeric.take(6).mkString.toLowerCase}"
      openDocument(branchUrl, 0, d)
      sessions.get(base).foreach(_.announce(branchUrl))
      updateStatus(s"Branched — $branchUrl (peers asked to rejoin)")
    }

  // A peer forked the shared line into a branch draft: offer to follow
  // rather than silently switching — the main doc is unaffected either
  // way. Joining attaches a session with empty seed text so the peer's
  // branch history arrives whole over sync.
  private def joinBranch(branchUrl: String): Unit =
    if (!sessions.contains(branchUrl) &&
        dom.window.confirm(
          s"A collaborator started a branch — rejoin at $branchUrl?")) {
      val placeholder: Document[NodeData] =
        beginDocument(NodeData.StringData("branch"))
      workspace = workspace.open(branchUrl, 0, placeholder)
      documentEditor.foreach(_.send(TypedDocInput.Load(placeholder)))
      attachSync(branchUrl, Some(""))
      updateStatus(s"Joined branch $branchUrl")
    }

  private def attachSync(
      url: String, seedText: Option[String] = None): Unit =
    if (syncOn && !sessions.contains(url) && !attaching(url)) {
      attaching += url
      loadSyncDoc(url).onComplete { result =>
        attaching -= url
        val saved = result.toOption.flatten
        val (actor, name) = sessionIdentity()
        val session = new DocSession(
          url,
          new BroadcastTransport(url),
          actor,
          name,
          (entries, heads) => sessionUpdate(url, entries, heads),
          bytes => saveSyncDoc(url, bytes),
          joinBranch)
        sessions += url -> session
        session.attach(
          seedText.getOrElse(
            workspace.doc.map(render).getOrElse("")),
          saved)
        updateStatus(s"Sync attached — $url ($name)")
      }
    }

  // A session's change graph grew (local or remote): rebuild the pane's
  // doc and history from it. Edits to non-open docs just update the
  // persisted Automerge state. The "remote edit" status fires only when
  // the latest change is a peer's and the text actually moved — local
  // edits and metadata-only growth stay quiet.
  private def sessionUpdate(
      url: String,
      entries: Vector[ChangeInfo],
      heads: Vector[String]): Unit =
    if (workspace.openUrl.contains(url)) {
      sessions.get(url).foreach { session =>
        parse(session.text) match {
          case Some(d) =>
            workspace = workspace.open(
              url, workspace.openVersion.getOrElse(0), d)
            val synced = SyncedHistory(
              entries.flatMap(e => parse(e.snapshotText).map(doc =>
                SyncedEntry(e.hash, e.deps,
                  e.message.getOrElse(shortActor(e.actor)), doc))),
              heads)
            documentEditor.foreach(_.send(TypedDocInput.SyncHistory(d, synced)))
            if (syncedTexts.get(url) != Some(session.text)) {
              syncedTexts += url -> session.text
              if (entries.lastOption.exists(_.actor != session.actorId))
                updateStatus(s"Synced remote edit — $url")
            }
          case None =>
            updateStatus(s"Remote edit to $url did not parse")
        }
      }
    }

  private def shortActor(actor: String): String =
    s"actor-${actor.take(8)}"
}
