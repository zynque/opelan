package opelan.ui.fp

import scala.scalajs.js
import org.scalajs.dom

// Turns a View into live DOM. Event listeners are attached once per event
// name; they resolve the current handler from a table stored on the element,
// so patching only swaps table entries and never re-binds listeners.
object Render {

  // Per-render accumulator: where children mount, and post-render hooks for
  // declarative focus/scroll (applied after the node is attached).
  final class Ctx {
    var mountPoint: Option[dom.Element] = None
    var focusElement: Option[(dom.Element, Boolean)] = None // (el, select text)
    var scrollElement: Option[dom.Element] = None
  }

  def render[I](view: View[I], emit: I => Unit, ctx: Ctx): dom.Node = view match {
    case View.Text(value) =>
      dom.document.createTextNode(value)

    case View.Mount =>
      val el = dom.document.createElement("div").asInstanceOf[dom.Element]
      el.setAttribute("style", "display:contents")
      ctx.mountPoint = Some(el)
      el

    case View.Elem(tag, attrs, events, children) =>
      val el = dom.document.createElement(tag)
      attrs.foreach((k, v) => el.setAttribute(k, v))
      wireEvents(el, events, emit)
      children.foreach(c => el.appendChild(render(c, emit, ctx)))
      if (attrs.get("data-focus").contains("true"))
        ctx.focusElement = Some((el, true))
      if (attrs.get("data-scroll").contains("true"))
        ctx.scrollElement = Some(el)
      el
  }

  private[fp] def scrollIntoView(el: dom.Element): Unit =
    el.asInstanceOf[js.Dynamic]
      .scrollIntoView(js.Dynamic.literal("block" -> "nearest"))

  // The per-element handler table. Kept on the element itself so it survives
  // patching; listeners look up the current handler at dispatch time.
  private[fp] def handlerStore(
      el: dom.Element): scala.collection.mutable.Map[String, dom.Event => Any] = {
    val dyn = el.asInstanceOf[js.Dynamic]
    if (js.isUndefined(dyn.selectDynamic("__fpHandlers")))
      dyn.updateDynamic("__fpHandlers")(
        scala.collection.mutable.Map.empty[String, dom.Event => Any].asInstanceOf[js.Any])
    dyn.selectDynamic("__fpHandlers")
      .asInstanceOf[scala.collection.mutable.Map[String, dom.Event => Any]]
  }

  // Sets the current handlers for an element, attaching a dispatcher for any
  // event name not seen before. Handlers absent from `events` are dropped
  // from the table (the listener stays but resolves to nothing).
  private[fp] def wireEvents[I](
      el: dom.Element,
      events: Map[String, dom.Event => Option[I]],
      emit: I => Unit): Unit = {
    val store = handlerStore(el)
    store.keys.toList.foreach(name => if (!events.contains(name)) store.remove(name))
    events.foreach { (name, f) =>
      if (!store.contains(name))
        el.addEventListener(name, (e: dom.Event) =>
          store.get(name).foreach(g =>
            g(e).asInstanceOf[Option[I]].foreach(emit)))
      store(name) = f.asInstanceOf[dom.Event => Any]
    }
  }
}
