package opelan.ui.view

import opelan.foundation.document._
import opelan.foundation.view.ViewTree
import opelan.ui.fp._
import opelan.ui.fp.Dsl._
import org.scalajs.dom

// The single interpreter from view trees — the detached document trees
// Language.render produces — to fp Views. Every derived view of every
// language renders through here, so languages emit data, never DOM.
//
// Recovering (Never Broken): an unrecognized tag or a datum with no
// presentational meaning renders as a hole chip rather than failing, and
// children always render — a malformed view tree degrades gracefully.
//
// Provenance: an element whose first child is a SourceRef leaf is tagged
// — it renders with a click handler emitting `onSource(id)`, so the host
// can navigate to the producing node. ("input" elements are the one
// exception: their SourceRef names the node they edit, not provenance.)
def viewTree[I](
    t: DetachedNode[NodeData],
    onSource: Int => I,
    onEdit: (Int, String) => I): View[I] = {
  val (src, kids) = t.children match {
    case DetachedNode(NodeData.SourceRef(id), Nil) :: rest => (Some(id), rest)
    case _ => (None, t.children)
  }
  if (t.data == NodeData.StringData("input"))
    src.fold(holeView("?input"))(id => inputView(id, kids, onEdit))
  else {
    val v = content(t.data, kids, onSource, onEdit)
    src.fold(v)(id => annotated(v, id, onSource))
  }
}

private def content[I](
    data: NodeData,
    kids: List[DetachedNode[NodeData]],
    onSource: Int => I,
    onEdit: (Int, String) => I): View[I] =
  data match {
    case NodeData.StringData(tag) if ViewTree.tags(tag) =>
      element(tag, kids.map(k => viewTree(k, onSource, onEdit)))
    case NodeData.StringData(tag) if kids.nonEmpty =>
      // An unrecognized element: surface the tag as a hole, children
      // still render.
      element("block",
        holeView(s"?$tag") +: kids.map(k => viewTree(k, onSource, onEdit)))
    case NodeData.StringData(s) => text(s)
    case NodeData.GapData(t) =>
      val chip = holeView(if (t.isEmpty) "?" else t)
      if (kids.isEmpty) chip
      else els("span")(chip +: kids.map(k => viewTree(k, onSource, onEdit)))
    case other =>
      // Scalars render as text; refs and stray SourceRefs become holes.
      val lead = other match {
        case NodeData.IntData(i)   => text(i.toString)
        case NodeData.FloatData(f) => text(f.toString)
        case d                     => holeView(NodeDataText.show(d))
      }
      if (kids.isEmpty) lead
      else element("block",
        lead +: kids.map(k => viewTree(k, onSource, onEdit)))
  }

private def element[I](tag: String, kids: Iterable[View[I]]): View[I] =
  tag match {
    case "block" =>
      els("div", style("display: flex; flex-direction: column;"))(kids)
    case "flow" => els("span")(kids)
    case "heading" =>
      els("div", style("font-weight: bold; margin-bottom: 4px;"))(kids)
    case "grid" =>
      el("table", style("border-collapse: collapse;"))(
        els("tbody")(kids.map(asRow)))
    case "row" => els("tr")(kids.map(asCell))
    case "cell" => els("td", style(cellCss))(kids)
    case "hcell" =>
      els("th",
        style(cellCss + " text-align: left; background: #f5f5f5;"))(kids)
  }

private val cellCss = "border: 1px solid #ddd; padding: 2px 8px;"

// A projected text box over a document node: the box shows the node's
// surface text; Enter or blur commits `onEdit(id, text)` and Escape
// reverts — the same contract as the structural editor's inline edit.
private def inputView[I](
    id: Int,
    display: List[DetachedNode[NodeData]],
    onEdit: (Int, String) => I): View[I] = {
  val current = display.map(ViewTree.flatten).mkString
  el("input",
    Map(
      "type"  -> "text",
      "value" -> current,
      "style" -> (inputCss(current) + "font: inherit; padding: 0; margin: 0; " +
        s"min-width: 4ch; width: ${math.max(4, current.length + 1)}ch;")),
    events(
      onEventOpt("keydown")(keyDown(id, current, onEdit)),
      onEventOpt("blur")(blurCommit(id, onEdit))))()
}

// A box over a hole (its surface text starts with "?") wears the hole
// chip's colors, so a stuck input reads as one at a glance.
private def inputCss(current: String): String =
  if (current.startsWith("?"))
    "border: 1px solid #e8d48a; border-radius: 3px; background: #fdf3d7; color: #8a6d1a; "
  else "border: none; background: transparent; "

private def keyDown[I](id: Int, current: String, onEdit: (Int, String) => I)(
    e: dom.Event): Option[I] = {
  val key = e.asInstanceOf[dom.KeyboardEvent].key
  // Function keys are never text input — let them bubble so a container
  // shortcut (e.g. F12 go-to-definition) works while a box is focused.
  if (!key.startsWith("F")) e.stopPropagation()
  key match {
    case "Enter" =>
      e.preventDefault()
      blur(e)
      Some(onEdit(id, valueOf(e)))
    case "Escape" =>
      e.preventDefault()
      e.target.asInstanceOf[dom.HTMLInputElement].value = current
      blur(e)
      None
    case _ => None
  }
}

private def blur(e: dom.Event): Unit =
  e.target.asInstanceOf[dom.HTMLInputElement].blur()

private def valueOf(e: dom.Event): String =
  e.target.asInstanceOf[dom.HTMLInputElement].value

// A blur delivered while the input is leaving the document is DOM
// teardown (a patch rebuilt the view), not the user moving focus away —
// committing it would write a stale value, so only live blurs count.
private def blurCommit[I](id: Int, onEdit: (Int, String) => I)(
    e: dom.Event): Option[I] =
  if (e.target.asInstanceOf[dom.Element].isConnected)
    Some(onEdit(id, valueOf(e)))
  else None

// Recover invalid table structure: grid children that aren't rows get
// wrapped in one, row children that aren't cells likewise — a <div>
// inside <tbody> would invite browser DOM normalization to fight the
// positional patcher.
private def asRow[I](v: View[I]): View[I] = v match {
  case e @ View.Elem("tr", _, _, _) => e
  case other => el("tr")(el("td", style(cellCss))(other))
}

private def asCell[I](v: View[I]): View[I] = v match {
  case e @ View.Elem(tag, _, _, _) if tag == "td" || tag == "th" => e
  case other => el("td", style(cellCss))(other)
}

private def holeView[I](t: String): View[I] =
  el("span",
    style("background: #fdf3d7; color: #8a6d1a; border: 1px solid #e8d48a; " +
      "border-radius: 3px; padding: 0 4px;"))(
    text(t))

// Tag a rendered fragment with provenance: click emits onSource(id) —
// and if the fragment contains an editor, the click lands in it, so one
// click goes straight to editing rather than needing a second click on
// the box itself.
private def annotated[I](v: View[I], id: Int, onSource: Int => I): View[I] = {
  val hasInput = containsInput(v)
  val click: dom.Event => Option[I] = e => {
    if (hasInput) focusContainedInput(e)
    Some(onSource(id))
  }
  v match {
    case View.Elem(tag, attrs, evs, kids) =>
      View.Elem(tag,
        attrs
          .updated("title", s"node #$id")
          .updated("style", attrs.getOrElse("style", "") + " cursor: pointer;"),
        evs + ("click" -> click),
        kids)
    case other =>
      el("span",
        Map("title" -> s"node #$id", "style" -> "cursor: pointer;"),
        Map("click" -> click))(
        other)
  }
}

private def containsInput[I](v: View[I]): Boolean = v match {
  case View.Elem("input", _, _, _)  => true
  case View.Elem(_, _, _, kids)     => kids.exists(containsInput)
  case _                            => false
}

// Focus the fragment's editor. The provenance navigation this same
// click emits is a silent select — the outline highlights the node but
// doesn't claim focus — so focusing here is safe: nothing takes it
// back. Clicks off the input (the cell's padding) focus + select-all so
// typing replaces, sheet-style; a click on the input itself just
// ensures focus (a mousedown blur elsewhere may have stolen it) and
// keeps the browser's cursor placement.
private def focusContainedInput(e: dom.Event): Unit = {
  val clicked = e.target.asInstanceOf[dom.Element]
  val box =
    if (clicked.isInstanceOf[dom.HTMLInputElement]) clicked
    else e.currentTarget.asInstanceOf[dom.Element].querySelector("input")
  if (box != null) {
    val inp = box.asInstanceOf[dom.HTMLInputElement]
    inp.focus()
    if (box != clicked) inp.select()
  }
}
