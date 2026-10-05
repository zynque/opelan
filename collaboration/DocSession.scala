package opelan.collaboration

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import opelan.collaboration.automerge.Automerge
import opelan.collaboration.backends.{SyncEnvelope, SyncTransport}

// Live sync for one document URL: an Automerge doc holding the document's
// outline text, pairwise sync state per peer, over a SyncTransport.
//
// Automerge's sync protocol is pairwise, so a sync state is kept per peer;
// peers announce themselves with a hello on join, and any envelope from an
// unknown peer starts a state lazily. Automerge docs are immutable —
// change/receive return new values — so `doc` is replaced on each update.
//
// `lastText` is the outline text as of the last applied change from either
// side; `localText` ignores repeats so the DocChanged echo produced by
// loading a remote edit back into the pane terminates instead of looping.
//
//   onRemoteText — a peer's changes produced new text: push it to the pane
//   onPersist    — the Automerge bytes changed: stash them for later reload
class DocSession(
    val url: String,
    transport: SyncTransport,
    onRemoteText: String => Unit,
    onPersist: Uint8Array => Unit) {

  private var doc: js.Dynamic = null
  private var peers = Map.empty[String, js.Dynamic]
  private var lastText = ""

  // Bring the session up on the current outline text. `saved` continues a
  // previously persisted Automerge doc (preserving actor history); its text
  // is immediately overwritten with the live text, so the local view wins
  // on (re)entry.
  def attach(initialText: String, saved: Option[Uint8Array]): Unit = {
    doc = saved.flatMap(bytes =>
        try Option(Automerge.load(bytes))
        catch { case _: Throwable => None }) // corrupt bytes: start fresh
      .getOrElse(Automerge.create(""))
    if (Automerge.textOf(doc) != initialText) doc = Automerge.setText(doc, initialText)
    lastText = initialText
    transport.subscribe(handleEnvelope)
    transport.send(SyncEnvelope(transport.peerId, "*", hello = true))
  }

  // A local edit to the document: apply as a text diff and sync out.
  def localText(text: String): Unit =
    if (doc != null && text != lastText) {
      doc = Automerge.setText(doc, text)
      lastText = text
      onPersist(Automerge.save(doc))
      peers.keys.foreach(sendSync)
    }

  // The session's view of the shared text — for status display and tests.
  def text: String = lastText

  def close(): Unit = transport.close()

  private def handleEnvelope(env: SyncEnvelope): Unit =
    if (env.from != transport.peerId) {
      if (env.hello) {
        peers += env.from -> peers.getOrElse(env.from, Automerge.initSyncState())
        sendSync(env.from)
      } else if (env.to == transport.peerId || env.to == "*") {
        env.payload.foreach(p => receive(env.from, p))
      }
    }

  // A peer's sync message: apply, and if the text changed, report it.
  // Persist runs on every applied message (heads change even when the
  // text doesn't). Always answer — the protocol ping-pongs to quiescence.
  private def receive(from: String, msg: Uint8Array): Unit =
    if (doc != null) {
      val state = peers.getOrElse(from, Automerge.initSyncState())
      val (newDoc, newState) = Automerge.receiveSyncMessage(doc, state, msg)
      doc = newDoc
      peers += from -> newState
      val text = Automerge.textOf(newDoc)
      if (text != lastText) {
        lastText = text
        onRemoteText(text)
      }
      onPersist(Automerge.save(doc))
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
