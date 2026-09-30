//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.data.storage

import scala.scalajs.js
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

// IndexedDB persistence for the workbench. Payloads are js.Dynamic so the
// store stays agnostic about what it holds: project stubs, Automerge document
// bytes, and UI settings. v2 drops the scaffold-era "schemas"/"definitions"
// object stores — schemas become documents and definitions live in them.
class IndexedDBStore(dbName: String = "opelan-workbench", version: Int = 2) {
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

        ensureStore("projects", "id")
        ensureStore("automerge_docs", "id")
        ensureStore("settings", "key")
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

  def storeProject(project: js.Dynamic): Future[Unit] =
    transact("projects", "readwrite", "Failed to store project")(_.put(project))(_ => ())

  def getProject(id: String): Future[Option[js.Dynamic]] =
    transact("projects", "readonly", "Failed to get project")(_.get(id))(
      result => Option(result).map(_.asInstanceOf[js.Dynamic]))

  def listProjects(): Future[List[js.Dynamic]] =
    transact("projects", "readonly", "Failed to list projects")(_.getAll())(
      _.asInstanceOf[js.Array[js.Dynamic]].toList)

  def storeAutomergeDoc(docId: String, document: js.Dynamic): Future[Unit] =
    transact("automerge_docs", "readwrite", "Failed to store Automerge document")(
      _.put(js.Dynamic.literal(
        "id" -> docId,
        "document" -> document,
        "lastModified" -> new js.Date().toISOString())))(_ => ())

  def getAutomergeDoc(docId: String): Future[Option[js.Dynamic]] =
    transact("automerge_docs", "readonly", "Failed to get Automerge document")(_.get(docId))(
      result => Option(result).map(_.asInstanceOf[js.Dynamic]))

  def storeSetting(key: String, value: js.Any): Future[Unit] =
    transact("settings", "readwrite", "Failed to store setting")(
      _.put(js.Dynamic.literal("key" -> key, "value" -> value)))(_ => ())

  def getSetting(key: String): Future[Option[js.Any]] =
    transact("settings", "readonly", "Failed to get setting")(_.get(key))(
      result => Option(result).map(_.asInstanceOf[js.Dynamic].selectDynamic("value")))

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

// Global IndexedDB store instance
object IndexedDBStore {
  private val store = new IndexedDBStore()

  def initialize(): Future[Unit] = store.initialize()
  def storeProject(project: js.Dynamic): Future[Unit] = store.storeProject(project)
  def getProject(id: String): Future[Option[js.Dynamic]] = store.getProject(id)
  def listProjects(): Future[List[js.Dynamic]] = store.listProjects()
  def storeAutomergeDoc(docId: String, document: js.Dynamic): Future[Unit] = store.storeAutomergeDoc(docId, document)
  def getAutomergeDoc(docId: String): Future[Option[js.Dynamic]] = store.getAutomergeDoc(docId)
  def storeSetting(key: String, value: js.Any): Future[Unit] = store.storeSetting(key, value)
  def getSetting(key: String): Future[Option[js.Any]] = store.getSetting(key)
  def close(): Unit = store.close()
}
