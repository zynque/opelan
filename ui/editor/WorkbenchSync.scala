package opelan.ui.editor

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import scala.concurrent.ExecutionContext.Implicits.global
import opelan.foundation.document._
import opelan.collaboration.DocSession
import opelan.collaboration.backends.BroadcastTransport
import opelan.data.storage.IndexedDBStore
import opelan.ui.typeddoc.TypedDocInput

// Live-sync plumbing for the workbench shell: a DocSession per document
// URL, enabled by the toolbar's Sync toggle. Local DocChanged edits flow
// into the session as outline-text diffs; remote changes arrive as text,
// reparse, and load into the pane — the DocChanged echo that produces is
// absorbed by DocSession's lastText check, so it doesn't loop.
//
// Automerge state persists to the `automerge_docs` object store, so a
// reloading tab resumes sync rather than starting as a fresh actor.
// This is the operational (draft) layer — saving to the document store
// remains an explicit, curated act.
trait WorkbenchSync extends WorkbenchDocs {
  private var sessions = Map.empty[String, DocSession]
  private var attaching = Set.empty[String]
  private var syncOn = false

  protected def toggleSync(): Unit = {
    syncOn = !syncOn
    if (syncOn) workspace.openUrl.foreach(attachSync)
    else closeSessions()
    updateStatus(
      if (syncOn) "Live sync on — tabs share edits over BroadcastChannel"
      else "Live sync off")
  }

  protected def closeSessions(): Unit = {
    sessions.values.foreach(_.close())
    sessions = Map.empty
  }

  // WorkbenchDocs hooks (see openDocument / docOutput).
  override protected def documentOpened(url: String): Unit =
    if (syncOn) attachSync(url)

  override protected def documentEdited(d: Document[NodeData]): Unit =
    workspace.openUrl.flatMap(sessions.get)
      .foreach(_.localText(Outline.render(d)))

  private def attachSync(url: String): Unit =
    if (syncOn && !sessions.contains(url) && !attaching(url)) {
      attaching += url
      IndexedDBStore.getAutomergeDoc(url).onComplete { result =>
        attaching -= url
        val saved = result.toOption.flatten
          .filterNot(r => js.isUndefined(r.document))
          .map(_.document.asInstanceOf[Uint8Array])
        val session = new DocSession(
          url,
          new BroadcastTransport(url),
          text => remoteText(url, text),
          bytes => IndexedDBStore.storeAutomergeDoc(
            url, bytes.asInstanceOf[js.Dynamic]))
        sessions += url -> session
        session.attach(
          workspace.doc.map(Outline.render).getOrElse(""), saved)
        updateStatus(s"Sync attached — $url")
      }
    }

  // Remote text for the open doc reparses and loads into the pane.
  // Edits to non-open docs just update the persisted Automerge state.
  private def remoteText(url: String, text: String): Unit =
    if (workspace.openUrl.contains(url)) {
      Outline.parse(text) match {
        case Some(d) =>
          workspace = workspace.open(
            url, workspace.openVersion.getOrElse(0), d)
          documentEditor.foreach(_.send(TypedDocInput.Load(d)))
          updateStatus(s"Synced remote edit — $url")
        case None =>
          updateStatus(s"Remote edit to $url did not parse")
      }
    }
}
