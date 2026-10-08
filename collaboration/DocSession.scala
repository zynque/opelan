package opelan.collaboration

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import opelan.collaboration.automerge.{Automerge, ChangeInfo}
import opelan.collaboration.backends.{SyncEnvelope, SyncTransport}

// Live sync for one document URL: an Automerge doc holding the document's
// outline text, pairwise sync state per peer, over a SyncTransport.
//
// Automerge's sync protocol is pairwise, so a sync state is kept per peer;
// peers announce themselves with a hello on join, and any envelope from an
// unknown peer starts a state lazily. Automerge docs are immutable —
// change/receive return new values — so `doc` is replaced on each update.
//
// `actorId`/`name` are the session's identity: every change the session
// writes carries `name` as its message, so the change graph is a shared,
// author-tagged history — identical on every peer once converged. The
// actor id must be unique per session (resumed tabs reusing a persisted
// actor would collide on (actor, seq)); `attach` injects it into both
// fresh and resumed docs.
//
// `lastText` is the outline text as of the last applied change from either
// side; `localText` ignores repeats so the DocChanged echo produced by
// loading a remote edit back into the pane terminates instead of looping.
//
//   onUpdate — the change graph grew: full history + current heads, for
//              the pane's shared-history view and the live doc text
//   onPersist — the Automerge bytes changed: stash them for later reload
//   onBranch  — a peer announced a forked draft at the given URL
class DocSession(
    val url: String,
    transport: SyncTransport,
    val actorId: String,
    val name: String,
    onUpdate: (Vector[ChangeInfo], Vector[String]) => Unit,
    onPersist: Uint8Array => Unit,
    onBranch: String => Unit = _ => ()) extends SessionUndo {

  protected var doc: js.Dynamic = null
  private var peers = Map.empty[String, js.Dynamic]
  private var lastText = ""
  private var lastCount = 0

  // Bring the session up on the current outline text. `saved` continues a
  // previously persisted Automerge doc (preserving change history); its
  // text is immediately overwritten with the live text, so the local view
  // wins on (re)entry.
  def attach(initialText: String, saved: Option[Uint8Array]): Unit = {
    doc = saved.flatMap(bytes =>
        try Option(Automerge.load(bytes, actorId))
        catch { case _: Throwable => None }) // corrupt bytes: start fresh
      .getOrElse(Automerge.create("", actorId))
    if (Automerge.textOf(doc) != initialText)
      doc = Automerge.setText(doc, initialText, name)
    lastText = initialText
    transport.subscribe(handleEnvelope)
    transport.send(SyncEnvelope(transport.peerId, "*", hello = true))
    emitUpdate()
  }

  // A local edit to the document: apply as a text diff, tag it with this
  // session's name, and sync out. The new change is pushed on the undo
  // stack (SessionUndo) so Ctrl+Z retracts it.
  def localText(text: String): Unit =
    if (doc != null && text != lastText) {
      org.scalajs.dom.console.log(
        s"[session] localText apply lines=${text.count(_ == '\n') + 1}")
      doc = Automerge.setText(doc, text, name)
      pushedLocalChange()
      publish()
    }
    else org.scalajs.dom.console.log(
      s"[session] localText skipped doc=${doc != null} same=${text == lastText}")

  // Shared by localText and undo/redo: every change the session writes
  // updates lastText, persists, reports history, and syncs to peers.
  protected def publish(): Unit = {
    lastText = Automerge.textOf(doc)
    org.scalajs.dom.console.log(
      s"[session] publish peers=${peers.size} changes=${Automerge.changeCount(doc)}")
    onPersist(Automerge.save(doc))
    emitUpdate()
    peers.keys.foreach(sendSync)
  }

  // Tell peers on this channel that this session forked its own draft at
  // `branchUrl` — their call whether to rejoin; the shared doc is untouched.
  def announce(branchUrl: String): Unit =
    transport.send(
      SyncEnvelope(transport.peerId, "*", branch = Some(branchUrl)))

  // The session's view of the shared text — for status display and tests.
  def text: String = lastText

  def close(): Unit = transport.close()

  // Report the change graph when it grew. Count is the gate: the graph is
  // append-only, and heads alone miss changes applied into the past.
  private def emitUpdate(): Unit = {
    val count = Automerge.changeCount(doc)
    if (count != lastCount) {
      lastCount = count
      onUpdate(Automerge.historyOf(doc), Automerge.headsOf(doc))
    }
  }

  private def handleEnvelope(env: SyncEnvelope): Unit =
    if (env.from != transport.peerId) {
      env.branch.foreach(onBranch)
      if (env.hello) {
        peers += env.from -> peers.getOrElse(env.from, Automerge.initSyncState())
        sendSync(env.from)
      } else if (env.to == transport.peerId || env.to == "*") {
        env.payload.foreach(p => receive(env.from, p))
      }
    }

  // A peer's sync message: apply and report if the graph grew. Persist
  // runs on every applied message (heads change even when the text
  // doesn't). Always answer — the protocol ping-pongs to quiescence.
  private def receive(from: String, msg: Uint8Array): Unit =
    if (doc != null) {
      val state = peers.getOrElse(from, Automerge.initSyncState())
      val (newDoc, newState) = Automerge.receiveSyncMessage(doc, state, msg)
      doc = newDoc
      peers += from -> newState
      lastText = Automerge.textOf(newDoc)
      onPersist(Automerge.save(doc))
      emitUpdate()
      sendSync(from)
    }

  private def sendSync(peer: String): Unit =
    if (doc != null) {
      val state = peers.getOrElse(peer, Automerge.initSyncState())
      val (newState, msg) = Automerge.generateSyncMessage(doc, state)
      peers += peer -> newState
      msg.foreach(b =>
        transport.send(SyncEnvelope(transport.peerId, peer, payload = Some(b))))
    }
}
