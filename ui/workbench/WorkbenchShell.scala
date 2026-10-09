package opelan.ui.workbench

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Random, Success, Failure}
import org.scalajs.dom
import opelan.collaboration.{DocSession, sessionIdentity}
import opelan.collaboration.backends.BroadcastTransport
import opelan.data.storage._
import opelan.ui.fp.{Handle, Runtime}
import opelan.ui.fp.demo.CounterList
import opelan.ui.typeddoc.{TypedDoc, TypedDocInput, TypedDocOutput}
import WorkbenchInput._
import WorkbenchOutput._

// The workbench's effect boundary: owns the mounted component, the pane
// handles, live DocSessions, and every output interpretation — storage,
// prompts, session wiring. Async results return as inputs, so the
// component stays a pure state machine.
//
// Session identity is per-tab (sessionStorage): a unique actor id keeps
// concurrent tabs from colliding on (actor, seq), and the generated name
// tags each change so shared history shows authors. Automerge state
// persists to the `automerge_docs` object store, so a reloading tab
// resumes sync rather than starting as a fresh actor. This is the
// operational (draft) layer — saving to the document store remains an
// explicit, curated act.
class WorkbenchShell {
  private var wb: Handle[WorkbenchInput, WorkbenchOutput] = null
  private var pane: Handle[TypedDocInput, TypedDocOutput] = null
  private var sessions = Map.empty[String, DocSession]
  private var attaching = Set.empty[String]
  private var initialized = false

  def initialize(containerId: String): Unit =
    if (!initialized) {
      val container = dom.document.getElementById(containerId)
      if (container == null)
        throw new IllegalArgumentException(
          s"Container element with id '$containerId' not found")
      WorkbenchShell.current = this
      wb = Runtime.mount(container, Workbench)
      wb.outputs.subscribe(interpret)
      wb.send(Boot)
      initialized = true
    }

  def cleanup(): Unit = {
    closeSessions()
    IndexedDBStore.close()
    if (WorkbenchShell.current == this) WorkbenchShell.current = null
    initialized = false
  }

  // The Managed slot mounts: each pane is its own runtime under the slot,
  // its outputs feeding back as workbench inputs.
  private[workbench] def mountDocPane(
      el: dom.Element, emit: WorkbenchInput => Unit): Unit = {
    val h = Runtime.mount(el, TypedDoc)
    h.outputs.subscribe(o => emit(FromPane(o)))
    pane = h
  }

  private[workbench] def mountComponentsPane(
      el: dom.Element, emit: WorkbenchInput => Unit): Unit =
    Runtime.mount(el, CounterList).outputs
      .subscribe(o => emit(StatusMsg(s"Components: $o")))

  private def send(in: WorkbenchInput): Unit =
    if (wb != null) wb.send(in)

  private def interpret(out: WorkbenchOutput): Unit = out match {
    case HydrateStorage =>
      IndexedDBStore.initialize().flatMap(_ => loadAll()).onComplete {
        case Success(store) => send(StorageLoaded(store))
        case Failure(e)     => send(StorageFailed(e.getMessage))
      }

    case PersistDoc(url, v, d) =>
      persist(url, v, d).onComplete {
        case Success(_) => send(StatusMsg(s"Saved $url@v$v"))
        case Failure(e) => send(StatusMsg(s"Save failed: ${e.getMessage}"))
      }

    case PromptName(save) =>
      val title = if (save) "Save document as:" else "Document name:"
      val name = dom.window.prompt(title, "untitled")
      if (name != null && name.nonEmpty)
        send(if (save) SaveAsNamed(name) else DocumentNamed(name))

    case PushPane(in) =>
      if (pane != null) pane.send(in)

    case AttachSync(url, seedText) => attachSync(url, seedText)
    case DetachSync                => closeSessions()

    case SessionText(url, text) =>
      sessions.get(url).foreach(_.localText(text))

    case SessionUndo(url) =>
      send(StatusMsg(
        sessions.get(url).map(_.undo()).getOrElse("Nothing to undo")))
    case SessionRedo(url) =>
      send(StatusMsg(
        sessions.get(url).map(_.redo()).getOrElse("Nothing to redo")))

    case RequestBranch(base, d) =>
      if (sessions.contains(base)) {
        val branchUrl =
          s"$base~${Random.alphanumeric.take(6).mkString.toLowerCase}"
        send(OpenBranch(base, branchUrl, d))
      }

    case AnnounceBranch(base, branchUrl) =>
      sessions.get(base).foreach(_.announce(branchUrl))
  }

  // Attach a session for `url`, resuming persisted Automerge state when
  // present. The callbacks turn session events into workbench inputs.
  private def attachSync(url: String, seedText: String): Unit =
    if (!sessions.contains(url) && !attaching(url)) {
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
          (entries, heads) =>
            sessions.get(url).foreach(s =>
              send(SessionUpdated(url, s.text, entries, heads, actor))),
          bytes => saveSyncDoc(url, bytes),
          branchOffered)
        sessions += url -> session
        session.attach(seedText, saved)
        send(SessionAttached(url, name))
      }
    }

  // A peer forked the shared line into a branch draft: offer to follow
  // rather than silently switching — the main doc is unaffected either
  // way.
  private def branchOffered(branchUrl: String): Unit =
    if (!sessions.contains(branchUrl) &&
        dom.window.confirm(
          s"A collaborator started a branch — rejoin at $branchUrl?"))
      send(JoinBranch(branchUrl))

  private def closeSessions(): Unit = {
    sessions.values.foreach(_.close())
    sessions = Map.empty
  }
}

object WorkbenchShell {
  // The live shell — set before mount so the view's Managed slots can
  // reach it (their mount callbacks delegate here).
  private[workbench] var current: WorkbenchShell = null

  private val shell = new WorkbenchShell

  def initialize(containerId: String): Unit = {
    current = shell
    shell.initialize(containerId)
  }

  def cleanup(): Unit = shell.cleanup()

  private[workbench] def docPaneMount(
      el: dom.Element, emit: WorkbenchInput => Unit): Unit =
    if (current != null) current.mountDocPane(el, emit)

  private[workbench] def componentsPaneMount(
      el: dom.Element, emit: WorkbenchInput => Unit): Unit =
    if (current != null) current.mountComponentsPane(el, emit)
}
