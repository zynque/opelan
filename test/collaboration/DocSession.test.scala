package opelan.collaboration

import opelan.collaboration.backends.{SyncEnvelope, SyncTransport}

// In-memory transport wiring peers directly — no BroadcastChannel needed
// under Node. `others` delivers a sent envelope to every linked peer's
// subscribed handler, so send/receive runs synchronously to quiescence.
class TestTransport(val peerId: String) extends SyncTransport {
  var handler: SyncEnvelope => Unit = _ => ()
  var others: List[TestTransport] = Nil
  def send(e: SyncEnvelope): Unit = others.foreach(_.handler(e))
  def subscribe(h: SyncEnvelope => Unit): Unit = handler = h
  def close(): Unit = ()
}

class DocSessionSuite extends munit.FunSuite {

  private def linkedPair(): (TestTransport, TestTransport) = {
    val (a, b) = (new TestTransport("A"), new TestTransport("B"))
    a.others = List(b)
    b.others = List(a)
    (a, b)
  }

  test("a local edit propagates to a joined peer") {
    val (ta, tb) = linkedPair()
    var remote = ""
    val a = new DocSession("u", ta, _ => (), _ => ())
    val b = new DocSession("u", tb, t => remote = t, _ => ())
    a.attach("v0", None)
    b.attach("v0", None)
    a.localText("v1")
    assertEquals(remote, "v1")
    assertEquals(b.text, "v1")
  }

  test("a late joiner catches up via the hello exchange") {
    val (ta, tb) = linkedPair()
    var remote = ""
    val a = new DocSession("u", ta, _ => (), _ => ())
    a.attach("base", None)
    a.localText("grown")
    // b attaches with a stale view, then learns a's state through sync
    val b = new DocSession("u", tb, t => remote = t, _ => ())
    b.attach("base", None)
    assertEquals(remote, "grown")
  }

  test("concurrent edits on a shared base merge instead of clobbering") {
    // Char-level merge requires a common ancestor doc: peers either load
    // the same persisted bytes (as here) or get state via sync before
    // editing. Two independently created docs racing on `text` resolve
    // last-writer-wins on the property — no shared history to diff.
    val seed = automerge.Automerge.save(automerge.Automerge.create("AAAA"))
    val ta = new TestTransport("A")
    val tb = new TestTransport("B")
    val a = new DocSession("u", ta, _ => (), _ => ())
    val b = new DocSession("u", tb, _ => (), _ => ())
    // No link during attach+edits: the two sessions diverge for real.
    a.attach("AAAA", Some(seed))
    b.attach("AAAA", Some(seed))
    a.localText("AAAAa")
    b.localText("AAAAb")
    // Link and re-announce; both converge to the same merged text.
    ta.others = List(tb)
    tb.others = List(ta)
    ta.send(SyncEnvelope("A", "*", hello = true))
    assertEquals(a.text, b.text)
    assert(a.text.startsWith("AAAA"))
    assert(a.text.contains("a"))
    assert(a.text.contains("b"))
  }
}
