package opelan.ui.typeddoc

import opelan.examples.{ExprLanguage, SheetLanguage, languageForDoc}
import opelan.foundation.document._
import opelan.foundation.langdef.{autoUpdateEnabled, setAutoUpdate}
import opelan.foundation.language.{
  HoleRetry, TextSession, applyDocContent, applyDocText, docText, holeCount}
import opelan.ui.editor.{DocPush, EditorOutput, SelectTarget}
import opelan.ui.fp.Update
import TypedDocInput._
import TypedDocOutput._

// The pure transition function for the typed-document pane. After each
// step: an auto-update-opted document accepts pending pin bumps without
// the click (the same recorded edit as AcceptUpdates), and the pane
// re-remembers its document's hole attempts against the current store,
// so the next render reuses every attempt a language change didn't
// touch. Both are skipped when the step moved neither doc nor store.
def typedDocUpdate(m: TypedDocModel, input: TypedDocInput): Update[TypedDocModel, TypedDocOutput, TypedDocInput] = {
  val u = autoAccept(step(m, input))
  val s = u.state
  val retry =
    if ((s.doc eq m.doc) && (s.store eq m.store)) s.holeRetry
    else languageForDoc(s.doc, s.store)
      .fold(HoleRetry())(_.rememberHoles(s.doc, s.holeRetry))
  u.copy(state = s.copy(holeRetry = retry))
}

private def autoAccept(
    u: Update[TypedDocModel, TypedDocOutput, TypedDocInput])
    : Update[TypedDocModel, TypedDocOutput, TypedDocInput] = {
  val s = u.state
  if (!autoUpdateEnabled(s.doc)) u
  else acceptUpdates(s.doc, s.store) match {
    case Some(d) =>
      val msg = "Pins auto-updated to their heads"
      u.copy(
        state = s.copy(
          doc = d, pushedDoc = d,
          pushMode = DocPush.Merge("Auto-update"), status = msg),
        out = u.out ++ Vector(DocChanged(d), Status(msg)))
    case None => u
  }
}

private def step(m: TypedDocModel, input: TypedDocInput): Update[TypedDocModel, TypedDocOutput, TypedDocInput] =
  input match {
    case SwitchView(v) => Update(m.copy(view = v))
    case Load(d, sel)  => load(m, d, sel, "Document loaded")
    // The doc plus its shared history both come from the session —
    // emit no DocChanged echo: the owner already knows, and the echo
    // would feed back into the session as a local text.
    case SyncHistory(d, h) =>
      // Only bump the epoch when the text actually changed — a push
      // echoing our own edit would remount the text cell mid-typing.
      val t = docText(d, langFor(m))
      Update(m.copy(
        doc = d,
        pushedDoc = d,
        pushedSelect = None,
        pushMode = DocPush.Synced(h),
        text = t,
        textEpoch = m.textEpoch + (if (t == m.text) 0 else 1),
        status = "Synced"))
    case RequestDoc    => Update(m, Vector(DocChanged(m.doc)))

    // The owner's props: apply the push when its serial is new; emit the
    // live doc when the pull serial moved. The push recurses into this
    // same update — its outputs propagate, its inputs stay internal.
    case Pane(push, pushSeq, pull, store) =>
      // The store prop applies before the push so a load resolves the
      // new document's language against the current resolution context.
      val m0 = m.copy(store = store)
      val applying = push.filter(_ => pushSeq != m.appliedPushSeq)
      val u = applying.map(in => typedDocUpdate(m0, in)).getOrElse(Update(m0))
      val m2 = u.state.copy(
        appliedPushSeq = if (applying.isDefined) pushSeq else m.appliedPushSeq,
        appliedPullSeq = pull)
      Update(m2, u.out ++
        (if (pull != m.appliedPullSeq) Vector(DocChanged(m2.doc))
         else Vector.empty))
    case LoadExprSample =>
      load(m, ExprLanguage.sampleDoc, None, "Expression sample loaded")
    case LoadSheetSample =>
      load(m, SheetLanguage.sampleDoc, None, "Sheet sample loaded")

    // A text edit is total: apply returns a new document (with holes
    // where the text doesn't fit) or an error that only touches status.
    // A language with an incremental syntax reparses through the pane's
    // text session, so the work follows the size of the edit.
    case TextEdited(t) =>
      val session = languageForDoc(m.doc, m.store).flatMap(_.textSyntax)
        .map(syn => m.textSession.fold(TextSession.start(syn, t))(_.retext(syn, t)))
      val applied = session match {
        case Some(s) => applyDocContent(m.doc, s.content)
        case None    => applyDocText(m.doc, langFor(m), t)
      }
      applied match {
        case Right(d) =>
          val holes = holeCount(d)
          val msg =
            if (holes == 0) "Parsed"
            else s"Parsed — $holes hole${if (holes == 1) "" else "s"}"
          Update(
            m.copy(
              doc = d, pushedDoc = d, pushedSelect = None,
              pushMode = DocPush.Merge("Text edit"),
              text = t, status = msg,
              textSession = session.orElse(m.textSession)),
            Vector(DocChanged(d), Status(msg)))
        case Left(err) =>
          Update(m.copy(text = t, status = err), Vector(Status(err)))
      }

    case FromEditor(EditorOutput.DocViewed(d)) =>
      // Like DocChanged (mirrors follow what the pane shows) but marked
      // as navigation so the owner never feeds it to a session.
      mirrorDoc(m, d, Vector(DocViewed(d)))
    case FromEditor(EditorOutput.UndoRequested) =>
      Update(m, Vector(UndoRequested))
    case FromEditor(EditorOutput.RedoRequested) =>
      Update(m, Vector(RedoRequested))
    case FromEditor(EditorOutput.BranchRequested(d)) =>
      // Force the remount: the pane is about to reopen at a branch URL.
      val t = docText(d, langFor(m))
      Update(
        m.copy(doc = d, text = t, textEpoch = m.textEpoch + 1),
        Vector(BranchRequested(d)))
    case FromEditor(EditorOutput.DocChanged(d)) =>
      // The editor echoes the doc we just pushed via SyncDocument; that
      // echo must not re-derive the text (it would remount the
      // editor mid-typing). A doc equal to ours is such an echo.
      if (d == m.doc) Update(m)
      else mirrorDoc(m, d, Vector(DocChanged(d)))
    case FromEditor(EditorOutput.FollowRef(ref)) =>
      Update(m, Vector(FollowRef(ref)))
    case FromEditor(EditorOutput.Status(s)) =>
      Update(m.copy(status = s), Vector(Status(s)))

    // Provenance navigation: select the producing node in the editor
    // child. The selection rides the ordinary push channel — a push of
    // the live doc carrying a selection records no version (see
    // syncDocument) — and it is a silent select: the node highlights
    // without the outline claiming focus, so a click in a projected
    // editor (a sheet cell's box) keeps focus where the user put it.
    // On a derived-view surface the click also stays there — the app
    // must not be yanked away by its own provenance — while on the
    // other surfaces the structural editor shows the selection.
    case SelectSource(id) =>
      if (m.doc.getNode(id).isDefined)
        Update(m.copy(
          pushedDoc = m.doc,
          pushedSelect = Some(SelectTarget(id, focus = false)),
          pushMode = m.pushMode match {
            case DocPush.Open => DocPush.Merge("Select")
            case p            => p
          },
          view = m.view match {
            case DocView.Derived(_) => m.view
            case _                  => DocView.Editor
          }))
      else Update(m)

    // Go to definition: the document's type ref names the language
    // document; the owner resolves it through the store.
    case GoToDefinition =>
      typeRefOf(m.doc) match {
        case Some(ref) => Update(m, Vector(FollowRef(ref)))
        case None =>
          Update(m.copy(status = "Untyped document"),
            Vector(Status("Untyped document")))
      }

    case AcceptUpdates =>
      acceptUpdates(m.doc, m.store) match {
        case Some(d) =>
          val msg = "Refs updated to their heads"
          Update(
            m.copy(
              doc = d, pushedDoc = d, pushedSelect = None,
              pushMode = DocPush.Merge("Accept updates"), status = msg),
            Vector(DocChanged(d), Status(msg)))
        case None =>
          Update(m.copy(status = "No pending updates"),
            Vector(Status("No pending updates")))
      }

    case ToggleAutoUpdate =>
      setAutoUpdate(m.doc, !autoUpdateEnabled(m.doc)) match {
        case Right(d) =>
          val on = autoUpdateEnabled(d)
          val msg = s"Auto-update ${if (on) "on" else "off"}"
          Update(
            m.copy(
              doc = d, pushedDoc = d, pushedSelect = None,
              pushMode = DocPush.Merge("Auto-update"), status = msg),
            Vector(DocChanged(d), Status(msg)))
        case Left(err) =>
          Update(m.copy(status = err), Vector(Status(err)))
      }

    // A projected cell edit: parse through the doc's language and commit
    // the content in place — the node's id survives, and so do the ids
    // of every child whose content didn't change (commitContent). An
    // unchanged commit — e.g. a blur with no edit — is a no-op, so
    // projections can't churn versions. Parsed constructs arrive as
    // inline refs; the commit imports them (internalizeRefs).
    case CommitCellText(id, t) =>
      languageForDoc(m.doc, m.store).flatMap(_.parseCell(t)) match {
        case Some(content) if unchangedCell(m.doc, id, content) =>
          Update(m)
        case Some(content) =>
          commitContent(id, content, m.doc)
              .flatMap(internalizeRefs(_, id)) match {
            case Right(d) =>
              val msg = "Cell updated"
              Update(
                m.copy(
                  doc = d, pushedDoc = d, pushedSelect = None,
                  pushMode = DocPush.Merge("Cell edit"),
                  text = docText(d, langFor(m)), textEpoch = m.textEpoch + 1,
                  status = msg),
                Vector(DocChanged(d), Status(msg)))
            case Left(err) =>
              Update(m.copy(status = err), Vector(Status(err)))
          }
        case None =>
          Update(m.copy(status = "No cell grammar"),
            Vector(Status("No cell grammar")))
      }
  }

// A cell commit that reproduces the node's current content (compared
// with its imports inlined, as the parser writes constructs).
private def unchangedCell(
    d: Document[NodeData], id: Int, content: DetachedNode[NodeData]): Boolean =
  extractExternalized(d, id).contains(content)

// The resolver handed to the text-surface functions: type refs resolve
// through the store prop, same as the derived views.
private def langFor(m: TypedDocModel)
    : Document[NodeData] => Option[opelan.foundation.language.Language] =
  d => languageForDoc(d, m.store)

// Mirror a document the editor is showing: re-derive its text, bumping
// the epoch when it changed (the bump remounts the text cell).
private def mirrorDoc(
    m: TypedDocModel,
    d: Document[NodeData],
    out: Vector[TypedDocOutput]): Update[TypedDocModel, TypedDocOutput, TypedDocInput] = {
  val t = docText(d, langFor(m))
  Update(
    m.copy(doc = d, text = t,
      textEpoch = m.textEpoch + (if (t == m.text) 0 else 1)),
    out)
}

private def load(
    m: TypedDocModel,
    d: Document[NodeData],
    select: Option[Int],
    msg: String): Update[TypedDocModel, TypedDocOutput, TypedDocInput] =
  Update(
    m.copy(
      doc = d,
      pushedDoc = d,
      pushedSelect = select.filter(id => d.getNode(id).isDefined).map(SelectTarget(_)),
      pushMode = DocPush.Open,
      // A derived view only survives the load when the new document's
      // language also offers it; otherwise fall to that language's
      // default perspective (a raw document falls back to the editor).
      view = m.view match {
        case DocView.Derived(n) =>
          languageForDoc(d, m.store) match {
            case Some(l) if l.views.contains(n) => m.view
            case Some(l) => l.defaultView.map(DocView.Derived(_))
              .getOrElse(DocView.Editor)
            case None => DocView.Editor
          }
        case v => v
      },
      text = docText(d, langFor(m)),
      textEpoch = m.textEpoch + 1,
      status = msg),
    Vector(DocChanged(d), Status(msg)))
