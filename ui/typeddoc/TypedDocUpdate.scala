package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.foundation.language.{ExprLanguage, applyDocText, docText, holeCount}
import opelan.ui.editor.{DocPush, EditorOutput}
import opelan.ui.fp.Update
import TypedDocInput._
import TypedDocOutput._

// The pure transition function for the typed-document pane.
def typedDocUpdate(m: TypedDocModel, input: TypedDocInput): Update[TypedDocModel, TypedDocOutput, TypedDocInput] =
  input match {
    case SwitchView(v) => Update(m.copy(view = v))
    case Load(d, sel)  => load(m, d, sel, "Document loaded")
    // The doc plus its shared history both come from the session —
    // emit no DocChanged echo: the owner already knows, and the echo
    // would feed back into the session as a local text.
    case SyncHistory(d, h) =>
      // Only bump the epoch when the text actually changed — a push
      // echoing our own edit would remount the text cell mid-typing.
      val t = docText(d)
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
    case Pane(push, pushSeq, pull) =>
      val applying = push.filter(_ => pushSeq != m.appliedPushSeq)
      val u = applying.map(in => typedDocUpdate(m, in)).getOrElse(Update(m))
      val m2 = u.state.copy(
        appliedPushSeq = if (applying.isDefined) pushSeq else m.appliedPushSeq,
        appliedPullSeq = pull)
      Update(m2, u.out ++
        (if (pull != m.appliedPullSeq) Vector(DocChanged(m2.doc))
         else Vector.empty))
    case LoadExprSample =>
      load(m, ExprLanguage.sampleDoc, None, "Expression sample loaded")

    // A text edit is total: apply returns a new document (with holes
    // where the text doesn't fit) or an error that only touches status.
    case TextEdited(t) =>
      applyDocText(m.doc, t) match {
        case Right(d) =>
          val holes = holeCount(d)
          val msg =
            if (holes == 0) "Parsed"
            else s"Parsed — $holes hole${if (holes == 1) "" else "s"}"
          Update(
            m.copy(
              doc = d, pushedDoc = d, pushedSelect = None,
              pushMode = DocPush.Merge("Text edit"),
              text = t, status = msg),
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
      val t = docText(d)
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
  }

// Mirror a document the editor is showing: re-derive its text, bumping
// the epoch when it changed (the bump remounts the text cell).
private def mirrorDoc(
    m: TypedDocModel,
    d: Document[NodeData],
    out: Vector[TypedDocOutput]): Update[TypedDocModel, TypedDocOutput, TypedDocInput] = {
  val t = docText(d)
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
      pushedSelect = select.filter(id => d.getNode(id).isDefined),
      pushMode = DocPush.Open,
      text = docText(d),
      textEpoch = m.textEpoch + 1,
      status = msg),
    Vector(DocChanged(d), Status(msg)))
