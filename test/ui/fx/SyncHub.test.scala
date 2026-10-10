package opelan.ui.fx

import scala.scalajs.js
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import opelan.ui.fp.{Child, Component, Handle, Runtime, Update, View}
import opelan.ui.fp.Dsl._
import SyncMsg._
import SyncEv._

enum ParentIn { case Sync(url: String, text: String); case FromHub(ev: SyncEv) }
import ParentIn._

// SyncHub as a reconcile-managed child — the same child() plumbing the
// workbench uses: props = desired map, outputs routed to a parent input.
private object Parent extends Component[ParentIn, SyncEv] {
  type State = Map[String, String]
  def init: State = Map.empty
  def update(s: State, in: ParentIn): Update[State, SyncEv, ParentIn] =
    in match {
      case Sync(u, t)    => Update(s + (u -> t))
      case FromHub(ev)   => Update(s, Vector(ev))
    }
  def view(s: State): View[ParentIn] = el("div")(mount)
  override def children(s: State) = Vector(
    Child("sync", SyncHub, SyncMsg.Props(SyncProps(s)), FromHub(_)))
}

// Drives SyncHub through the real Runtime + real BroadcastChannel (Node
// provides it; two "tabs" in one process share the channel), with
// FakeBrowser's in-memory window.indexedDB for session persistence.
// Every test uses a fresh channel url and disposes its mounts so no
// session from an earlier test can cross-talk.
class SyncHubSuite extends munit.FunSuite {

  private val handles = mutable.ListBuffer.empty[Handle[?, ?]]

  override def afterEach(context: AfterEach): Unit = {
    handles.foreach(_.dispose())
    handles.clear()
  }

  private def mount[I, O](c: Component[I, O]): Handle[I, O] = {
    val h = Runtime.mount(FakeBrowser.container(), c)
    handles += h
    h
  }

  private def tick(): Future[Unit] = {
    val p = Promise[Unit]()
    js.timers.setTimeout(20)(p.success(()))
    p.future
  }

  private def until(cond: => Boolean, tries: Int = 50): Future[Unit] =
    if (cond) Future.successful(())
    else if (tries <= 0) Future.failed(new Exception("timed out"))
    else tick().flatMap(_ => until(cond, tries - 1))

  test("props attach a session and emit Attached") {
    FakeBrowser.installWindow()
    val out = mutable.ListBuffer.empty[SyncEv]
    val h = mount(SyncHub)
    h.outputs.subscribe(out += _)
    h.send(Props(SyncProps(Map("attach" -> "doc text"))))
    until(out.exists(_.isInstanceOf[Attached])).map { _ =>
      assert(out.exists { case Attached("attach", _) => true; case _ => false })
    }
  }

  test("props delivered through reconcile drive a session; two parents converge") {
    FakeBrowser.installWindow()
    val out1 = mutable.ListBuffer.empty[SyncEv]
    val out2 = mutable.ListBuffer.empty[SyncEv]
    val h1 = mount(Parent)
    h1.outputs.subscribe(out1 += _)
    val h2 = mount(Parent)
    h2.outputs.subscribe(out2 += _)

    // Staggered attach, like real tabs: the second resumes the first's
    // persisted seed bytes, so no concurrent seed write races the sync.
    val seed = "root \"seed\"\n"
    h1.send(Sync("parented", seed))
    until(out1.exists(_.isInstanceOf[Attached]))
      .flatMap(_ => tick())
      .flatMap { _ =>
        h2.send(Sync("parented", seed))
        until(
          out1.exists(_.isInstanceOf[Attached]) &&
          out2.exists(_.isInstanceOf[Attached]))
      }.flatMap { _ =>
      h1.send(Sync("parented", "root \"changed\"\n"))
      until(out2.exists {
        case Grew("parented", t, _, _, _) => t.contains("changed")
        case _ => false
      }).recover { case _ =>
        fail(s"grew stalled: out1=$out1 out2=$out2")
      }
    }.map(_ => assert(true))
  }

  test("two hubs converge: a text change on one reaches the other") {
    FakeBrowser.installWindow()
    val out1 = mutable.ListBuffer.empty[SyncEv]
    val out2 = mutable.ListBuffer.empty[SyncEv]
    val h1 = mount(SyncHub)
    h1.outputs.subscribe(out1 += _)
    val h2 = mount(SyncHub)
    h2.outputs.subscribe(out2 += _)

    val seed = "root \"a\"\n"
    h1.send(Props(SyncProps(Map("direct" -> seed))))
    until(out1.exists(_.isInstanceOf[Attached]))
      .flatMap(_ => tick())
      .flatMap { _ =>
        h2.send(Props(SyncProps(Map("direct" -> seed))))
        until(
          out1.exists(_.isInstanceOf[Attached]) &&
          out2.exists(_.isInstanceOf[Attached]))
      }.flatMap { _ =>
      // tab 1 edits; tab 2's session should grow and report the new text
      h1.send(Props(SyncProps(Map("direct" -> "root \"a\"\nroot \"b\"\n"))))
      until(out2.exists {
        case Grew("direct", t, _, _, _) => t.contains("\"b\"")
        case _ => false
      })
    }.map(_ => assert(true))
  }
}
