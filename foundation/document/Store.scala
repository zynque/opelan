package opelan.foundation.document

// A versioned store of documents addressed by URL — the resolution side of
// ExternalNodeReference's `url@version#node` addressing scheme. Append-only:
// each `put` under a URL creates the next version (0, 1, 2, …) and earlier
// versions remain reachable, so existing external refs stay pinned and never
// dangle as a document evolves. Compaction is a lineage boundary within one
// version; the version boundary is the stronger one across documents.
//
// Pure and in-memory; IndexedDB persistence lives in data/storage and
// rebuilds a Store at load time via withVersion.
case class Store(
    versions: Map[(String, Int), Document[NodeData]] = Map.empty,
    heads: Map[String, Int] = Map.empty) {

  // The URLs that have at least one version, sorted for stable display.
  def urls: List[String] = heads.keys.toList.sorted

  def get(url: String, version: Int): Option[Document[NodeData]] =
    versions.get((url, version))

  // The latest version of a URL: (version id, document).
  def head(url: String): Option[(Int, Document[NodeData])] =
    heads.get(url).flatMap(v => get(url, v).map(v -> _))

  // Append `doc` as the next version of `url`; returns the new store and
  // the version id assigned. Versions are zero-based.
  def put(url: String, doc: Document[NodeData]): (Store, Int) = {
    val v = heads.getOrElse(url, -1) + 1
    (withVersion(url, v, doc), v)
  }

  // Place a document at an explicit version — used when rebuilding the
  // store from persistence. Raises the head if the version is newer.
  def withVersion(url: String, version: Int, doc: Document[NodeData]): Store =
    copy(
      versions = versions + ((url, version) -> doc),
      heads = heads + (url -> math.max(heads.getOrElse(url, -1), version)))

  // Dereference an external ref: the document at url@version, plus the node
  // within it. Fails if the version isn't in the store or the node id
  // doesn't exist in that version.
  def resolve(ref: ExternalNodeReference): Option[(Document[NodeData], Node[NodeData])] =
    get(ref.documentUrl, ref.documentVersionId).flatMap(doc =>
      doc.getNode(ref.nodeId).map(doc -> _))

  // A document's display name: its root's label, per the Typed convention
  // that the root's data labels the document. Falls back to the URL.
  def labelOf(url: String): String =
    head(url).flatMap { case (_, d) => Store.labelOf(d) }.getOrElse(url)

  // An unused URL for a new document named `name`: the slugged base, or
  // the first free `base-2`, `base-3`, … suffix when the name is taken.
  def freshUrl(name: String): String = {
    val base = Store.urlForName(name)
    if (!heads.contains(base)) base
    else Iterator.from(2).map(i => s"$base-$i").find(u => !heads.contains(u)).get
  }
}

object Store {
  val empty: Store = Store()

  // The root's label if it holds a string.
  def labelOf(doc: Document[NodeData]): Option[String] =
    doc.getNode(doc.rootId).map(_.data).collect { case NodeData.StringData(s) => s }

  // URL for a user document under the local `opelan:docs/` scheme — the
  // same scheme ExternalNodeReference.documentUrl addresses. The name
  // doubles as identity: saving under a taken URL appends versions to
  // that document rather than forking a new one.
  def urlForName(name: String): String = {
    val slug = name.toLowerCase.map(c => if (c.isLetterOrDigit) c else '-')
      .mkString.replaceAll("-+", "-").stripPrefix("-").stripSuffix("-")
    s"opelan:docs/${if (slug.nonEmpty) slug else "untitled"}"
  }
}
