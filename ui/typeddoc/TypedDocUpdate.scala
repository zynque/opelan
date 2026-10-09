package opelan.ui.typeddoc

import opelan.foundation.document._
import opelan.foundation.language.{ExprLanguage, applyDocText, docText, holeCount}
import opelan.ui.editor.EditorOutput
import opelan.ui.fp.Update
import TypedDocInput._
import TypedDocOutput._

// The pure transition function for the typed-document pane.
def typedDocUpdate(m: TypedDocModel, input: TypedDocInput): Update[TypedDocModel, TypedDocOutput] =
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
        pushedMergeLabel = None,
        pushedHistory = Some(h),
        text = t,
        textEpoch = m.textEpoch + (if (t == m.text) 0 else 1),
        status = "Synced"))
    case RequestDoc    => Update(m, Vector(DocChanged(m.doc)))
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
              pushedMergeLabel = Some("Text edit"), pushedHistory = None,
              text = t, status = msg),
            Vector(DocChanged(d), Status(msg)))
        case Left(err) =>
          Update(m.copy(text = t, status = err), Vector(Status(err)))
      }

    case FromEditor(EditorOutput.DocViewed(d)) =>
      // Like DocChanged (mirrors follow what the pane shows) but marked
      // as navigation so the owner never feeds it to a session.
      val t = docText(d)
      val bump = if (t == m.text) 0 else 1
      Update(
        m.copy(doc = d, text = t, textEpoch = m.textEpoch + bump),
        Vector(DocViewed(d)))
    case FromEditor(EditorOutput.UndoRequested) =>
      Update(m, Vector(UndoRequested))
    case FromEditor(EditorOutput.RedoRequested) =>
      Update(m, Vector(RedoRequested))
    case FromEditor(EditorOutput.BranchRequested(d)) =>
      val t = docText(d)
      Update(
        m.copy(doc = d, text = t, textEpoch = m.textEpoch + 1),
        Vector(BranchRequested(d)))
    case FromEditor(EditorOutput.DocChanged(d)) =>
      // The editor echoes the doc we just pushed via SyncDocument; that
      // echo must not re-derive the text (it would remount the
      // editor mid-typing). A doc equal to ours is such an echo.
      if (d == m.doc) Update(m)
      else {
        val t = docText(d)
        val bump = if (t == m.text) 0 else 1
        Update(
          m.copy(doc = d, text = t, textEpoch = m.textEpoch + bump),
          Vector(DocChanged(d)))
      }
    case FromEditor(EditorOutput.FollowRef(ref)) =>
      Update(m, Vector(FollowRef(ref)))
    case FromEditor(EditorOutput.Status(s)) =>
      Update(m.copy(status = s), Vector(Status(s)))
  }

private def load(
    m: TypedDocModel,
    d: Document[NodeData],
    select: Option[Int],
    msg: String,
    mergeLabel: Option[String] = None): Update[TypedDocModel, TypedDocOutput] =
  Update(
    m.copy(
      doc = d,
      pushedDoc = d,
      pushedSelect = select.filter(id => d.getNode(id).isDefined),
      pushedMergeLabel = mergeLabel,
      pushedHistory = None,
      text = docText(d),
      textEpoch = m.textEpoch + 1,
      status = msg),
    Vector(DocChanged(d), Status(msg)))
