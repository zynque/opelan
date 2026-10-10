package opelan.ui.fx

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.typedarray.Uint8Array
import opelan.collaboration.{DocSession, sessionIdentity}
import opelan.collaboration.automerge.ChangeInfo
import opelan.collaboration.backends.BroadcastTransport
import opelan.data.storage.{loadSyncDoc, saveSyncDoc}
import opelan.ui.fp._

// Seam component: live sync sessions. Unlike the request/response seams
// this one is stateful — its props are *desired state*: which URLs should
// have a session and what outline text each should hold. The hub diffs
// desired against live sessions: new URLs attach, removed URLs close,
// changed text feeds `localText` (which ignores repeats). One-shot
// commands (undo/redo/announce) ride along serialised so each fires once.
//
// Session identity is per-tab (sessionStorage): a unique actor id keeps
// concurrent tabs from colliding on (actor, seq), and the generated name
// tags each change so shared history shows authors. Automerge state
// persists to the `automerge_docs` store so a reloading tab resumes.
case class SyncProps(
    desired: Map[String, String],        // url → desired outline text
    cmd: Option[(Int, SyncCmd)] = None)  // serial'd one-shot command

enum SyncCmd {
  case Undo(url: String)
  case Redo(url: String)
  case Announce(base: String, branchUrl: String)
}

// The hub's input union: Props from the parent, everything else internal
// self-inputs emitted by Cmds (async completions and session callbacks).
enum SyncMsg {
  case Props(p: SyncProps)
  case AttachReady(url: String, saved: Option[Uint8Array])
  case SessionCreated(url: String, session: DocSession)
  case Started(url: String, name: String)
  case Grown(url: String, text: String,
    entries: Vector[ChangeInfo], heads: Vector[String], actor: String)
  case BranchOffered(branchUrl: String)
}

enum SyncEv {
  case Attached(url: String, name: String)
  case Grew(url: String, text: String,
    entries: Vector[ChangeInfo], heads: Vector[String], actor: String)
  case OfferBranch(branchUrl: String)
  case Status(message: String)
}

case class SyncState(
    desired: Map[String, String] = Map.empty,
    sessions: Map[String, DocSession] = Map.empty,
    attaching: Set[String] = Set.empty,
    done: Set[Int] = Set.empty)          // fired command serials

object SyncHub extends Component[SyncMsg, SyncEv] {
  type State = SyncState

  def init: State = SyncState()

  def update(s: State, in: SyncMsg): Update[State, SyncEv, SyncMsg] =
    in match {
      case SyncMsg.Props(p) => applyProps(s, p)

      // Persisted Automerge bytes arrived: build and attach the session.
      // Wiring happens inside the Cmd — the only place `emit` exists —
      // so callbacks can feed Grown/BranchOffered back as inputs.
      // A url undesired while its bytes loaded is simply dropped.
      case SyncMsg.AttachReady(url, saved) =>
        val s2 = s.copy(attaching = s.attaching - url)
        if (!s2.desired.contains(url)) Update(s2)
        else Update(s2, cmds = Vector(emit => {
          var sref: DocSession = null
          val (actor, name) = sessionIdentity()
          val session = new DocSession(
            url,
            new BroadcastTransport(url),
            actor,
            name,
            (entries, heads) =>
              emit(SyncMsg.Grown(url, sref.text, entries, heads, actor)),
            bytes => saveSyncDoc(url, bytes),
            u => emit(SyncMsg.BranchOffered(u)))
          sref = session
          emit(SyncMsg.SessionCreated(url, session))
          session.attach(s.desired.getOrElse(url, ""), saved)
          emit(SyncMsg.Started(url, name))
        }))

      case SyncMsg.SessionCreated(url, session) =>
        // Catch up if the desired text moved while bytes were loading.
        s.desired.get(url).foreach(session.localText)
        Update(s.copy(sessions = s.sessions + (url -> session)))

      case SyncMsg.Started(url, name) =>
        Update(s, Vector(SyncEv.Attached(url, name)))

      case SyncMsg.Grown(url, text, entries, heads, actor) =>
        Update(s, Vector(SyncEv.Grew(url, text, entries, heads, actor)))

      case SyncMsg.BranchOffered(branchUrl) =>
        // A peer forked the shared line into a branch draft: offer to
        // follow rather than silently switching.
        if (s.sessions.contains(branchUrl) || s.attaching(branchUrl)) Update(s)
        else Update(s, Vector(SyncEv.OfferBranch(branchUrl)))
    }

  // Reconcile desired sessions against live ones; run any new command.
  private def applyProps(s: State, p: SyncProps): Update[State, SyncEv, SyncMsg] = {
    val closing = s.sessions.keySet -- p.desired.keySet
    closing.foreach(u => s.sessions(u).close())
    var st = s.copy(sessions = s.sessions -- closing, desired = p.desired)

    // Feed text into live sessions; localText ignores repeats, so the
    // echo from pushing a synced doc back into the pane terminates.
    st.sessions.foreach((url, sess) => p.desired.get(url).foreach(sess.localText))

    val attaching = p.desired.keySet -- st.sessions.keySet -- st.attaching
    st = st.copy(attaching = st.attaching ++ attaching)
    val attachCmds = attaching.toVector.map(url => Cmd[SyncMsg](emit =>
      loadSyncDoc(url).onComplete(r =>
        emit(SyncMsg.AttachReady(url, r.toOption.flatten)))))

    val out = p.cmd.filter(c => !st.done(c._1)) match {
      case Some((n, c)) =>
        st = st.copy(done = st.done + n)
        c match {
          case SyncCmd.Undo(url) =>
            Vector(SyncEv.Status(
              st.sessions.get(url).map(_.undo()).getOrElse("Nothing to undo")))
          case SyncCmd.Redo(url) =>
            Vector(SyncEv.Status(
              st.sessions.get(url).map(_.redo()).getOrElse("Nothing to redo")))
          case SyncCmd.Announce(base, branch) =>
            st.sessions.get(base).foreach(_.announce(branch))
            Vector.empty
        }
      case None => Vector.empty
    }
    Update(st, out, attachCmds)
  }

  override def unmount(s: State): Unit =
    s.sessions.values.foreach(_.close())

  def view(s: State): View[SyncMsg] = View.Text("")
}
