package opelan.ui.workbench

import scala.scalajs.js
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import opelan.foundation.document._
import opelan.ui.fx.FakeBrowser
import opelan.ui.fp.{Component, Runtime, Update, View}
import opelan.ui.typeddoc.{TypedDocInput, TypedDocOutput}
import opelan.ui.workbench.WorkbenchInput._

// A CodeMirror-free stand-in for the TypedDoc pane: accepts Pane props,
// applies Load/SyncHistory pushes, and records what landed so the test
// can see it. No editor child, no text cell.
private class FakePane extends Component[TypedDocInput, TypedDocOutput] {
  type State = Option[Document[NodeData]]
  var lastPushed: Option[Document[NodeData]] = None

  def init: State = None

  def update(s: State, in: TypedDocInput)
      : Update[State, TypedDocOutput, TypedDocInput] =
    in match {
      case TypedDocInput.Pane(Some(inner), _, _) =>
        inner match {
          case TypedDocInput.Load(d, _)        => applied(d)
          case TypedDocInput.SyncHistory(d, _) => applied(d)
          case _ => Update(s)
        }
      case _ => Update(s)
    }

  private def applied(d: Document[NodeData]) = {
    lastPushed = Some(d)
    Update(Option(d))
  }

  def view(s: State): View[TypedDocInput] = View.Text("")
}

// The user's real scenario end-to-end: two mounted Workbenches (two
// "tabs") sharing one in-memory indexedDB and a real BroadcastChannel.
// Both hydrate the same seeded document, both toggle sync on, then a
// pane edit reported by tab 1 must land in tab 2's pane.
class WorkbenchSyncSuite extends munit.FunSuite {

  private def tick(): Future[Unit] = {
    val p = Promise[Unit]()
    js.timers.setTimeout(20)(p.success(()))
    p.future
  }

  private def until(cond: => Boolean, tries: Int = 100): Future[Unit] =
    if (cond) Future.successful(())
    else if (tries <= 0) Future.failed(new Exception("timed out"))
    else tick().flatMap(_ => until(cond, tries - 1))

  test("an edit in one workbench reaches a synced second workbench") {
    FakeBrowser.installWindow()
    val url = "wb-sync-doc"
    val seedDoc: Document[NodeData] =
      beginDocument(NodeData.StringData("a"))
    FakeBrowser.db.stores("documents") = js.Dictionary(
      s"$url@0" -> js.Dynamic.literal(
        "key" -> s"$url@0", "url" -> url,
        "version" -> 0, "outline" -> render(seedDoc)))

    val c1 = FakeBrowser.container()
    val c2 = FakeBrowser.container()
    val pane1 = new FakePane
    val pane2 = new FakePane
    val wb1 = Runtime.mount(c1, new Workbench(pane1), Boot)
    val wb2 = Runtime.mount(c2, new Workbench(pane2), Boot)

    until(
      FakeBrowser.hasText(c1, s"$url@v0") &&
      FakeBrowser.hasText(c2, s"$url@v0")).flatMap { _ =>
      // Staggered attach, like real tabs: tab 2 resumes the persisted
      // seed bytes rather than racing a concurrent seed write.
      wb1.send(ToggleSync)
      until(FakeBrowser.hasText(c1, "Session ready"))
        .flatMap(_ => tick())
        .flatMap { _ =>
          wb2.send(ToggleSync)
          until(FakeBrowser.hasText(c2, "Session ready"))
        }
    }.flatMap { _ =>
      // an edit "from the pane" in tab 1
      val edited: Document[NodeData] =
        beginDocument(NodeData.StringData("b"))
      wb1.send(FromPane(TypedDocOutput.DocChanged(edited)))
      until(pane2.lastPushed.exists(d => render(d) == render(edited)) &&
        FakeBrowser.hasText(c2, "Synced remote edit"))
    }.andThen { case _ =>
      wb1.dispose()
      wb2.dispose()
    }.map(_ => assert(true))
  }
}
