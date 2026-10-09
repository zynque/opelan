package opelan.data.storage

import scala.scalajs.js
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

// IndexedDB persistence for the workbench — a generic record store over
// named object stores. Record shapes live in the per-store repos:
// `documents` in DocumentRepo (versioned outline text) and
// `automerge_docs` in AutomergeRepo (sync op-log bytes). v2 dropped the
// scaffold-era "schemas"/"definitions" object stores — schemas become
// documents and definitions live in them.
class IndexedDBStore(dbName: String = "opelan-workbench", version: Int = 3) {
  private var db: js.Dynamic = null
  private var isInitialized = false
  private val initPromise = Promise[Unit]()

  def initialize(): Future[Unit] = {
    if (!isInitialized) {
      val request = dom.window.indexedDB.get.open(dbName, version)

      request.onerror = (event: dom.Event) => {
        initPromise.tryFailure(new Exception("Failed to open IndexedDB"))
      }

      request.onsuccess = (event: dom.Event) => {
        db = event.target.asInstanceOf[js.Dynamic].result
        isInitialized = true
        initPromise.trySuccess(())
      }

      request.onupgradeneeded = (event: dom.Event) => {
        val upgradedDb = event.target.asInstanceOf[js.Dynamic].result
        val storeNames = upgradedDb.objectStoreNames

        // Retired scaffold-era stores
        List("schemas", "definitions").foreach { name =>
          if (storeNames.contains(name).asInstanceOf[Boolean]) {
            upgradedDb.deleteObjectStore(name)
          }
        }

        def ensureStore(name: String, keyPath: String): Unit = {
          if (!storeNames.contains(name).asInstanceOf[Boolean]) {
            upgradedDb.createObjectStore(name, js.Dynamic.literal("keyPath" -> keyPath))
          }
        }

        ensureStore("automerge_docs", "id")
        ensureStore("documents", "key")
      }
    }

    initPromise.future
  }

  // Run a single request against a store, completing a promise with a value
  // derived from request.result.
  private def transact[A](
    storeName: String,
    mode: String,
    errorMessage: String
  )(request: js.Dynamic => js.Dynamic)(read: js.Dynamic => A): Future[A] = {
    ensureInitialized().flatMap { _ =>
      val store = db.transaction(js.Array(storeName), mode).objectStore(storeName)
      val promise = Promise[A]()
      val req = request(store)
      req.onsuccess = (event: dom.Event) => promise.success(read(req.result))
      req.onerror = (event: dom.Event) => promise.failure(new Exception(errorMessage))
      promise.future
    }
  }

  // IndexedDB get() returns undefined for a missing key — Option() alone
  // only maps null to None, so check explicitly.
  private def optResult(result: js.Dynamic): Option[js.Dynamic] =
    if (result == null || js.isUndefined(result)) None
    else Some(result.asInstanceOf[js.Dynamic])

  def put(storeName: String, record: js.Dynamic): Future[Unit] =
    transact(storeName, "readwrite", s"Failed to write to $storeName")(
      _.put(record))(_ => ())

  def get(storeName: String, key: String): Future[Option[js.Dynamic]] =
    transact(storeName, "readonly", s"Failed to read from $storeName")(
      _.get(key))(optResult)

  def getAll(storeName: String): Future[List[js.Dynamic]] =
    transact(storeName, "readonly", s"Failed to list $storeName")(
      _.getAll())(_.asInstanceOf[js.Array[js.Dynamic]].toList)

  private def ensureInitialized(): Future[Unit] = {
    if (isInitialized) Future.successful(()) else initialize()
  }

  def close(): Unit = {
    if (db != null) {
      db.close()
      isInitialized = false
    }
  }
}

// Global IndexedDB store instance.
object IndexedDBStore {
  private val store = new IndexedDBStore()
  export store.{initialize, put, get, getAll, close}
}
