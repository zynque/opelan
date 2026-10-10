package opelan.ui.fx

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure, Try}
import opelan.data.storage.{IndexedDBStore, loadAll, persist}
import opelan.foundation.document._
import opelan.ui.fp._

// Seam component: IndexedDB persistence for the document store. Requests
// arrive as props — the input is the current head of the parent's
// request queue (None = idle); each request carries a serial the parent
// clears on reply. Completions arrive as internal self-inputs emitted by
// the Cmd that launched the async work.
enum StorageReq {
  // Requests (sent by the parent).
  case Hydrate(id: Int)
  case Persist(id: Int, url: String, version: Int, doc: Document[NodeData])
  // Internal completions (emitted by cmds back to this component).
  case Hydrated(id: Int, result: Try[Store])
  case Persisted(id: Int, url: String, version: Int, result: Try[Unit])
}

enum StorageEv {
  case Hydrated(store: Store)
  case HydrateFailed(message: String)
  case Persisted(url: String, version: Int)
  case PersistFailed(message: String)
}

case class StorageState(seen: Set[Int] = Set.empty)

object StorageHub extends Component[Option[StorageReq], StorageEv] {
  type State = StorageState

  def init: State = StorageState()

  def update(s: State, in: Option[StorageReq]): Update[State, StorageEv, Option[StorageReq]] =
    in match {
      case Some(StorageReq.Hydrate(id)) if !s.seen(id) =>
        Update(s.copy(seen = s.seen + id), cmds = Vector(emit =>
          IndexedDBStore.initialize().flatMap(_ => loadAll()).onComplete(r =>
            emit(Some(StorageReq.Hydrated(id, r))))))

      case Some(StorageReq.Persist(id, url, v, doc)) if !s.seen(id) =>
        Update(s.copy(seen = s.seen + id), cmds = Vector(emit =>
          persist(url, v, doc).onComplete(r =>
            emit(Some(StorageReq.Persisted(id, url, v, r))))))

      case Some(StorageReq.Hydrated(_, Success(store))) =>
        Update(s, Vector(StorageEv.Hydrated(store)))
      case Some(StorageReq.Hydrated(_, Failure(e))) =>
        Update(s, Vector(StorageEv.HydrateFailed(e.getMessage)))
      case Some(StorageReq.Persisted(_, url, v, Success(_))) =>
        Update(s, Vector(StorageEv.Persisted(url, v)))
      case Some(StorageReq.Persisted(_, _, _, Failure(e))) =>
        Update(s, Vector(StorageEv.PersistFailed(e.getMessage)))

      case _ => Update(s)
    }

  def view(s: State): View[Option[StorageReq]] = View.Text("")
}
