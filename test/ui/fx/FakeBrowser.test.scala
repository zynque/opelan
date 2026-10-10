package opelan.ui.fx

import scala.scalajs.js
import scala.collection.mutable
import org.scalajs.dom
import opelan.ui.editor.{FakeDom, FakeElement, FakeNode}

// A fake browser environment for Node tests: `window` carrying an
// in-memory indexedDB + sessionStorage, on top of the editor suite's
// FakeDom `document`. BroadcastChannel is real (Node ≥18 provides it),
// so two mounted components genuinely sync through the channel.
class FakeIDBDatabase extends js.Object {
  val stores = js.Dictionary[js.Dictionary[js.Dynamic]]()
  val objectStoreNames: js.Dynamic = js.Dynamic.literal(
    "contains" -> ((n: String) => stores.contains(n)))
  def createObjectStore(name: String, opts: js.Dynamic): Unit =
    stores.getOrElseUpdate(name, js.Dictionary.empty)
  def deleteObjectStore(name: String): Unit = { stores.remove(name); () }
  def transaction(names: js.Array[String], mode: String): FakeIDBTx =
    new FakeIDBTx(this)
  def close(): Unit = ()
}

class FakeIDBTx(db: FakeIDBDatabase) extends js.Object {
  def objectStore(name: String): FakeIDBStore =
    new FakeIDBStore(db.stores.getOrElseUpdate(name, js.Dictionary.empty))
}

class FakeIDBReq(getter: () => js.Dynamic) extends js.Object {
  var onsuccess: js.Function1[dom.Event, Any] = _
  var onerror: js.Function1[dom.Event, Any] = _
  def result: js.Dynamic = getter()
  js.timers.setTimeout(0) {
    if (onsuccess != null) onsuccess(null.asInstanceOf[dom.Event])
  }
}

class FakeIDBStore(records: js.Dictionary[js.Dynamic]) extends js.Object {
  private def keyOf(r: js.Dynamic): String =
    (if (!js.isUndefined(r.key)) r.key else r.id).asInstanceOf[String]
  def put(rec: js.Dynamic): FakeIDBReq =
    new FakeIDBReq(() => { records(keyOf(rec)) = rec; rec })
  def get(key: String): FakeIDBReq =
    new FakeIDBReq(() =>
      records.get(key).map(_.asInstanceOf[js.Dynamic])
        .getOrElse(null.asInstanceOf[js.Dynamic]))
  def getAll(): FakeIDBReq =
    new FakeIDBReq(() => js.Array(records.values.toSeq: _*)
      .asInstanceOf[js.Dynamic])
}

class FakeIDBOpen(db: FakeIDBDatabase) extends js.Object {
  var onsuccess: js.Function1[dom.Event, Any] = _
  var onerror: js.Function1[dom.Event, Any] = _
  var onupgradeneeded: js.Function1[dom.Event, Any] = _
  val result: js.Dynamic = db.asInstanceOf[js.Dynamic]
  js.timers.setTimeout(0) {
    val ev = js.Dynamic.literal("target" -> this.asInstanceOf[js.Dynamic])
      .asInstanceOf[dom.Event]
    if (onupgradeneeded != null) onupgradeneeded(ev)
    if (onsuccess != null) onsuccess(ev)
  }
}

// getItem always misses — every sessionIdentity() call generates a fresh
// actor, so two mounted components behave like two distinct tabs.
class FakeSessionStorage extends js.Object {
  def getItem(k: String): String = null.asInstanceOf[String]
  def setItem(k: String, v: String): Unit = ()
}

object FakeBrowser {
  // The indexedDB the store singleton is pointed at — shared across opens
  // so tests can seed it regardless of when initialization happens.
  val db = new FakeIDBDatabase

  def installWindow(): Unit =
    js.Dynamic.global.globalThis.window = js.Dynamic.literal(
      "indexedDB" -> js.Dynamic.literal(
        "open" -> ((name: String, version: Int) => new FakeIDBOpen(db))),
      "sessionStorage" -> new FakeSessionStorage)

  def container(): dom.Element = {
    if (!FakeDom.installed) FakeDom.install()
    val c = new FakeElement("div")
    FakeDom.registerRoot(c.asInstanceOf[dom.Node])
    c.asInstanceOf[dom.Element]
  }

  def hasText(root: dom.Node, s: String): Boolean =
    (root: Any) match {
      case e: FakeElement => e.children.exists(c => hasText(c, s))
      case n: FakeNode  => Option(n.textContent).exists(_.contains(s))
      case _            => false
    }
}
