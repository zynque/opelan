package opelan.collaboration.automerge

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Uint8Array

@js.native @JSImport("@automerge/automerge", JSImport.Namespace)
private object AmJs extends js.Object

// Thin wrapper over @automerge/automerge 3.x (fullfat bundle — the wasm
// is embedded, so no explicit initializeWasm is needed). The workbench's
// sync documents have a single property, `text`, holding a document's
// outline text; in 3.x every string is a mergeable text sequence, so
// concurrent edits merge at the character level.
object Automerge {

  private val am = AmJs.asInstanceOf[js.Dynamic]

  def create(text: String): js.Dynamic =
    am.applyDynamic("from")(js.Dynamic.literal("text" -> text))
      .asInstanceOf[js.Dynamic]

  def textOf(doc: js.Dynamic): String = doc.text.toString.asInstanceOf[String]

  // Apply a new full text: updateText diffs old->new and splices the
  // minimal change, so concurrent remote edits merge instead of clobber.
  def setText(doc: js.Dynamic, text: String): js.Dynamic =
    am.change(doc, js.Any.fromFunction1 { (d: js.Dynamic) =>
      am.updateText(d, js.Array("text"), text)
      ()
    }).asInstanceOf[js.Dynamic]

  def save(doc: js.Dynamic): Uint8Array =
    am.save(doc).asInstanceOf[Uint8Array]

  def load(bytes: Uint8Array): js.Dynamic =
    am.load(bytes).asInstanceOf[js.Dynamic]

  def initSyncState(): js.Dynamic =
    am.initSyncState().asInstanceOf[js.Dynamic]

  // (newSyncState, message for the peer if any)
  def generateSyncMessage(
      doc: js.Dynamic, state: js.Dynamic): (js.Dynamic, Option[Uint8Array]) = {
    val r = am.generateSyncMessage(doc, state).asInstanceOf[js.Array[js.Any]]
    (r(0).asInstanceOf[js.Dynamic],
      Option(r(1)).map(_.asInstanceOf[Uint8Array]))
  }

  // (newDoc, newSyncState)
  def receiveSyncMessage(
      doc: js.Dynamic, state: js.Dynamic,
      msg: Uint8Array): (js.Dynamic, js.Dynamic) = {
    val r = am.receiveSyncMessage(doc, state, msg).asInstanceOf[js.Array[js.Any]]
    (r(0).asInstanceOf[js.Dynamic], r(1).asInstanceOf[js.Dynamic])
  }
}
