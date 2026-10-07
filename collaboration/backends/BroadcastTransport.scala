package opelan.collaboration.backends

import scala.scalajs.js
import scala.scalajs.js.JSConverters.JSRichOption
import org.scalajs.dom
import scala.scalajs.js.typedarray.Uint8Array

// BroadcastChannel transport: every tab of this app on the same browser
// is a peer — the "same-machine P2P" case, needing no server. Messages
// are structured-cloned (Uint8Array payloads travel fine). The channel is
// scoped per document URL so each synced document is its own session.
class BroadcastTransport(documentUrl: String) extends SyncTransport {

  val peerId: String = s"tab-${randomId()}"
  private val channel = new dom.BroadcastChannel(s"opelan-doc:$documentUrl")

  def send(envelope: SyncEnvelope): Unit =
    channel.postMessage(js.Dynamic.literal(
      "from" -> envelope.from,
      "to" -> envelope.to,
      "hello" -> envelope.hello,
      "payload" -> envelope.payload.orUndefined,
      "branch" -> envelope.branch.orUndefined))

  def subscribe(handler: SyncEnvelope => Unit): Unit =
    channel.onmessage = (e: dom.MessageEvent) =>
      Option(e.data).foreach { data =>
        val d = data.asInstanceOf[js.Dynamic]
        handler(SyncEnvelope(
          d.from.asInstanceOf[String],
          d.to.asInstanceOf[String],
          d.hello.asInstanceOf[Boolean],
          d.payload.asInstanceOf[js.UndefOr[Uint8Array]].toOption,
          d.branch.asInstanceOf[js.UndefOr[String]].toOption))
      }

  def close(): Unit = channel.close()

  private def randomId(): String =
    scala.util.Random.alphanumeric.take(8).mkString
}
