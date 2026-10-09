package opelan.collaboration.automerge

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Uint8Array

@js.native @JSImport("@automerge/automerge", JSImport.Namespace)
private object AmJs extends js.Object

// Thin typed wrapper over @automerge/automerge 3.x (fullfat bundle — the
// wasm is embedded, so no explicit initializeWasm is needed). The
// workbench's sync documents have a single property, `text`, holding a
// document's outline text; in 3.x every string is a mergeable text
// sequence, so concurrent edits merge at the character level.
//
// Doc, SyncState and Cursor are opaque handles: the underlying js.Dynamic
// never leaves this file, so Automerge's dynamically-typed, procedural
// API is confined to the facade. Docs are immutable values — every
// change produces a new Doc.
object Automerge {

  // An Automerge document. `>: Null` so a session field can start
  // unattached; nothing outside this facade can read or mutate it.
  opaque type Doc >: Null <: AnyRef = js.Dynamic
  // Per-peer sync protocol state.
  opaque type SyncState = js.Dynamic
  // A stable position marker into a doc's `text` — resolves across
  // views of the doc, moving past concurrent insertions.
  opaque type Cursor = js.Dynamic

  private val am = AmJs.asInstanceOf[js.Dynamic]

  def create(text: String, actorId: String): Doc =
    am.applyDynamic("from")(js.Dynamic.literal("text" -> text), actorId)
      .asInstanceOf[js.Dynamic]

  def actorIdOf(doc: Doc): String =
    am.getActorId(doc).asInstanceOf[String]

  def textOf(doc: Doc): String = doc.text.toString.asInstanceOf[String]

  // Apply a new full text: updateText diffs old->new and splices the
  // minimal change, so concurrent remote edits merge instead of clobber.
  // The message is stored on the change and surfaces in history.
  def setText(doc: Doc, text: String, message: String): Doc =
    am.change(doc, js.Dynamic.literal("message" -> message),
      js.Any.fromFunction1 { (d: js.Dynamic) =>
        am.updateText(d, js.Array("text"), text)
        ()
      }).asInstanceOf[js.Dynamic]

  def save(doc: Doc): Uint8Array =
    am.save(doc).asInstanceOf[Uint8Array]

  // Resuming saved bytes with a fresh actor id: two sessions loading the
  // same bytes must not share an actor, or their changes collide on
  // (actor, seq).
  def load(bytes: Uint8Array, actorId: String): Doc =
    am.load(bytes, actorId).asInstanceOf[js.Dynamic]

  // The doc's change graph in dependency order — every change with the
  // document text right after it. Identical on every peer once converged,
  // so it doubles as the shared history projection.
  def historyOf(doc: Doc): Vector[ChangeInfo] =
    am.getHistory(doc).asInstanceOf[js.Array[js.Dynamic]].toVector.map { h =>
      val c = h.change
      ChangeInfo(
        hash = c.hash.asInstanceOf[String],
        actor = c.actor.asInstanceOf[String],
        seq = c.seq.asInstanceOf[Int],
        time = c.time.asInstanceOf[Double],
        message = Option(c.message).map(_.toString),
        deps = c.deps.asInstanceOf[js.Array[String]].toVector,
        snapshotText = h.snapshot.text.toString)
    }

  // Current frontier of the change graph (hashes with no dependents).
  def headsOf(doc: Doc): Vector[String] =
    am.getHeads(doc).asInstanceOf[js.Array[String]].toVector

  // The document as it was at a point in history — heads are change
  // hashes. Used for revert computation and history inspection.
  def view(doc: Doc, heads: Vector[String]): Doc =
    am.view(doc, js.Array(heads: _*)).asInstanceOf[js.Dynamic]

  def textAtHeads(doc: Doc, heads: Vector[String]): String =
    if (heads.isEmpty) "" else textOf(view(doc, heads))

  // A positional splice on the `text` field — undo/redo apply an inverse
  // splice at the current frontier rather than rewriting whole strings.
  def spliceText(
      doc: Doc, pos: Int, del: Int, ins: String,
      message: String): Doc =
    am.change(doc, js.Dynamic.literal("message" -> message),
      js.Any.fromFunction1 { (d: js.Dynamic) =>
        am.splice(d, js.Array("text"), pos, del, ins)
        ()
      }).asInstanceOf[js.Dynamic]

  // Stable position markers: a cursor created on one view of the doc
  // resolves to the corresponding index in another, moving past any
  // concurrent insertions — how an old change's span maps to the
  // current frontier.
  def cursorAt(doc: Doc, index: Int): Cursor =
    am.getCursor(doc, js.Array("text"), index).asInstanceOf[js.Dynamic]

  def cursorPosition(doc: Doc, cursor: Cursor): Int =
    am.getCursorPosition(doc, js.Array("text"), cursor).asInstanceOf[Int]

  // Cheap growth check — much lighter than building historyOf.
  def changeCount(doc: Doc): Int =
    am.getAllChanges(doc).asInstanceOf[js.Array[js.Any]].length

  def initSyncState(): SyncState =
    am.initSyncState().asInstanceOf[js.Dynamic]

  // (newSyncState, message for the peer if any)
  def generateSyncMessage(
      doc: Doc, state: SyncState): (SyncState, Option[Uint8Array]) = {
    val r = am.generateSyncMessage(doc, state).asInstanceOf[js.Array[js.Any]]
    (r(0).asInstanceOf[js.Dynamic],
      Option(r(1)).map(_.asInstanceOf[Uint8Array]))
  }

  // (newDoc, newSyncState)
  def receiveSyncMessage(
      doc: Doc, state: SyncState,
      msg: Uint8Array): (Doc, SyncState) = {
    val r = am.receiveSyncMessage(doc, state, msg).asInstanceOf[js.Array[js.Any]]
    (r(0).asInstanceOf[js.Dynamic], r(1).asInstanceOf[js.Dynamic])
  }
}
