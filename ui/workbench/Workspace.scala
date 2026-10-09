package opelan.ui.workbench

import opelan.foundation.document._

// The workbench's document workspace — a pure value so the open/save/
// follow-ref decisions are unit-testable. `store` is the versioned
// document set; `openUrl`/`openVersion`/`doc` track what the pane is
// showing. DOM and IndexedDB effects stay in WorkbenchShell.
case class Workspace(
    store: Store = Store.empty,
    openUrl: Option[String] = None,
    openVersion: Option[Int] = None,
    doc: Option[Document[NodeData]] = None) {

  // Mark a document (at a specific version) as open in the pane.
  def open(url: String, version: Int, d: Document[NodeData]): Workspace =
    copy(openUrl = Some(url), openVersion = Some(version), doc = Some(d))

  // Record that the open document changed (an edit from the pane).
  def changed(d: Document[NodeData]): Workspace = copy(doc = Some(d))

  // An unused URL for a new document named `name`.
  def freshUrl(name: String): String = store.freshUrl(name)

  // Save `d` as the next version of `url` — unless the head already holds
  // an equal document. Returns the updated workspace (store written, the
  // doc marked open at the new version) and the version written, if any.
  def save(url: String, d: Document[NodeData]): (Workspace, Option[Int]) =
    if (store.head(url).exists(_._2 == d)) (this, None)
    else {
      val (s, v) = store.put(url, d)
      (copy(store = s, openUrl = Some(url), openVersion = Some(v), doc = Some(d)), Some(v))
    }

  // What a followed external ref should open: (url, version, doc, nodeId).
  def follow(ref: ExternalNodeReference): Option[(String, Int, Document[NodeData], Int)] =
    store.resolve(ref).map { case (d, _) =>
      (ref.documentUrl, ref.documentVersionId, d, ref.nodeId)
    }

  // Sidebar entries: (url, head version, label), sorted by URL.
  def entries: List[(String, Int, String)] =
    store.urls.flatMap(url =>
      store.head(url).map { case (v, _) => (url, v, store.labelOf(url)) })
}

object Workspace {
  val empty: Workspace = Workspace()
}
