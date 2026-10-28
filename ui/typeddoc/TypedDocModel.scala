package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.foundation.document.store._
import opelan.foundation.language.{HoleRetry, InputSessions, TextSession}
import opelan.ui.editor.{DocPush, EditorOutput, SelectTarget, SyncedHistory}

// Which surface fills the pane: the generic structural editor, the
// CodeMirror text surface over the document's text, or one of the
// language's derived views shown alone (the "app" surface — the
// spreadsheet without the machinery). Derived views also stay live in
// the sidebar while editing.
enum DocView {
  case Editor
  case Text
  case Derived(name: String)
}

// The complete pane state — a pure value. `doc` mirrors the latest
// document (including edits made inside the editor child); `pushedDoc`
// is the value last pushed down into that child and changes only on
// external loads and text-originated edits, so editor-originated changes
// are never sent back — reloading the child would reset its selection,
// edit mode, and undo.
//
// `text` mirrors the text surface's content; `textEpoch` is bumped when
// text is re-derived from the document (structural edits, loads), which
// remounts the editor with the new text. TextEdited never bumps it —
// the surface already holds that text — so typed input never echoes back.
//
// `pushedSelect` rides along with `pushedDoc`: a load can name a node to
// select (e.g. a followed ref's target). It only matters when pushedDoc
// itself changes — the child input is resent only on change.
//
// `pushMode` says how the pushed doc relates to the one shown: Open
// restarts history, Merge records it as a version under a label
// (text-surface edits), Synced rebuilds the DAG from the session's shared
// change graph — identical on every peer.
case class TypedDocModel(
    doc: Document[NodeData],
    pushedDoc: Document[NodeData],
    pushedSelect: Option[SelectTarget] = None,
    pushMode: DocPush = DocPush.Open,
    view: DocView = DocView.Editor,
    text: String = "",
    textEpoch: Int = 0,
    status: String = "Ready",
    // The document store, delivered as a prop: language refs (a doc's
    // type, an input grammar's language) resolve against it, so an edited
    // `lang:` doc re-interprets dependents on the next render.
    store: Store = Store.empty,
    // The URL the shown document is stored under — a document's
    // identity, delivered as a prop alongside the store. A `lang:` def
    // doc's URL is the language it defines; views that interpret the
    // document itself (the def view's worked-example checks) need it.
    docUrl: Option[String] = None,
    // The text surface's incremental parse, carried across text edits.
    textSession: Option[TextSession] = None,
    // Each hole's last parse attempt, so a language change retries only
    // the holes that consulted a changed rule.
    holeRetry: HoleRetry = HoleRetry(),
    // Projected inputs' incremental parses, carried across commits.
    inputSessions: InputSessions = InputSessions(),
    // Props bookkeeping for the Pane input: which push/pull serials were
    // already applied, so re-delivery of unchanged props is a no-op.
    appliedPushSeq: Int = -1,
    appliedPullSeq: Int = 0)

// Everything that can happen to the pane — view events, view switching,
// text edits, and outputs routed up from the editor child.
enum TypedDocInput {
  case SwitchView(view: DocView)
  // `selectId` optionally selects a node in the loaded document — the
  // follow-a-ref jump target.
  case Load(doc: Document[NodeData], selectId: Option[Int] = None)
  // A synced session's change graph grew: push the live doc plus the
  // shared, author-tagged history — identical on every peer — so the
  // editor rebuilds its version DAG rather than keeping a private one.
  case SyncHistory(doc: Document[NodeData], history: SyncedHistory)
  // Re-emit the current document as DocChanged — lets the owner pull the
  // live doc (e.g. to save it) without a shadow copy going stale.
  case RequestDoc
  // Owner-driven props: `push` is desired state (a load/sync push),
  // applied when `pushSeq` is new — same-doc re-delivery can't reload;
  // bump `pushSeq` to force a re-push. A `pull` bump re-emits DocChanged.
  // `store` is the owner's current document store — the language
  // resolution context; `docUrl` the shown document's own URL (its
  // stored identity, when it has one).
  case Pane(
      push: Option[TypedDocInput], pushSeq: Int, pull: Int,
      store: Store = Store.empty, docUrl: Option[String] = None)
  case LoadExprSample
  case LoadSheetSample
  case TextEdited(text: String)
  // A provenance-tagged fragment of a derived view was clicked: select
  // the source node in the structural editor.
  case SelectSource(nodeId: Int)
  // A projected input committed text: parse it through the
  // language's input grammar and rewrite the node in place.
  case CommitInputText(nodeId: Int, text: String)
  // Go to definition: follow the document's type ref — the owner opens
  // the language document it names (the app's "implementation").
  case GoToDefinition
  // Accept pending updates: every external ref pinned behind a head
  // bumps to it — one ordinary edit, recorded as a version.
  case AcceptUpdates
  // Toggle the document's auto-update flag — pending pins then accept
  // without the click (still a recorded, versioned edit).
  case ToggleAutoUpdate
  case FromEditor(out: EditorOutput)
}

// What the pane reports outward, interpreted by whoever mounts it.
enum TypedDocOutput {
  case DocChanged(doc: Document[NodeData])
  // The editor's view moved to another version without producing a change
  // — update mirrors (save target, text surface) but never feed a session.
  case DocViewed(doc: Document[NodeData])
  case Status(message: String)
  // The editor's request to follow an external ref; resolved by whoever
  // holds the document store.
  case FollowRef(ref: ExternalNodeReference)
  // At a synced head, undo/redo belong to the session (revert this
  // actor's own change); the pane forwards the request to the owner.
  case UndoRequested
  case RedoRequested
  // The user edited a checked-out version of a synced doc — the owner
  // forks a branch draft instead of reverting the shared frontier.
  case BranchRequested(doc: Document[NodeData])
}
