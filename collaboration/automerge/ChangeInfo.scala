package opelan.collaboration.automerge

// One change in an Automerge doc's history — the unit of the shared
// change graph. `deps` are the change's parent hashes (0 = a root, 2+ =
// a merge); `snapshotText` is the document's text immediately after the
// change applied. `message` is the author-set label — synced docs put
// the writer's display name there.
case class ChangeInfo(
    hash: String,
    actor: String,
    seq: Int,
    time: Double,
    message: Option[String],
    deps: Vector[String],
    snapshotText: String)
