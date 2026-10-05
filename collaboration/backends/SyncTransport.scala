package opelan.collaboration.backends

import scala.scalajs.js.typedarray.Uint8Array

// One message between peers in a document's sync channel. `hello` is a
// payload-less announcement sent on join so existing peers start the
// Automerge sync exchange; otherwise `payload` carries a sync message.
// `to == "*"` broadcasts; point-to-point envelopes are filtered by
// transport.
case class SyncEnvelope(
    from: String,
    to: String,
    hello: Boolean = false,
    payload: Option[Uint8Array] = None)

// How DocSession exchanges sync messages with peers. Implementations
// (BroadcastChannel today; WebSocket/WebRTC signaling later) deliver
// envelopes addressed to `peerId` or "*".
trait SyncTransport {
  def peerId: String
  def send(envelope: SyncEnvelope): Unit
  def subscribe(handler: SyncEnvelope => Unit): Unit
  def close(): Unit
}
