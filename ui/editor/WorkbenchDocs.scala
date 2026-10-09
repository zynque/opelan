package opelan.ui.editor

import org.scalajs.dom
import opelan.data.storage.{IndexedDBStore, loadAll, persist}
import opelan.foundation.document._
import opelan.ui.typeddoc.{TypedDocInput, TypedDocOutput}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

// Document-workspace plumbing for the workbench shell: hydrates the
// versioned Store from IndexedDB, renders the documents sidebar, and
// implements open / save / follow-ref by deciding in the pure Workspace
// value and pushing Load inputs into the TypedDoc pane.
trait WorkbenchDocs {
  // Provided by the shell.
  protected def documentEditor: Option[opelan.ui.fp.Handle[TypedDocInput, TypedDocOutput]]
  protected def updateStatus(message: String): Unit

  protected var workspace = Workspace.empty
  // Set while a save is waiting for the pane to report its live document.
  private var pendingSave = false

  // Hydrate the store, render the sidebar, and reopen the first stored
  // document (restoring the workspace across sessions).
  protected def initializeDocuments(): Unit = {
    IndexedDBStore.initialize().flatMap(_ => loadAll()).onComplete {
      case Success(store) =>
        workspace = workspace.copy(store = store)
        updateStatus(s"Storage initialized — ${store.urls.length} document(s)")
        renderDocumentList()
        store.urls.headOption.foreach(url =>
          store.head(url).foreach { case (v, d) => openDocument(url, v, d) })
      case Failure(e) =>
        updateStatus(s"Storage initialization failed: ${e.getMessage}")
    }
  }

  // Outputs coming up from the TypedDoc pane.
  protected def docOutput(out: TypedDocOutput): Unit =
    out match {
      case TypedDocOutput.DocChanged(d) =>
        workspace = workspace.changed(d)
        if (pendingSave) {
          pendingSave = false
          saveDocument(d)
        }
        documentEdited(d)
      // History navigation: the mirror tracks what the user sees (saving
      // checkpoints the viewed version) but no session ever hears of it.
      case TypedDocOutput.DocViewed(d) =>
        workspace = workspace.changed(d)
        if (pendingSave) {
          pendingSave = false
          saveDocument(d)
        }
      case TypedDocOutput.UndoRequested      => undoRequested()
      case TypedDocOutput.RedoRequested      => redoRequested()
      case TypedDocOutput.BranchRequested(d) => branchRequested(d)
      case TypedDocOutput.FollowRef(ref)     => followRef(ref)
      case TypedDocOutput.Status(msg)        => updateStatus(msg)
    }

  protected def renderDocumentList(): Unit = {
    val listEl = dom.document.getElementById("document-list")
    listEl.innerHTML = ""

    val entries = workspace.entries
    if (entries.isEmpty) {
      val empty = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
      empty.textContent = "No documents"
      empty.style.padding = "5px"
      empty.style.color = "#666"
      listEl.appendChild(empty)
    } else {
      entries.foreach { case (url, version, label) =>
        val item = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
        item.textContent = s"$label  v$version"
        item.title = url
        item.style.padding = "5px"
        item.style.cursor = "pointer"
        item.style.borderBottom = "1px solid #eee"
        if (workspace.openUrl.contains(url)) item.style.fontWeight = "bold"
        item.onclick = (_: dom.MouseEvent) => selectDocument(url)
        listEl.appendChild(item)
      }
    }
  }

  protected def selectDocument(url: String): Unit =
    workspace.store.head(url) match {
      case Some((v, d)) => openDocument(url, v, d)
      case None         => updateStatus(s"No versions of $url")
    }

  protected def createNewDocument(): Unit = {
    val name = dom.window.prompt("Document name:", "untitled")
    if (name != null && name.nonEmpty) {
      val url = workspace.freshUrl(name)
      val doc: Document[NodeData] = beginDocument(NodeData.StringData(name))
      val (ws, written) = workspace.save(url, doc)
      workspace = ws
      written.foreach { v =>
        persist(url, v, doc)
        openDocument(url, v, doc)
        updateStatus(s"Created $url@v$v")
      }
    }
  }

  // Push a stored document (at a specific version) into the pane.
  protected def openDocument(
      url: String,
      version: Int,
      d: Document[NodeData],
      select: Option[Int] = None): Unit = {
    workspace = workspace.open(url, version, d)
    documentEditor.foreach(_.send(TypedDocInput.Load(d, select)))
    updateCurrentDocLabel()
    renderDocumentList()
    documentOpened(url)
  }

  // Hooks for WorkbenchSync: a document became current / the open document
  // was edited. Default no-ops keep the shell usable without sync.
  protected def documentOpened(url: String): Unit = ()
  protected def documentEdited(d: Document[NodeData]): Unit = ()
  // Synced-head undo/redo and edit-on-checkout fork requests; WorkbenchSync
  // overrides these, unsynced shells report nothing to undo.
  protected def undoRequested(): Unit =
    updateStatus("Nothing to undo")
  protected def redoRequested(): Unit =
    updateStatus("Nothing to redo")
  protected def branchRequested(d: Document[NodeData]): Unit = ()

  // The pane owns the live document; pull it via RequestDoc and save when
  // the DocChanged reply arrives (FIFO dispatch keeps the order correct).
  protected def saveCurrentDocument(): Unit =
    documentEditor match {
      case Some(h) if !pendingSave =>
        pendingSave = true
        h.send(TypedDocInput.RequestDoc)
      case _ => ()
    }

  private def saveDocument(d: Document[NodeData]): Unit =
    workspace.openUrl match {
      case Some(url) => doSave(url, d)
      case None =>
        val name = dom.window.prompt("Save document as:", "untitled")
        if (name != null && name.nonEmpty) doSave(workspace.freshUrl(name), d)
    }

  private def doSave(url: String, d: Document[NodeData]): Unit = {
    val (ws, written) = workspace.save(url, d)
    workspace = ws
    written match {
      case Some(v) =>
        persist(url, v, d).onComplete {
          case Success(_) => updateStatus(s"Saved $url@v$v")
          case Failure(e) => updateStatus(s"Save failed: ${e.getMessage}")
        }
        updateCurrentDocLabel()
        renderDocumentList()
      case None =>
        updateStatus(s"No changes to save — $url")
    }
  }

  // The pane asked to follow a ref (Ctrl+Enter on an ExternalNodeRef node):
  // resolve it against the store and open the pinned version at the node.
  private def followRef(ref: ExternalNodeReference): Unit =
    workspace.follow(ref) match {
      case Some((url, v, d, nodeId)) =>
        openDocument(url, v, d, Some(nodeId))
        updateStatus(s"Followed ref → $url@v$v#$nodeId")
      case None =>
        updateStatus(
          s"Unresolved ref: ${ref.documentUrl}@${ref.documentVersionId} (not in document store)")
    }

  private def updateCurrentDocLabel(): Unit = {
    val text = (workspace.openUrl, workspace.openVersion, workspace.doc) match {
      case (Some(url), Some(v), Some(d)) =>
        s"${Store.labelOf(d).getOrElse(url)} — $url@v$v"
      case _ => "unsaved"
    }
    dom.document.getElementById("current-doc-name").textContent = text
  }
}
