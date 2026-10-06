package opelan.collaboration

import opelan.collaboration.automerge.ChangeInfo
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

  private def actor(c: String): String = c * 64

  test("a local edit propagates to a joined peer") {
    val (ta, tb) = linkedPair()
    var remote = ""
    var b: DocSession = null
    val a = new DocSession("u", ta, actor("a"), "amber-fox", (_, _) => (), _ => ())
    b = new DocSession("u", tb, actor("b"), "calm-otter",
      (_, _) => remote = b.text, _ => ())
    a.attach("v0", None)
    b.attach("v0", None)
    a.localText("v1")
    assertEquals(remote, "v1")
    assertEquals(b.text, "v1")
  }

  test("a late joiner catches up via the hello exchange") {
    val (ta, tb) = linkedPair()
    var remote = ""
    var b: DocSession = null
    val a = new DocSession("u", ta, actor("a"), "amber-fox", (_, _) => (), _ => ())
    a.attach("base", None)
    a.localText("grown")
    // b attaches with a stale view, then learns a's state through sync.
    // The two independently created docs race on `text` — convergence is
    // what matters, not which side won the root property's LWW.
    b = new DocSession("u", tb, actor("b"), "calm-otter",
      (_, _) => remote = b.text, _ => ())
    b.attach("base", None)
    assertEquals(remote, a.text)
    assertEquals(b.text, a.text)
  }

  test("concurrent edits on a shared base merge instead of clobbering") {
    // Char-level merge requires a common ancestor doc: peers either load
    // the same persisted bytes (as here) or get state via sync before
    // editing. Two independently created docs racing on `text` resolve
    // last-writer-wins on the property — no shared history to diff.
    val seed = automerge.Automerge.save(automerge.Automerge.create("AAAA", actor("5")))
    val ta = new TestTransport("A")
    val tb = new TestTransport("B")
    val a = new DocSession("u", ta, actor("a"), "amber-fox", (_, _) => (), _ => ())
    val b = new DocSession("u", tb, actor("b"), "calm-otter", (_, _) => (), _ => ())
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

  test("sessions resumed from the same bytes keep distinct actors") {
    // Two tabs sharing the persisted Automerge doc must not share an
    // actor id — concurrent changes would collide on (actor, seq).
    val seed = automerge.Automerge.save(automerge.Automerge.create("x", actor("5")))
    val ta = new TestTransport("A")
    val tb = new TestTransport("B")
    val a = new DocSession("u", ta, actor("a"), "amber-fox", (_, _) => (), _ => ())
    val b = new DocSession("u", tb, actor("b"), "calm-otter", (_, _) => (), _ => ())
    a.attach("x", Some(seed))
    b.attach("x", Some(seed))
    assertNotEquals(a.actorId, b.actorId)
  }

  test("history entries are tagged with the writer's name and actor") {
    val (ta, tb) = linkedPair()
    var hist = Vector.empty[ChangeInfo]
    val a = new DocSession("u", ta, actor("a"), "amber-fox", (_, _) => (), _ => ())
    val b = new DocSession("u", tb, actor("b"), "calm-otter", (h, _) => hist = h, _ => ())
    a.attach("v0", None)
    b.attach("v0", None)
    a.localText("v1")
    val mine = hist.filter(_.actor == actor("a"))
    assert(mine.nonEmpty)
    assertEquals(mine.last.message, Some("amber-fox"))
    assertEquals(mine.last.snapshotText, "v1")
  }

  test("editing on divergent heads produces a two-dep merge change") {
    val seed = automerge.Automerge.save(automerge.Automerge.create("AAAA", actor("5")))
    val ta = new TestTransport("A")
    val tb = new TestTransport("B")
    var hist = Vector.empty[ChangeInfo]
    val a = new DocSession("u", ta, actor("a"), "amber-fox", (h, _) => hist = h, _ => ())
    val b = new DocSession("u", tb, actor("b"), "calm-otter", (_, _) => (), _ => ())
    a.attach("AAAA", Some(seed))
    b.attach("AAAA", Some(seed))
    a.localText("AAAAa")
    b.localText("AAAAb")
    // Converge: two heads now. The next change depends on both.
    ta.others = List(tb)
    tb.others = List(ta)
    ta.send(SyncEnvelope("A", "*", hello = true))
    a.localText("merged")
    assertEquals(hist.last.deps.length, 2)
  }
}
