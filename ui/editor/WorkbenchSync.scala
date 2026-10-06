package opelan.ui.editor

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Random, Try}
import org.scalajs.dom
import opelan.foundation.document._
import opelan.collaboration.{DocSession, Identity}
import opelan.collaboration.automerge.ChangeInfo
import opelan.collaboration.backends.BroadcastTransport
import opelan.data.storage.IndexedDBStore
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
        val (actor, name) = sessionIdentity()
        val session = new DocSession(
          url,
          new BroadcastTransport(url),
          actor,
          name,
          (entries, heads) => sessionUpdate(url, entries, heads),
          bytes => IndexedDBStore.storeAutomergeDoc(
            url, bytes.asInstanceOf[js.Dynamic]))
        sessions += url -> session
        session.attach(
          workspace.doc.map(Outline.render).getOrElse(""), saved)
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
        Outline.parse(session.text) match {
          case Some(d) =>
            workspace = workspace.open(
              url, workspace.openVersion.getOrElse(0), d)
            val synced = SyncedHistory(
              entries.flatMap(e => Outline.parse(e.snapshotText).map(doc =>
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

  // Per-tab identity, stable across that tab's reloads; falls back to
  // ephemeral ids when storage is unavailable.
  private def sessionIdentity(): (String, String) = {
    def stored(key: String, gen: => String): String =
      Try(Option(dom.window.sessionStorage.getItem(key))
          .filter(_.nonEmpty)).toOption.flatten
        .getOrElse {
          val v = gen
          Try(dom.window.sessionStorage.setItem(key, v))
          v
        }
    (stored("opelan:actor", Identity.freshActorId(Random)),
     stored("opelan:name", Identity.generateName(Random)))
  }
}
