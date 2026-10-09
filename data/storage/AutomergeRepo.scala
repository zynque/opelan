package opelan.data.storage

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// Persistence for live-sync sessions: one record per document URL in the
// `automerge_docs` store holding the Automerge doc's saved bytes — the
// op log a reattaching session resumes from.

// The bytes a session last saved for this URL, if it was synced before.
def loadSyncDoc(url: String): Future[Option[Uint8Array]] =
  IndexedDBStore.get("automerge_docs", url).map(_.flatMap { r =>
    // A record written without its document payload reads as undefined.
    if (js.isUndefined(r.document)) None
    else Some(r.document.asInstanceOf[Uint8Array])
  })

def saveSyncDoc(url: String, bytes: Uint8Array): Future[Unit] =
  IndexedDBStore.put("automerge_docs", js.Dynamic.literal(
    "id" -> url,
    "document" -> bytes,
    "lastModified" -> new js.Date().toISOString()))
