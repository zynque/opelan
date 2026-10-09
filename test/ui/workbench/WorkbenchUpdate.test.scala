package opelan.ui.workbench

import opelan.collaboration.automerge.ChangeInfo
import opelan.foundation.document._
import opelan.ui.typeddoc.{TypedDocInput, TypedDocOutput}
import WorkbenchInput._
import WorkbenchOutput._

class WorkbenchUpdateSuite extends munit.FunSuite {

  private def init: WorkbenchModel = Workbench.init

  private def doc(label: String): Document[NodeData] =
    beginDocument(NodeData.StringData(label))

  private def change(
      hash: String, actor: String, text: String,
      deps: Vector[String] = Vector.empty): ChangeInfo =
    ChangeInfo(hash, actor, 1, 0, Some("wrote"), deps, text)

  test("Boot requests storage hydration") {
    val u = workbenchUpdate(init, Boot)
    assertEquals(u.out, Vector(HydrateStorage))
  }

  test("hydration opens the first stored document") {
    val d = doc("alpha")
    val (store, v) = Store.empty.put("opelan:docs/alpha", d)
    val u = workbenchUpdate(init, StorageLoaded(store))
    assertEquals(u.state.workspace.openUrl, Some("opelan:docs/alpha"))
    assert(u.out.contains(PushPane(TypedDocInput.Load(d, None))))
  }

  test("a named document is persisted and opened") {
    val u = workbenchUpdate(init, DocumentNamed("My Doc"))
    val url = "opelan:docs/my-doc"
    assertEquals(u.state.workspace.openUrl, Some(url))
    assertEquals(u.state.workspace.openVersion, Some(0))
    assert(u.out.collect { case PersistDoc(u2, 0, _) => u2 }.contains(url))
    assert(u.out.exists { case PushPane(TypedDocInput.Load(_, _)) => true; case _ => false })
  }

  test("selecting a stored document pushes a load") {
    val d = doc("alpha")
    val (store, v) = Store.empty.put("u1", d)
    val m = init.copy(workspace = Workspace(store))
    val u = workbenchUpdate(m, SelectDocument("u1"))
    assertEquals(u.out, Vector(PushPane(TypedDocInput.Load(d, None))))
  }

  test("save pulls the doc then persists the reply") {
    val d = doc("alpha")
    val (store, _) = Store.empty.put("u1", doc("old"))
    val m = init.copy(workspace = Workspace(store).open("u1", 0, d))
    val u1 = workbenchUpdate(m, SaveRequested)
    assert(u1.state.pendingSave)
    assertEquals(u1.out, Vector(PushPane(TypedDocInput.RequestDoc)))
    val d2 = doc("edited")
    val u2 = workbenchUpdate(
      u1.state, FromPane(TypedDocOutput.DocChanged(d2)))
    assert(!u2.state.pendingSave)
    assert(u2.out.collect { case PersistDoc("u1", 1, dd) => dd }.contains(d2))
  }

  test("a save with no open document prompts for a name") {
    val m = init.copy(
      workspace = Workspace(doc = Some(doc("x"))), pendingSave = true)
    val u = workbenchUpdate(m, FromPane(TypedDocOutput.DocChanged(doc("x"))))
    assertEquals(u.out, Vector(PromptName(save = true)))
  }

  test("DocChanged feeds the session only when sync is on") {
    val d = doc("alpha")
    val ws = Workspace.empty.open("u1", 0, d)
    val off = workbenchUpdate(
      init.copy(workspace = ws), FromPane(TypedDocOutput.DocChanged(d)))
    assert(!off.out.exists(_.isInstanceOf[SessionText]))
    val on = workbenchUpdate(
      init.copy(workspace = ws, syncOn = true),
      FromPane(TypedDocOutput.DocChanged(d)))
    assertEquals(on.out, Vector(SessionText("u1", render(d))))
  }

  test("DocViewed mirrors but never feeds a session") {
    val d = doc("seen")
    val m = init.copy(
      workspace = Workspace.empty.open("u1", 0, doc("x")), syncOn = true)
    val u = workbenchUpdate(m, FromPane(TypedDocOutput.DocViewed(d)))
    assertEquals(u.state.workspace.doc, Some(d))
    assert(u.out.isEmpty)
  }

  test("toggling sync attaches the open doc, detaching reloads it") {
    val d = doc("alpha")
    val m = init.copy(workspace = Workspace.empty.open("u1", 0, d))
    val u = workbenchUpdate(m, ToggleSync)
    assert(u.state.syncOn)
    assertEquals(u.out, Vector(AttachSync("u1", render(d))))
    val off = workbenchUpdate(u.state, ToggleSync)
    assert(!off.state.syncOn)
    assertEquals(off.out.toSet,
      Set(DetachSync, PushPane(TypedDocInput.Load(d))))
  }

  test("undo/redo route to the session only while syncing") {
    val m = init.copy(workspace = Workspace.empty.open("u1", 0, doc("x")))
    val u1 = workbenchUpdate(m, FromPane(TypedDocOutput.UndoRequested))
    assertEquals(u1.state.status, "Nothing to undo")
    val u2 = workbenchUpdate(
      m.copy(syncOn = true), FromPane(TypedDocOutput.UndoRequested))
    assertEquals(u2.out, Vector(SessionUndo("u1")))
  }

  test("a session update pushes doc and synced history into the pane") {
    val ws = Workspace.empty.open("u1", 0, doc("x"))
    val m = init.copy(workspace = ws, syncOn = true)
    val text = render(doc("shared"))
    val entries = Vector(change("h1", "peer-actor", text))
    val u = workbenchUpdate(
      m, SessionUpdated("u1", text, entries, Vector("h1"), "my-actor"))
    val push = u.out.collect { case PushPane(i) => i }.head
    assert(push.isInstanceOf[TypedDocInput.SyncHistory])
    assertEquals(u.state.status, "Synced remote edit — u1")
    // The same text again reports nothing (local echo, no remote flag).
    val echo = workbenchUpdate(u.state,
      SessionUpdated("u1", text, entries, Vector("h1"), "my-actor"))
    assertEquals(echo.state.status, u.state.status)
  }

  test("a session update for a non-open document changes nothing") {
    val m = init.copy(syncOn = true)
    val u = workbenchUpdate(m,
      SessionUpdated("other", render(doc("y")),
        Vector(change("h1", "a", "t")), Vector("h1"), "me"))
    assertEquals(u.state, m)
    assert(u.out.isEmpty)
  }

  test("editing a checked-out version requests a branch, then opens it") {
    val edited = doc("edited")
    val m = init.copy(
      workspace = Workspace.empty.open("u1", 0, doc("x")), syncOn = true)
    val req = workbenchUpdate(m, FromPane(TypedDocOutput.BranchRequested(edited)))
    assertEquals(req.out, Vector(RequestBranch("u1", edited)))
    val u = workbenchUpdate(m, OpenBranch("u1", "u1~abc123", edited))
    assertEquals(u.state.workspace.openUrl, Some("u1~abc123"))
    assert(u.out.contains(AnnounceBranch("u1", "u1~abc123")))
    assert(u.out.contains(AttachSync("u1~abc123", render(edited))))
  }

  test("joining a branch attaches with an empty seed") {
    val m = init.copy(syncOn = true)
    val u = workbenchUpdate(m, JoinBranch("u1~xyz"))
    assertEquals(u.state.workspace.openUrl, Some("u1~xyz"))
    assert(u.out.contains(AttachSync("u1~xyz", "")))
    // Without sync the offer is stale; nothing happens.
    val off = workbenchUpdate(init, JoinBranch("u1~xyz"))
    assertEquals(off.state, init)
    assert(off.out.isEmpty)
  }

  test("a followed ref opens the pinned version and selects the node") {
    val target = doc("target")
    val (store, v) = Store.empty.put("u2", target)
    val ref = ExternalNodeReference("u2", v, target.rootId)
    val m = init.copy(workspace = Workspace(store))
    val u = workbenchUpdate(m, FromPane(TypedDocOutput.FollowRef(ref)))
    assertEquals(u.state.workspace.openUrl, Some("u2"))
    assert(u.out.contains(
      PushPane(TypedDocInput.Load(target, Some(target.rootId)))))
  }
}
