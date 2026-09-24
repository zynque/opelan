//> using dep org.scala-js:scalajs-dom_sjs1_3:2.8.1

package opelan.data.storage

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

// IndexedDB store for persistent data storage using js.Dynamic for simplicity
class IndexedDBStore(dbName: String = "opelan-workbench", version: Int = 1) {
  private var db: js.Dynamic = null
  private var isInitialized = false
  private val initPromise = Promise[Unit]()
  
  // Initialize the database
  def initialize(): Future[Unit] = {
    if (!isInitialized) {
      val request = dom.window.indexedDB.get.open(dbName, version)
      
      request.onerror = (event: dom.Event) => {
        initPromise.failure(new Exception("Failed to open IndexedDB"))
      }
      
      request.onsuccess = (event: dom.Event) => {
        db = event.target.asInstanceOf[js.Dynamic].result
        isInitialized = true
        initPromise.success(())
      }
      
      request.onupgradeneeded = (event: dom.Event) => {
        val db = event.target.asInstanceOf[js.Dynamic].result
        
        // Create object stores for different data types
        if (!db.objectStoreNames.contains("projects").asInstanceOf[Boolean]) {
          db.createObjectStore("projects", js.Dynamic.literal("keyPath" -> "id"))
        }
        
        if (!db.objectStoreNames.contains("schemas").asInstanceOf[Boolean]) {
          db.createObjectStore("schemas", js.Dynamic.literal("keyPath" -> "id"))
        }
        
        if (!db.objectStoreNames.contains("definitions").asInstanceOf[Boolean]) {
          db.createObjectStore("definitions", js.Dynamic.literal("keyPath" -> "id"))
        }
        
        if (!db.objectStoreNames.contains("automerge_docs").asInstanceOf[Boolean]) {
          db.createObjectStore("automerge_docs", js.Dynamic.literal("keyPath" -> "id"))
        }
        
        if (!db.objectStoreNames.contains("settings").asInstanceOf[Boolean]) {
          db.createObjectStore("settings", js.Dynamic.literal("keyPath" -> "key"))
        }
      }
    }
    
    initPromise.future
  }
  
  // Store a project
  def storeProject(project: js.Dynamic): Future[Unit] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("projects"), "readwrite")
      val store = transaction.objectStore("projects")
      
      val promise = Promise[Unit]()
      
      val request = store.put(project)
      
      request.onsuccess = (event: dom.Event) => {
        promise.success(())
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to store project"))
      }
      
      promise.future
    }
  }
  
  // Get a project by ID
  def getProject(id: String): Future[Option[js.Dynamic]] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("projects"), "readonly")
      val store = transaction.objectStore("projects")
      
      val promise = Promise[Option[js.Dynamic]]()
      
      val request = store.get(id)
      
      request.onsuccess = (event: dom.Event) => {
        val result = event.target.asInstanceOf[js.Dynamic].result
        promise.success(Option(result).map(_.asInstanceOf[js.Dynamic]))
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to get project"))
      }
      
      promise.future
    }
  }
  
  // List all projects
  def listProjects(): Future[List[js.Dynamic]] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("projects"), "readonly")
      val store = transaction.objectStore("projects")
      
      val promise = Promise[List[js.Dynamic]]()
      
      val request = store.getAll()
      
      request.onsuccess = (event: dom.Event) => {
        val results = event.target.asInstanceOf[js.Dynamic].result.asInstanceOf[js.Array[js.Dynamic]]
        promise.success(results.toList)
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to list projects"))
      }
      
      promise.future
    }
  }
  
  // Store a schema
  def storeSchema(schema: js.Dynamic): Future[Unit] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("schemas"), "readwrite")
      val store = transaction.objectStore("schemas")
      
      val promise = Promise[Unit]()
      
      val request = store.put(schema)
      
      request.onsuccess = (event: dom.Event) => {
        promise.success(())
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to store schema"))
      }
      
      promise.future
    }
  }
  
  // Get a schema by ID
  def getSchema(id: String): Future[Option[js.Dynamic]] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("schemas"), "readonly")
      val store = transaction.objectStore("schemas")
      
      val promise = Promise[Option[js.Dynamic]]()
      
      val request = store.get(id)
      
      request.onsuccess = (event: dom.Event) => {
        val result = event.target.asInstanceOf[js.Dynamic].result
        promise.success(Option(result).map(_.asInstanceOf[js.Dynamic]))
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to get schema"))
      }
      
      promise.future
    }
  }
  
  // List all schemas
  def listSchemas(): Future[List[js.Dynamic]] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("schemas"), "readonly")
      val store = transaction.objectStore("schemas")
      
      val promise = Promise[List[js.Dynamic]]()
      
      val request = store.getAll()
      
      request.onsuccess = (event: dom.Event) => {
        val results = event.target.asInstanceOf[js.Dynamic].result.asInstanceOf[js.Array[js.Dynamic]]
        promise.success(results.toList)
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to list schemas"))
      }
      
      promise.future
    }
  }
  
  // Store a definition
  def storeDefinition(definition: js.Dynamic): Future[Unit] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("definitions"), "readwrite")
      val store = transaction.objectStore("definitions")
      
      val promise = Promise[Unit]()
      
      val request = store.put(definition)
      
      request.onsuccess = (event: dom.Event) => {
        promise.success(())
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to store definition"))
      }
      
      promise.future
    }
  }
  
  // Get a definition by ID
  def getDefinition(id: String): Future[Option[js.Dynamic]] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("definitions"), "readonly")
      val store = transaction.objectStore("definitions")
      
      val promise = Promise[Option[js.Dynamic]]()
      
      val request = store.get(id)
      
      request.onsuccess = (event: dom.Event) => {
        val result = event.target.asInstanceOf[js.Dynamic].result
        promise.success(Option(result).map(_.asInstanceOf[js.Dynamic]))
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to get definition"))
      }
      
      promise.future
    }
  }
  
  // Store Automerge document
  def storeAutomergeDoc(docId: String, document: js.Dynamic): Future[Unit] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("automerge_docs"), "readwrite")
      val store = transaction.objectStore("automerge_docs")
      
      val promise = Promise[Unit]()
      
      val docData = js.Dynamic.literal(
        "id" -> docId,
        "document" -> document,
        "lastModified" -> new js.Date().toISOString()
      )
      
      val request = store.put(docData)
      
      request.onsuccess = (event: dom.Event) => {
        promise.success(())
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to store Automerge document"))
      }
      
      promise.future
    }
  }
  
  // Get Automerge document
  def getAutomergeDoc(docId: String): Future[Option[js.Dynamic]] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("automerge_docs"), "readonly")
      val store = transaction.objectStore("automerge_docs")
      
      val promise = Promise[Option[js.Dynamic]]()
      
      val request = store.get(docId)
      
      request.onsuccess = (event: dom.Event) => {
        val result = event.target.asInstanceOf[js.Dynamic].result
        promise.success(Option(result).map(_.asInstanceOf[js.Dynamic]))
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to get Automerge document"))
      }
      
      promise.future
    }
  }
  
  // Store a setting
  def storeSetting(key: String, value: js.Any): Future[Unit] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("settings"), "readwrite")
      val store = transaction.objectStore("settings")
      
      val promise = Promise[Unit]()
      
      val settingData = js.Dynamic.literal(
        "key" -> key,
        "value" -> value
      )
      
      val request = store.put(settingData)
      
      request.onsuccess = (event: dom.Event) => {
        promise.success(())
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to store setting"))
      }
      
      promise.future
    }
  }
  
  // Get a setting
  def getSetting(key: String): Future[Option[js.Any]] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("settings"), "readonly")
      val store = transaction.objectStore("settings")
      
      val promise = Promise[Option[js.Any]]()
      
      val request = store.get(key)
      
      request.onsuccess = (event: dom.Event) => {
        val result = event.target.asInstanceOf[js.Dynamic].result
        promise.success(Option(result).map(_.asInstanceOf[js.Dynamic].selectDynamic("value")))
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to get setting"))
      }
      
      promise.future
    }
  }
  
  // Delete a project
  def deleteProject(id: String): Future[Unit] = {
    ensureInitialized().flatMap { _ =>
      val transaction = db.transaction(js.Array("projects"), "readwrite")
      val store = transaction.objectStore("projects")
      
      val promise = Promise[Unit]()
      
      val request = store.delete(id)
      
      request.onsuccess = (event: dom.Event) => {
        promise.success(())
      }
      
      request.onerror = (event: dom.Event) => {
        promise.failure(new Exception("Failed to delete project"))
      }
      
      promise.future
    }
  }
  
  // Ensure database is initialized
  private def ensureInitialized(): Future[Unit] = {
    if (isInitialized) {
      Future.successful(())
    } else {
      initialize()
    }
  }
  
  // Close the database
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
  def storeSchema(schema: js.Dynamic): Future[Unit] = store.storeSchema(schema)
  def getSchema(id: String): Future[Option[js.Dynamic]] = store.getSchema(id)
  def listSchemas(): Future[List[js.Dynamic]] = store.listSchemas()
  def storeDefinition(definition: js.Dynamic): Future[Unit] = store.storeDefinition(definition)
  def getDefinition(id: String): Future[Option[js.Dynamic]] = store.getDefinition(id)
  def storeAutomergeDoc(docId: String, document: js.Dynamic): Future[Unit] = store.storeAutomergeDoc(docId, document)
  def getAutomergeDoc(docId: String): Future[Option[js.Dynamic]] = store.getAutomergeDoc(docId)
  def storeSetting(key: String, value: js.Any): Future[Unit] = store.storeSetting(key, value)
  def getSetting(key: String): Future[Option[js.Any]] = store.getSetting(key)
  def deleteProject(id: String): Future[Unit] = store.deleteProject(id)
  def close(): Unit = store.close()
}
