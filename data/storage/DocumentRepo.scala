//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.data.storage

import scala.scalajs.js
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import opelan.foundation.document._

// Persistence for the document store: each version is one record in the
// `documents` object store, keyed "url@version", holding the document's
// outline text. Heads are derived by scanning records (documents are few
// and small), so there is no separate index to keep consistent.
object DocumentRepo {

  // Write one version record. The caller's in-memory Store assigns the
  // version number (Store.put) before persisting.
  def persist(url: String, version: Int, doc: Document[NodeData]): Future[Unit] =
    IndexedDBStore.storeDocumentRecord(js.Dynamic.literal(
      "key" -> s"$url@$version",
      "url" -> url,
      "version" -> version,
      "outline" -> Outline.render(doc)))

  // Rebuild the whole store from persisted version records. Records that
  // fail to parse are skipped rather than failing the load.
  def loadAll(): Future[Store] =
    IndexedDBStore.listDocumentRecords().map { records =>
      records.foldLeft(Store.empty) { (store, r) =>
        Outline.parse(r.outline.asInstanceOf[String]) match {
          case Some(doc) =>
            store.withVersion(
              r.url.asInstanceOf[String],
              r.version.asInstanceOf[Int],
              doc)
          case None => store
        }
      }
    }
}
