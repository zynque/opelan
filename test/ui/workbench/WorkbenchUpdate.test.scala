package opelan.ui.workbench

import opelan.collaboration.automerge.ChangeInfo
import opelan.foundation.document._
import opelan.ui.fx._
import opelan.ui.typeddoc.{TypedDocInput, TypedDocOutput}
import opelan.ui.workbench.WorkbenchInput._
import opelan.ui.workbench.WorkbenchUpdate.workbenchUpdate

// The workbench is pure now: instead of asserting emitted outputs, tests
// assert the desired state the children() declaration diffs into seam
// props — storageQ heads, prompt/random requests, the synced map.
class WorkbenchUpdateSuite extends munit.FunSuite {

  private def init: WorkbenchModel = Workbench.init

  private def doc(label: String): Document[NodeData] =
    beginDocument(NodeData.StringData(label))

  private def change(
      hash: String, actor: String, text: String,
      deps: Vector[String] = Vector.empty): ChangeInfo =
    ChangeInfo(hash, actor, 1, 0, Some("wrote"), deps, text)

  test("Boot queues storage hydration") {
    val u = workbenchUpdate(init, Boot)
    assertEquals(
      u.state.storageQ, Vector(StorageReq.Hydrate(0)))
  }

  test("hydration opens the first stored document") {
    val d = doc("alpha")
    val (store, _) = Store.empty.put("opelan:docs/alpha", d)
    val m = workbenchUpdate(init, Boot).state
    val u = workbenchUpdate(m, FromStorage(StorageEv.Hydrated(store)))
    // the loaded store is installed — it's what the sidebar lists
    assertEquals(u.state.workspace.store, store)
    assertEquals(u.state.workspace.openUrl, Some("opelan:docs/alpha"))
    assertEquals(u.state.panePush, Some(TypedDocInput.Load(d, None)))
    assert(u.state.storageQ.isEmpty)
  }

  test("NewDocument asks the prompt; its reply creates and persists") {
    val m = workbenchUpdate(init, NewDocument).state
    assert(m.prompt.isDefined)
    assertEquals(m.promptFor, Some(PromptFor.NewDoc))
    val u = workbenchUpdate(m, FromPrompt(PromptEv.Answered(0, Some("My Doc"))))
    val url = "opelan:docs/my-doc"
    assertEquals(u.state.workspace.openUrl, Some(url))
    assertEquals(u.state.workspace.openVersion, Some(0))
    assert(u.state.panePush.exists(_.isInstanceOf[TypedDocInput.Load]))
    assert(u.state.storageQ.exists {
      case StorageReq.Persist(_, u2, 0, _) => u2 == url
      case _ => false
    })
    assert(u.state.prompt.isEmpty)
  }

  test("selecting a stored document pushes a load") {
    val d = doc("alpha")
    val (store, _) = Store.empty.put("u1", d)
    val m = init.copy(workspace = Workspace(store))
    val u = workbenchUpdate(m, SelectDocument("u1"))
    assertEquals(u.state.panePush, Some(TypedDocInput.Load(d, None)))
    assertEquals(u.state.pushSeq, m.pushSeq + 1)
  }

  test("save pulls the doc then persists the reply") {
    val d = doc("alpha")
    val (store, _) = Store.empty.put("u1", doc("old"))
    val m = init.copy(workspace = Workspace(store).open("u1", 0, d))
    val u1 = workbenchUpdate(m, SaveRequested)
    assert(u1.state.pendingSave)
    assertEquals(u1.state.pullSerial, m.pullSerial + 1)
    val d2 = doc("edited")
    val u2 = workbenchUpdate(
      u1.state, FromPane(TypedDocOutput.DocChanged(d2)))
    assert(!u2.state.pendingSave)
    assert(u2.state.storageQ.exists {
      case StorageReq.Persist(_, "u1", 1, dd) => dd == d2
      case _ => false
    })
  }

  test("a save with no open document prompts for a name") {
    val m = init.copy(
      workspace = Workspace(doc = Some(doc("x"))), pendingSave = true)
    val u = workbenchUpdate(m, FromPane(TypedDocOutput.DocChanged(doc("x"))))
    assert(u.state.prompt.isDefined)
    assertEquals(u.state.promptFor, Some(PromptFor.SaveAs))
    // The reply persists under the generated url.
    val named = workbenchUpdate(u.state,
      FromPrompt(PromptEv.Answered(0, Some("thing"))))
    assert(named.state.storageQ.exists(_.isInstanceOf[StorageReq.Persist]))
  }

  test("DocChanged feeds desired session text only when syncing") {
    val d = doc("alpha")
    val ws = Workspace.empty.open("u1", 0, d)
    val off = workbenchUpdate(
      init.copy(workspace = ws), FromPane(TypedDocOutput.DocChanged(d)))
    assert(!off.state.synced.contains("u1"))
    val on = workbenchUpdate(
      init.copy(workspace = ws, syncOn = true, synced = Map("u1" -> "old")),
      FromPane(TypedDocOutput.DocChanged(d)))
    assertEquals(on.state.synced, Map("u1" -> render(d)))
  }

  test("DocViewed mirrors but never feeds a session") {
    val d = doc("seen")
    val m = init.copy(
      workspace = Workspace.empty.open("u1", 0, doc("x")),
      syncOn = true, synced = Map("u1" -> "old"))
    val u = workbenchUpdate(m, FromPane(TypedDocOutput.DocViewed(d)))
    assertEquals(u.state.workspace.doc, Some(d))
    assertEquals(u.state.synced, Map("u1" -> "old"))
  }

  test("toggling sync declares a session; off clears it and re-pushes") {
    val d = doc("alpha")
    val m = init.copy(workspace = Workspace.empty.open("u1", 0, d))
    val u = workbenchUpdate(m, ToggleSync)
    assert(u.state.syncOn)
    assertEquals(u.state.synced, Map("u1" -> render(d)))
    val off = workbenchUpdate(u.state, ToggleSync)
    assert(!off.state.syncOn)
    assert(off.state.synced.isEmpty)
    assertEquals(off.state.panePush, Some(TypedDocInput.Load(d)))
    assertEquals(off.state.pushSeq, u.state.pushSeq + 1)
  }

  test("undo/redo become serial'd session commands only while syncing") {
    val m = init.copy(workspace = Workspace.empty.open("u1", 0, doc("x")))
    val u1 = workbenchUpdate(m, FromPane(TypedDocOutput.UndoRequested))
    assertEquals(u1.state.status, "Nothing to undo")
    val u2 = workbenchUpdate(
      m.copy(syncOn = true, synced = Map("u1" -> "")),
      FromPane(TypedDocOutput.UndoRequested))
    assertEquals(u2.state.syncCmd, Some(0 -> SyncCmd.Undo("u1")))
  }

  test("a session's growth pushes doc and synced history into the pane") {
    val ws = Workspace.empty.open("u1", 0, doc("x"))
    val m = init.copy(
      workspace = ws, syncOn = true, synced = Map("u1" -> "old"))
    val text = render(doc("shared"))
    val entries = Vector(change("h1", "peer-actor", text))
    val ev = SyncEv.Grew("u1", text, entries, Vector("h1"), "my-actor")
    val u = workbenchUpdate(m, FromSync(ev))
    assert(u.state.panePush.exists(_.isInstanceOf[TypedDocInput.SyncHistory]))
    assertEquals(u.state.status, "Synced remote edit — u1")
    // The same text again reports nothing (local echo, no remote flag).
    val echo = workbenchUpdate(u.state, FromSync(ev))
    assertEquals(echo.state.status, u.state.status)
  }

  test("a session update for a non-open document changes nothing") {
    val m = init.copy(syncOn = true)
    val u = workbenchUpdate(m, FromSync(SyncEv.Grew("other",
      render(doc("y")), Vector(change("h1", "a", "t")),
      Vector("h1"), "me")))
    assertEquals(u.state.workspace, m.workspace)
    assert(u.state.panePush.isEmpty)
  }

  test("editing a checked-out version requests a suffix, then branches") {
    val edited = doc("edited")
    val m = init.copy(
      workspace = Workspace.empty.open("u1", 0, doc("x")),
      syncOn = true, synced = Map("u1" -> ""))
    val req = workbenchUpdate(m, FromPane(TypedDocOutput.BranchRequested(edited)))
    assert(req.state.randomReq.isDefined)
    assertEquals(req.state.pendingBranch, Some("u1" -> edited))
    val u = workbenchUpdate(req.state,
      FromRandom(RandomEv.Generated(0, "abc123")))
    assertEquals(u.state.workspace.openUrl, Some("u1~abc123"))
    assertEquals(u.state.synced("u1~abc123"), render(edited))
    assert(u.state.syncCmd.exists(_._2 == SyncCmd.Announce("u1", "u1~abc123")))
    assert(u.state.randomReq.isEmpty)
    assert(u.state.pendingBranch.isEmpty)
  }

  test("a branch offer confirms; accepting joins the branch") {
    val m = init.copy(syncOn = true, synced = Map("u1" -> ""))
    val offered = workbenchUpdate(m,
      FromSync(SyncEv.OfferBranch("u1~xyz")))
    assert(offered.state.prompt.isDefined)
    assertEquals(offered.state.promptFor, Some(PromptFor.Join("u1~xyz")))
    val u = workbenchUpdate(offered.state,
      FromPrompt(PromptEv.Decided(0, true)))
    assertEquals(u.state.workspace.openUrl, Some("u1~xyz"))
    assertEquals(u.state.synced("u1~xyz"), "")
    assert(u.state.panePush.exists(_.isInstanceOf[TypedDocInput.Load]))
    // Declining opens nothing.
    val no = workbenchUpdate(offered.state,
      FromPrompt(PromptEv.Decided(0, false)))
    assertEquals(no.state.workspace.openUrl, None)
  }

  test("a followed ref opens the pinned version and selects the node") {
    val target = doc("target")
    val (store, v) = Store.empty.put("u2", target)
    val ref = ExternalNodeReference("u2", v, target.rootId)
    val m = init.copy(workspace = Workspace(store))
    val u = workbenchUpdate(m, FromPane(TypedDocOutput.FollowRef(ref)))
    assertEquals(u.state.workspace.openUrl, Some("u2"))
    assertEquals(u.state.panePush,
      Some(TypedDocInput.Load(target, Some(target.rootId))))
  }

  test("children declares all seams and both panes") {
    val kids = Workbench.children(init)
    assertEquals(kids.map(_.key).toSet,
      Set("storage", "prompt", "random", "sync", "doc", "demo"))
    assertEquals(kids.find(_.key == "doc").map(_.slot), Some("doc"))
    assertEquals(kids.find(_.key == "demo").map(_.slot), Some("demo"))
  }
}
